import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Banco concurrente: transferencias entre cuentas con productores/consumidores.
 *
 * Mecanismos de concurrencia:
 *  - ReentrantLock por cuenta, adquiridos SIEMPRE en orden creciente de id (previene deadlock).
 *  - ArrayBlockingQueue acotada entre productores y trabajadores (poison pill para terminar).
 *  - Auditor que toma una foto consistente adquiriendo todos los locks en el mismo orden.
 *  - AtomicLong para contadores globales.
 *
 * Compilar: javac BancoConcurrente.java
 * Ejecutar: java BancoConcurrente [--cuentas 50] [--saldo 10000] [--productores 2] [--workers 4]
 *                                 [--transferencias 100000] [--cola 256] [--auditMs 500]
 *                                 [--seed 42] [--quiet] [--unsafe]
 */
public class BancoConcurrente {

    // ---------- Modelo ----------
    record Transferencia(int origen, int destino, long monto) {}
    static final Transferencia POISON = new Transferencia(-1, -1, 0);

    static final class Cuenta {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
        long saldo; // centavos; protegido por 'lock' (salvo en modo --unsafe)
        Cuenta(int id, long saldo) { this.id = id; this.saldo = saldo; }
    }

    static final class Banco {
        final Cuenta[] cuentas;
        final boolean unsafe;
        final AtomicLong aceptadas = new AtomicLong();
        final AtomicLong rechazadas = new AtomicLong();
        final long totalInicial;

        Banco(int n, long saldoInicial, boolean unsafe) {
            this.cuentas = new Cuenta[n];
            for (int i = 0; i < n; i++) cuentas[i] = new Cuenta(i, saldoInicial);
            this.totalInicial = n * saldoInicial;
            this.unsafe = unsafe;
        }

        boolean transferir(Transferencia t) {
            if (t.origen() == t.destino()) { rechazadas.incrementAndGet(); return false; }
            Cuenta a = cuentas[t.origen()], b = cuentas[t.destino()];
            if (unsafe) return mover(a, b, t.monto());
            // Orden global de adquisición => sin ciclos de espera => sin deadlock
            Cuenta primero = a.id < b.id ? a : b;
            Cuenta segundo = a.id < b.id ? b : a;
            primero.lock.lock();
            try {
                segundo.lock.lock();
                try {
                    return mover(a, b, t.monto());
                } finally {
                    segundo.lock.unlock();
                }
            } finally {
                primero.lock.unlock();
            }
        }

        private boolean mover(Cuenta a, Cuenta b, long monto) {
            if (a.saldo < monto) { rechazadas.incrementAndGet(); return false; }
            a.saldo -= monto;
            if (unsafe) Thread.yield(); // agranda la ventana de la carrera en el modo de control
            b.saldo += monto;
            aceptadas.incrementAndGet();
            return true;
        }

        /** Suma consistente: bloquea todas las cuentas en orden creciente. */
        long sumaConsistente() {
            if (unsafe) { long s = 0; for (Cuenta c : cuentas) s += c.saldo; return s; }
            for (Cuenta c : cuentas) c.lock.lock();
            try {
                long s = 0;
                for (Cuenta c : cuentas) s += c.saldo;
                return s;
            } finally {
                for (int i = cuentas.length - 1; i >= 0; i--) cuentas[i].lock.unlock();
            }
        }

        boolean haySaldoNegativo() {
            for (Cuenta c : cuentas) if (c.saldo < 0) return true;
            return false;
        }
    }

    // ---------- Configuración ----------
    static final class Config {
        int cuentas = 50, productores = 2, workers = 4, cola = 256, auditMs = 500;
        long saldo = 10_000_00L; // centavos
        long transferencias = 100_000, seed = 42;
        boolean quiet = false, unsafe = false;

        static Config parse(String[] args) {
            Config c = new Config();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--cuentas" -> c.cuentas = Integer.parseInt(args[++i]);
                    case "--saldo" -> c.saldo = Long.parseLong(args[++i]) * 100;
                    case "--productores" -> c.productores = Integer.parseInt(args[++i]);
                    case "--workers" -> c.workers = Integer.parseInt(args[++i]);
                    case "--transferencias" -> c.transferencias = Long.parseLong(args[++i]);
                    case "--cola" -> c.cola = Integer.parseInt(args[++i]);
                    case "--auditMs" -> c.auditMs = Integer.parseInt(args[++i]);
                    case "--seed" -> c.seed = Long.parseLong(args[++i]);
                    case "--quiet" -> c.quiet = true;
                    case "--unsafe" -> c.unsafe = true;
                    default -> throw new IllegalArgumentException("Parámetro desconocido: " + args[i]);
                }
            }
            if (c.cuentas < 2 || c.productores < 1 || c.workers < 1)
                throw new IllegalArgumentException("Se requieren >=2 cuentas, >=1 productor y >=1 worker");
            return c;
        }
    }

    static String fmt(long centavos) { return String.format("%,.2f", centavos / 100.0); }

    // ---------- Main ----------
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        Config cfg = Config.parse(args);
        Banco banco = new Banco(cfg.cuentas, cfg.saldo, cfg.unsafe);
        BlockingQueue<Transferencia> cola = new ArrayBlockingQueue<>(cfg.cola);
        AtomicLong procesadas = new AtomicLong();
        AtomicLong auditoriasOk = new AtomicLong(), auditoriasFalla = new AtomicLong();
        AtomicReference<String> ultimaAuditoria = new AtomicReference<>("pendiente");
        long[] porWorker = new long[cfg.workers]; // cada worker escribe SOLO su índice; se lee tras join()
        AtomicBoolean fin = new AtomicBoolean(false);

        System.out.printf("=== Banco concurrente === cuentas=%d productores=%d workers=%d transferencias=%,d%s%n",
                cfg.cuentas, cfg.productores, cfg.workers, cfg.transferencias, cfg.unsafe ? "  [MODO UNSAFE]" : "");
        System.out.printf("Saldo total inicial: %s%n", fmt(banco.totalInicial));

        long t0 = System.nanoTime();

        // Productores: reparten el total de transferencias entre sí
        List<Thread> productores = new ArrayList<>();
        for (int p = 0; p < cfg.productores; p++) {
            final long cuota = cfg.transferencias / cfg.productores + (p < cfg.transferencias % cfg.productores ? 1 : 0);
            final long semilla = cfg.seed + p;
            Thread th = new Thread(() -> {
                Random r = new Random(semilla);
                try {
                    for (long i = 0; i < cuota; i++) {
                        int o = r.nextInt(cfg.cuentas), d = r.nextInt(cfg.cuentas);
                        long m = 1 + r.nextInt(50_000); // hasta 500,00
                        cola.put(new Transferencia(o, d, m)); // bloquea si la cola está llena
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "productor-" + p);
            productores.add(th);
        }

        // Trabajadores
        List<Thread> workers = new ArrayList<>();
        for (int w = 0; w < cfg.workers; w++) {
            final int idx = w;
            Thread th = new Thread(() -> {
                try {
                    while (true) {
                        Transferencia t = cola.take(); // bloquea si está vacía (sin busy-wait)
                        if (t == POISON) break;
                        banco.transferir(t);
                        procesadas.incrementAndGet();
                        porWorker[idx]++;
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "worker-" + w);
            workers.add(th);
        }

        // Auditor: foto consistente periódica
        Thread auditor = new Thread(() -> {
            try {
                while (!fin.get()) {
                    Thread.sleep(cfg.auditMs);
                    long s = banco.sumaConsistente();
                    boolean ok = s == banco.totalInicial && !banco.haySaldoNegativo();
                    (ok ? auditoriasOk : auditoriasFalla).incrementAndGet();
                    ultimaAuditoria.set(ok ? "OK (" + fmt(s) + ")" : "FALLA (" + fmt(s) + ")");
                }
            } catch (InterruptedException e) { /* cierre */ }
        }, "auditor");
        auditor.setDaemon(true);

        // Monitor: panel por consola cada segundo
        Thread monitor = new Thread(() -> {
            long previo = 0;
            try {
                while (!fin.get()) {
                    Thread.sleep(1000);
                    long ahora = procesadas.get();
                    if (!cfg.quiet) {
                        System.out.printf("[%5.1fs] procesadas=%,d  aceptadas=%,d  rechazadas=%,d  %,d tps  cola=%d/%d  auditoría=%s%n",
                                (System.nanoTime() - t0) / 1e9, ahora, banco.aceptadas.get(), banco.rechazadas.get(),
                                ahora - previo, cola.size(), cfg.cola, ultimaAuditoria.get());
                    }
                    previo = ahora;
                }
            } catch (InterruptedException e) { /* cierre */ }
        }, "monitor");
        monitor.setDaemon(true);

        auditor.start(); monitor.start();
        workers.forEach(Thread::start);
        productores.forEach(Thread::start);

        // Cierre ordenado: productores -> poison pills -> workers
        for (Thread p : productores) p.join();
        for (int i = 0; i < cfg.workers; i++) cola.put(POISON);
        for (Thread w : workers) w.join();
        fin.set(true);
        auditor.interrupt(); monitor.interrupt();
        auditor.join(); monitor.join();

        double seg = (System.nanoTime() - t0) / 1e9;
        long sumaFinal = banco.sumaConsistente();
        boolean ok = sumaFinal == banco.totalInicial && !banco.haySaldoNegativo();

        System.out.println("\n=== Informe final ===");
        System.out.printf("Tiempo total: %.2f s | Throughput: %,.0f transferencias/s%n", seg, procesadas.get() / seg);
        System.out.printf("Procesadas: %,d (aceptadas %,d, rechazadas %,d)%n",
                procesadas.get(), banco.aceptadas.get(), banco.rechazadas.get());
        for (int i = 0; i < cfg.workers; i++) System.out.printf("  worker-%d: %,d%n", i, porWorker[i]);
        System.out.printf("Auditorías periódicas: %d OK, %d con falla%n", auditoriasOk.get(), auditoriasFalla.get());
        System.out.printf("Saldo inicial: %s | Saldo final: %s | Diferencia: %s%n",
                fmt(banco.totalInicial), fmt(sumaFinal), fmt(sumaFinal - banco.totalInicial));
        System.out.println("Veredicto: " + (ok ? "OK - invariante conservado" : "FALLA - se perdió/creó dinero"));
        System.exit(ok ? 0 : 1);
    }
}
