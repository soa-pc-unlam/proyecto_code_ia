import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Banco concurrente v2: transferencias entre cuentas con productores/consumidores.
 *
 * Mecanismos de concurrencia:
 *  - ReentrantLock por cuenta, adquiridos SIEMPRE en orden creciente de id (previene deadlock).
 *  - BlockingQueue acotada entre productores y trabajadores (poison pill para terminar).
 *  - Auditor que toma una foto consistente adquiriendo todos los locks en el mismo orden,
 *    y mide cuánto tiempo detiene a los trabajadores (pausa).
 *  - Watchdog (ThreadMXBean) que detecta deadlocks reales en tiempo de ejecución.
 *  - AtomicLong para contadores globales; contadores por worker con relleno (padding)
 *    para evitar falso compartido (false sharing).
 *
 * Compilar: javac BancoConcurrente.java
 * Ejecutar: java BancoConcurrente [--cuentas 50] [--saldo 10000] [--productores 2] [--workers 4]
 *                                 [--transferencias 100000] [--cola 256] [--auditMs 500]
 *                                 [--seed 42] [--queue array|linked] [--fair] [--quiet] [--unsafe]
 * Código de salida: 0 = OK, 1 = invariante violado o deadlock, 2 = parámetros inválidos.
 */
public class BancoConcurrente {

    // ---------- Modelo ----------
    record Transferencia(int origen, int destino, long monto) {}
    static final Transferencia POISON = new Transferencia(-1, -1, 0);
    static final int PAD = 16; // 16 longs = 128 bytes: separa los contadores en líneas de caché distintas

    static final class Cuenta {
        final int id;
        final ReentrantLock lock;
        long saldo; // centavos; protegido por 'lock' (salvo en modo --unsafe)
        Cuenta(int id, long saldo, boolean fair) { this.id = id; this.saldo = saldo; this.lock = new ReentrantLock(fair); }
    }

    static final class Banco {
        final Cuenta[] cuentas;
        final boolean unsafe;
        final AtomicLong aceptadas = new AtomicLong();
        final AtomicLong rechazadas = new AtomicLong();
        final long totalInicial;

        Banco(int n, long saldoInicial, boolean unsafe, boolean fair) {
            this.cuentas = new Cuenta[n];
            for (int i = 0; i < n; i++) cuentas[i] = new Cuenta(i, saldoInicial, fair);
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

    // ---------- Configuración (con validación) ----------
    static final class Config {
        int cuentas = 50, productores = 2, workers = 4, cola = 256, auditMs = 500;
        long saldo = 10_000_00L; // centavos
        long transferencias = 100_000, seed = 42;
        boolean quiet = false, unsafe = false, fair = false;
        String queue = "array";

        static Config parse(String[] args) {
            Config c = new Config();
            try {
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
                        case "--queue" -> c.queue = args[++i];
                        case "--fair" -> c.fair = true;
                        case "--quiet" -> c.quiet = true;
                        case "--unsafe" -> c.unsafe = true;
                        default -> throw new IllegalArgumentException("Parámetro desconocido: " + args[i]);
                    }
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor numérico inválido: " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Falta el valor del último parámetro");
            }
            c.validar();
            return c;
        }

        void validar() {
            if (cuentas < 2 || cuentas > 1_000_000) throw new IllegalArgumentException("--cuentas debe estar entre 2 y 1.000.000");
            if (productores < 1 || productores > 256) throw new IllegalArgumentException("--productores debe estar entre 1 y 256");
            if (workers < 1 || workers > 256) throw new IllegalArgumentException("--workers debe estar entre 1 y 256");
            if (cola < 1) throw new IllegalArgumentException("--cola debe ser >= 1");
            if (auditMs < 1) throw new IllegalArgumentException("--auditMs debe ser >= 1");
            if (saldo < 0) throw new IllegalArgumentException("--saldo no puede ser negativo");
            if (transferencias < 0) throw new IllegalArgumentException("--transferencias no puede ser negativo");
            if (!queue.equals("array") && !queue.equals("linked")) throw new IllegalArgumentException("--queue debe ser array o linked");
        }
    }

    /** Resultado de una corrida (permite probar el programa sin System.exit). */
    record Resultado(boolean ok, boolean deadlock, long procesadas, long aceptadas, long rechazadas,
                     long totalInicial, long totalFinal, double segundos, long[] porWorker,
                     long auditoriasOk, long auditoriasFalla, double pausaMaxMs, double pausaMediaMs) {}

    static String fmt(long centavos) { return String.format("%,.2f", centavos / 100.0); }

    // ---------- Ejecución ----------
    static Resultado run(Config cfg, PrintStream out) throws InterruptedException {
        Banco banco = new Banco(cfg.cuentas, cfg.saldo, cfg.unsafe, cfg.fair);
        BlockingQueue<Transferencia> cola = cfg.queue.equals("linked")
                ? new LinkedBlockingQueue<>(cfg.cola) : new ArrayBlockingQueue<>(cfg.cola);
        AtomicLong procesadas = new AtomicLong();
        AtomicLong auditoriasOk = new AtomicLong(), auditoriasFalla = new AtomicLong();
        AtomicLong pausaTotalNs = new AtomicLong(), pausaMaxNs = new AtomicLong();
        AtomicReference<String> ultimaAuditoria = new AtomicReference<>("pendiente");
        AtomicBoolean deadlock = new AtomicBoolean(false);
        // cada worker escribe SOLO su posición (idx*PAD); se lee tras join() => happens-before
        long[] porWorker = new long[cfg.workers * PAD];
        AtomicBoolean fin = new AtomicBoolean(false);

        out.printf("=== Banco concurrente v2 === cuentas=%d productores=%d workers=%d transferencias=%,d cola=%s(%d)%s%s%n",
                cfg.cuentas, cfg.productores, cfg.workers, cfg.transferencias, cfg.queue, cfg.cola,
                cfg.fair ? " fair" : "", cfg.unsafe ? "  [MODO UNSAFE]" : "");
        out.printf("Saldo total inicial: %s%n", fmt(banco.totalInicial));

        long t0 = System.nanoTime();

        List<Thread> productores = new ArrayList<>();
        for (int p = 0; p < cfg.productores; p++) {
            final long cuota = cfg.transferencias / cfg.productores + (p < cfg.transferencias % cfg.productores ? 1 : 0);
            final long semilla = cfg.seed + p;
            productores.add(new Thread(() -> {
                Random r = new Random(semilla);
                try {
                    for (long i = 0; i < cuota; i++) {
                        int o = r.nextInt(cfg.cuentas), d = r.nextInt(cfg.cuentas);
                        long m = 1 + r.nextInt(50_000); // hasta 500,00
                        cola.put(new Transferencia(o, d, m)); // bloquea si la cola está llena
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "productor-" + p));
        }

        List<Thread> workers = new ArrayList<>();
        for (int w = 0; w < cfg.workers; w++) {
            final int idx = w * PAD;
            workers.add(new Thread(() -> {
                try {
                    while (true) {
                        Transferencia t = cola.take(); // bloquea si está vacía (sin busy-wait)
                        if (t == POISON) break;
                        banco.transferir(t);
                        procesadas.incrementAndGet();
                        porWorker[idx]++;
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "worker-" + w));
        }

        Thread auditor = new Thread(() -> {
            try {
                while (!fin.get()) {
                    Thread.sleep(cfg.auditMs);
                    long a = System.nanoTime();
                    long s = banco.sumaConsistente();
                    long pausa = System.nanoTime() - a; // espera por locks + suma: lo que "ve" el sistema
                    pausaTotalNs.addAndGet(pausa);
                    pausaMaxNs.accumulateAndGet(pausa, Math::max);
                    boolean ok = s == banco.totalInicial && !banco.haySaldoNegativo();
                    (ok ? auditoriasOk : auditoriasFalla).incrementAndGet();
                    ultimaAuditoria.set(ok ? "OK (" + fmt(s) + ")" : "FALLA (" + fmt(s) + ")");
                }
            } catch (InterruptedException e) { /* cierre */ }
        }, "auditor");
        auditor.setDaemon(true);

        // Monitor: panel por consola + watchdog de deadlocks (ThreadMXBean)
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        Thread monitor = new Thread(() -> {
            long previo = 0;
            try {
                while (!fin.get()) {
                    Thread.sleep(1000);
                    long ahora = procesadas.get();
                    long[] bloqueados = mx.findDeadlockedThreads();
                    if (bloqueados != null) {
                        deadlock.set(true);
                        out.println("!!! DEADLOCK detectado entre " + bloqueados.length + " hilos");
                    }
                    if (!cfg.quiet) {
                        out.printf("[%5.1fs] procesadas=%,d  aceptadas=%,d  rechazadas=%,d  %,d tps  cola=%d/%d  auditoría=%s%n",
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
        boolean ok = sumaFinal == banco.totalInicial && !banco.haySaldoNegativo() && !deadlock.get();
        long[] pw = new long[cfg.workers];
        for (int i = 0; i < cfg.workers; i++) pw[i] = porWorker[i * PAD];
        long nAud = auditoriasOk.get() + auditoriasFalla.get();
        return new Resultado(ok, deadlock.get(), procesadas.get(), banco.aceptadas.get(), banco.rechazadas.get(),
                banco.totalInicial, sumaFinal, seg, pw, auditoriasOk.get(), auditoriasFalla.get(),
                pausaMaxNs.get() / 1e6, nAud == 0 ? 0 : pausaTotalNs.get() / 1e6 / nAud);
    }

    static void informe(Config cfg, Resultado r, PrintStream out) {
        out.println("\n=== Informe final ===");
        out.printf("Tiempo total: %.2f s | Throughput: %,.0f transferencias/s%n", r.segundos(), r.procesadas() / r.segundos());
        out.printf("Procesadas: %,d (aceptadas %,d, rechazadas %,d)%n", r.procesadas(), r.aceptadas(), r.rechazadas());
        for (int i = 0; i < r.porWorker().length; i++) out.printf("  worker-%d: %,d%n", i, r.porWorker()[i]);
        out.printf("Auditorías periódicas: %d OK, %d con falla | pausa media %.3f ms, máxima %.3f ms%n",
                r.auditoriasOk(), r.auditoriasFalla(), r.pausaMediaMs(), r.pausaMaxMs());
        out.printf("Saldo inicial: %s | Saldo final: %s | Diferencia: %s%n",
                fmt(r.totalInicial()), fmt(r.totalFinal()), fmt(r.totalFinal() - r.totalInicial()));
        out.println("Deadlock detectado: " + (r.deadlock() ? "SÍ" : "no"));
        out.println("Veredicto: " + (r.ok() ? "OK - invariante conservado" : "FALLA - se perdió/creó dinero o hubo deadlock"));
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        Config cfg;
        try {
            cfg = Config.parse(args);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
            System.exit(2);
            return;
        }
        Resultado r = run(cfg, out);
        informe(cfg, r, out);
        System.exit(r.ok() ? 0 : 1);
    }
}
