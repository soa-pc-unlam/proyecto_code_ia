package ar.edu.unlam.reparabarrio.arranque;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Exclusión entre procesos anterior a JDBC. El archivo permanente nunca se borra. */
public final class GuardiaInstancia implements AutoCloseable {
    private final Path directorio;
    private final FileChannel canal;
    private final FileLock lock;
    private boolean emitida;
    private GuardiaInstancia(Path directorio, FileChannel canal, FileLock lock) {
        this.directorio = directorio; this.canal = canal; this.lock = lock;
    }
    public static GuardiaInstancia adquirir(Path directorio) throws IOException {
        Path real = directorio.toRealPath();
        Path archivo = real.resolve("instancia.lock");
        if (Files.isSymbolicLink(archivo)) throw new IOException("Guardia enlazada no admitida");
        var canal = FileChannel.open(archivo, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock = canal.tryLock(); }
            catch (OverlappingFileLockException e) { throw new IOException("INSTANCIA_OCUPADA", e); }
            if (lock == null) throw new IOException("INSTANCIA_OCUPADA");
            return new GuardiaInstancia(real, canal, lock);
        } catch (IOException | RuntimeException e) { canal.close(); throw e; }
    }
    public Path directorio() { return directorio; }
    public boolean vigente() { return canal.isOpen() && lock.isValid(); }
    /** Señal técnica conservadora; no guarda inventario ni identidad de negocio. */
    public synchronized boolean baseConocida() throws IOException { return canal.size() > 0; }
    public synchronized void recordarBaseConocida() throws IOException {
        if (!vigente()) throw new IOException("Guardia no vigente");
        if (!baseConocida()) {
            var bytes = java.nio.ByteBuffer.wrap("H4_BASE_CONOCIDA\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            canal.position(0); while (bytes.hasRemaining()) canal.write(bytes); canal.force(true);
        }
    }
    public synchronized void emitirAlmacen() {
        if (!vigente() || emitida) throw new IllegalStateException("Guardia sin capacidad disponible");
        emitida = true;
    }
    @Override public synchronized void close() throws IOException {
        if (!canal.isOpen()) return;
        try { if (lock.isValid()) lock.release(); } finally { canal.close(); }
    }
}
