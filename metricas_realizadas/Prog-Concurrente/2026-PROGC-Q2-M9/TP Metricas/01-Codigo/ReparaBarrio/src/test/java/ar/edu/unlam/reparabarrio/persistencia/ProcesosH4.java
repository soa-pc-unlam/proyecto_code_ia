package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.TestContextos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Drenaje continuo, señales y timeout; sólo termina el proceso hijo que lanzó. */
final class ProcesosH4 implements AutoCloseable {
    final Process proceso;
    final List<String> salida = Collections.synchronizedList(new ArrayList<>());
    private final BlockingQueue<String> lineas = new LinkedBlockingQueue<>();
    private final Thread drenaje;
    ProcesosH4(Path raiz, String... argumentos) throws Exception {
        TestContextos.validar(raiz.resolve("taller"));
        var comando = new ArrayList<String>();
        comando.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        comando.add("-Dfile.encoding=UTF-8"); comando.add("-cp");
        comando.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        if (Boolean.getBoolean("h4.evidencia")) comando.add("-Dh4.evidencia=true");
        comando.add(ProcesoH4.class.getName()); comando.add(raiz.toString()); comando.addAll(List.of(argumentos));
        proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        drenaje = Thread.ofPlatform().daemon(true).start(() -> {
            try (var r = new BufferedReader(new InputStreamReader(proceso.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String linea; while ((linea = r.readLine()) != null) { salida.add(linea); lineas.offer(linea); }
            } catch (IOException e) { salida.add(e.toString()); }
        });
        System.out.println("H4 proceso PID=" + proceso.pid() + " raíz=" + raiz + " args=" + List.of(argumentos));
    }
    String esperar(String prefijo) throws Exception {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < fin) {
            String linea = lineas.poll(100, TimeUnit.MILLISECONDS);
            if (linea != null && linea.startsWith(prefijo)) return linea.substring(prefijo.length());
            if (!proceso.isAlive() && lineas.isEmpty()) fail("PID " + proceso.pid() + " terminó antes de " + prefijo + ": " + salida);
        }
        fail("Timeout PID=" + proceso.pid() + " esperando " + prefijo + " salida=" + salida); return null;
    }
    String valor(String prefijo) {
        synchronized (salida) { return salida.stream().filter(s -> s.startsWith(prefijo)).findFirst().orElseThrow(() -> new AssertionError(salida)).substring(prefijo.length()); }
    }
    void liberar() throws Exception { proceso.getOutputStream().write('\n'); proceso.getOutputStream().flush(); }
    void terminar() throws Exception {
        proceso.destroyForcibly(); assertTrue(proceso.waitFor(10, TimeUnit.SECONDS), "Hijo no terminó"); drenaje.join(10000);
        assertFalse(proceso.isAlive());
    }
    int finalizar() throws Exception {
        assertTrue(proceso.waitFor(45, TimeUnit.SECONDS), "Timeout PID=" + proceso.pid() + " " + salida); drenaje.join(10000);
        return proceso.exitValue();
    }
    @Override public void close() throws Exception {
        if (proceso.isAlive()) terminar(); else drenaje.join(10000);
        // Se conserva la evidencia; ninguna limpieza de bases ni procesos ajenos.
        Path logs = Path.of("target", "h4-logs"); Files.createDirectories(logs);
        Files.write(logs.resolve("proceso-" + proceso.pid() + "-" + UUID.randomUUID() + ".txt"), salida, java.nio.charset.StandardCharsets.UTF_8);
    }
}
