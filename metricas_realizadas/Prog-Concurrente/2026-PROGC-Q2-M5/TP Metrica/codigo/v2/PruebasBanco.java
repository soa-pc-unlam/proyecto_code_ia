import java.io.*;
import java.util.concurrent.*;

/** Pruebas automáticas sin dependencias externas. Ejecutar: java PruebasBanco (código 0 = todo OK). */
public class PruebasBanco {
    static int fallos = 0, total = 0;
    static final PrintStream NADA = new PrintStream(OutputStream.nullOutputStream());

    static void check(String nombre, boolean cond) {
        total++;
        System.out.println((cond ? "  [OK]    " : "  [FALLA] ") + nombre);
        if (!cond) fallos++;
    }
    static BancoConcurrente.Config cfg(String... a) { return BancoConcurrente.Config.parse(a); }

    /** Corre con tope de tiempo: si se cuelga (posible deadlock) devuelve null. */
    static BancoConcurrente.Resultado conTimeout(BancoConcurrente.Config c, int seg) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        try { return ex.submit(() -> BancoConcurrente.run(c, NADA)).get(seg, TimeUnit.SECONDS); }
        catch (TimeoutException e) { return null; }
        finally { ex.shutdownNow(); }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        System.out.println("Pruebas de BancoConcurrente");

        var r = conTimeout(cfg("--transferencias", "200000", "--quiet"), 60);
        check("conserva el dinero (200.000 transferencias)", r != null && r.ok() && r.totalFinal() == r.totalInicial());
        check("aceptadas + rechazadas == procesadas == pedidas",
                r != null && r.aceptadas() + r.rechazadas() == r.procesadas() && r.procesadas() == 200_000);
        check("los workers procesan todo (suma por worker == total)",
                r != null && java.util.Arrays.stream(r.porWorker()).sum() == r.procesadas());

        r = conTimeout(cfg("--cuentas", "2", "--workers", "16", "--productores", "4", "--transferencias", "500000", "--quiet"), 60);
        check("alta contención (2 cuentas, 16 hilos): sin deadlock y sin pérdida", r != null && r.ok() && !r.deadlock());

        r = conTimeout(cfg("--cuentas", "3", "--workers", "8", "--fair", "--transferencias", "200000", "--quiet"), 60);
        check("locks justos (--fair): sin deadlock y sin pérdida", r != null && r.ok());

        r = conTimeout(cfg("--queue", "linked", "--transferencias", "200000", "--quiet"), 60);
        check("cola LinkedBlockingQueue: sin pérdida", r != null && r.ok());

        r = conTimeout(cfg("--transferencias", "0", "--quiet"), 20);
        check("cero transferencias termina limpio", r != null && r.ok() && r.procesadas() == 0);

        r = conTimeout(cfg("--cuentas", "2", "--saldo", "1", "--transferencias", "100000", "--quiet"), 60);
        check("saldos chicos: nunca hay saldo negativo", r != null && r.ok());

        boolean detectada = false;
        for (int i = 0; i < 5 && !detectada; i++) {
            r = conTimeout(cfg("--unsafe", "--workers", "8", "--transferencias", "300000", "--quiet"), 60);
            detectada = r != null && !r.ok();
        }
        check("el modo --unsafe es detectado por el auditor (control negativo)", detectada);

        String[][] malos = { {"--cuentas","1"}, {"--workers","0"}, {"--cola","0"}, {"--saldo","abc"}, {"--queue","x"}, {"--foo"}, {"--workers"} };
        boolean todosRechazados = true;
        for (String[] m : malos) {
            try { cfg(m); todosRechazados = false; System.out.println("    no rechazó: " + String.join(" ", m)); }
            catch (IllegalArgumentException e) { /* esperado */ }
        }
        check("parámetros inválidos rechazados con mensaje claro", todosRechazados);

        System.out.printf("%n%d pruebas, %d fallas%n", total, fallos);
        System.exit(fallos == 0 ? 0 : 1);
    }
}
