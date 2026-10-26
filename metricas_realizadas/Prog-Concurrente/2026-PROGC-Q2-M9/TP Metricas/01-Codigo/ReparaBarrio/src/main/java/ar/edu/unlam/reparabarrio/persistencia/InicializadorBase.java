package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.*;
import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import ar.edu.unlam.reparabarrio.dominio.TipoHerramienta;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** DDL por pasos verificados; no presupone atomicidad DDL/DML de H2.
 * Ningún archivo existente se abre en JDBC antes de verificar la autorización.
 */
public final class InicializadorBase {
    public enum Modo { CREAR_NUEVA, ABRIR_EXISTENTE, MIGRAR_V1, REANUDAR }
    public record Solicitud(Modo modo, AutorizacionBase autorizacion) {
        public Solicitud { Objects.requireNonNull(modo); Objects.requireNonNull(autorizacion); }
    }
    @FunctionalInterface interface Frontera { void paso(String fase) throws Exception; }
    private static final List<String> TABLAS_NEGOCIO = List.of("CATALOGO", "CONTADOR", "ORDENES", "REQUISITOS", "TECNICOS", "PUESTOS", "HERRAMIENTAS", "ASIGNACIONES", "HERRAMIENTAS_ACTIVAS", "HISTORIAL", "HISTORIAL_RECURSOS");
    // Sólo plantilla inmutable de estructura, nunca datos/inventario confirmados.
    private static final Map<Integer, List<String>> PLANTILLAS = new HashMap<>();
    private InicializadorBase() { }
    static String url(Path base) { return "jdbc:h2:file:" + base.toString().replace('\\', '/') + ";WRITE_DELAY=0;LOCK_TIMEOUT=5000"; }
    static List<String> sql(String recurso) throws Exception {
        try (var in = InicializadorBase.class.getResourceAsStream("/persistencia/" + recurso)) {
            if (in == null) throw new IllegalStateException("Falta " + recurso);
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";")).filter(s -> !s.isBlank()).toList();
        }
    }
    private static void ejecutar(Connection c, String sql) throws SQLException { try (var st = c.createStatement()) { st.execute(sql); } }
    private static List<Path> inventario(Path base) throws Exception {
        if (!Files.isDirectory(base.getParent())) return List.of();
        try (var entradas = Files.list(base.getParent())) {
            var archivos = entradas.filter(p -> p.getFileName().toString().startsWith(base.getFileName() + ".")).sorted().toList();
            for (var p : archivos) if (Files.isSymbolicLink(p) || !Files.isRegularFile(p)) throw new IllegalArgumentException("Archivo H2 no regular: " + p);
            return archivos;
        }
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static String huella(Path base) throws Exception {
        var valores = new ArrayList<String>();
        for (var p : inventario(base)) valores.add(p.getFileName() + ":" + Files.size(p) + ":" + hash(Files.readAllBytes(p)));
        return valores.isEmpty() ? "AUSENTE" : hash(String.join("\n", valores).getBytes(StandardCharsets.UTF_8));
    }
    private static void comprobarPermiso(Path base, Solicitud s, boolean nueva) throws Exception {
        var a = s.autorizacion();
        if (!base.equals(a.archivoBase()) || !a.recuperacionFisica() || a.destino() != 2)
            throw new IllegalArgumentException("Autorización incompatible con ruta/alcance");
        boolean origenValido = switch (s.modo()) {
            case CREAR_NUEVA -> a.origen() == 0;
            case ABRIR_EXISTENTE -> a.origen() == 2;
            case MIGRAR_V1 -> a.origen() == 1;
            case REANUDAR -> a.origen() == 0 || a.origen() == 1;
        };
        if (!origenValido) throw new IllegalArgumentException("Origen no autorizado para este modo");
        if (s.modo() != Modo.REANUDAR && !a.huella().equals(huella(base))) throw new IllegalArgumentException("Huella obsoleta");
        if (nueva != (s.modo() == Modo.CREAR_NUEVA)) throw new IllegalArgumentException("Creación explícita/archivo existente incompatible");
        if (nueva && a.origen() != 0) throw new IllegalArgumentException("Origen de creación inválido");
        if (s.modo() == Modo.REANUDAR && !a.reanudacion()) throw new IllegalArgumentException("Reanudación no autorizada");
    }
    private static void preservar(Path base, AutorizacionBase a) throws Exception {
        Path destino = Objects.requireNonNull(a.preservacion(), "Preservación requerida");
        validarPreservacion(base, a);
        if (Files.exists(destino)) throw new IllegalArgumentException("Preservación ya existente; no se sobrescribe");
        Files.createDirectories(destino);
        for (var p : inventario(base)) {
            Path copia = destino.resolve(p.getFileName()); Files.copy(p, copia);
            try (var canal = FileChannel.open(copia, StandardOpenOption.WRITE)) { canal.force(true); }
            if (Files.size(p) != Files.size(copia) || !Arrays.equals(Files.readAllBytes(p), Files.readAllBytes(copia)))
                throw new IllegalStateException("Copia incompleta");
        }
        if (!a.huella().equals(huella(destino.resolve(base.getFileName())))) throw new IllegalStateException("Copia no coincide");
    }
    private static void validarPreservacion(Path base, AutorizacionBase a) throws Exception {
        Path directorio = base.getParent();
        Path raiz = directorio.endsWith(Path.of("datos", "operativo")) ? directorio.getParent().getParent() : directorio;
        Path esperada = raiz.resolve("datos/preservacion/h4").resolve(a.id()).normalize();
        Path destino = Objects.requireNonNull(a.preservacion(), "Preservación requerida");
        if (!destino.isAbsolute() || !destino.normalize().equals(esperada) || !esperada.startsWith(raiz))
            throw new IllegalArgumentException("Destino de preservación no admitido");
        for (Path p = destino; p != null && p.startsWith(raiz); p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Preservación enlazada no admitida");
    }
    static String preparar(GuardiaInstancia guardia, Solicitud s, Frontera frontera) throws Exception {
        if (!guardia.vigente()) throw new IllegalStateException("Guardia no vigente");
        Path base = guardia.directorio().resolve("taller");
        boolean nueva = inventario(base).isEmpty(); comprobarPermiso(base, s, nueva);
        if (nueva && guardia.baseConocida()) throw new IllegalStateException("BASE_CONOCIDA_AUSENTE: no se recrea automáticamente");
        var a = s.autorizacion();
        String funcional = "", id;
        if (s.modo() == Modo.REANUDAR && a.origen() == 1) {
            validarPreservacion(base, a);
            if (!a.huella().equals(huella(a.preservacion().resolve("taller")))) throw new IllegalStateException("Preservación no acreditada");
            funcional = auditarPreservada(guardia, a);
        }
        if (s.modo() == Modo.MIGRAR_V1) {
            if (a.origen() != 1) throw new IllegalArgumentException("Origen de migración inválido");
            preservar(base, a); frontera.paso("PRESERVACION");
            funcional = auditarPreservada(guardia, a);
            if (!a.huella().equals(huella(base))) throw new IllegalStateException("Original cambió antes de migración");
        }
        String jdbc = url(base) + (nueva ? "" : ";IFEXISTS=TRUE");
        guardia.recordarBaseConocida();
        try (var privada = new ConexionPrivada(DriverManager.getConnection(jdbc, "sa", ""))) {
            var c = privada.conexion;
            verificarWriteDelay(c);
            if (s.modo() == Modo.ABRIR_EXISTENTE) {
                if (a.origen() != 2) throw new IllegalArgumentException("Apertura no autoriza migración");
                verificarVersion(c, 2); validarEstructura(c, 2);
                return new RepositorioRecuperacionJdbc(c).leer().metadata().baseId();
            }
            return prepararConexion(c, s.modo(), Parametros.de(a), base, funcional, frontera);
        }
    }
    private record Parametros(String id, String huella, int origen, Path preservacion, AutorizacionBase archivo) {
        static Parametros de(AutorizacionBase a) { return new Parametros(a.id(), a.huella(), a.origen(), a.preservacion(), a); }
    }
    /** Misma implementación de creación v2 para archivo y memoria; memoria no simula permiso de archivo. */
    static String prepararMemoria(Connection c, AutorizacionSimulacion permiso) throws Exception {
        verificarWriteDelay(c);
        return prepararConexion(c, Modo.CREAR_NUEVA,
                new Parametros(permiso.id(), "AUSENTE", 0, null, null), null, "", fase -> { });
    }
    private static String prepararConexion(Connection c, Modo modo, Parametros a, Path base,
                                           String funcional, Frontera frontera) throws Exception {
        String id;
            List<String> protocolo = sql("protocolo.sql");
            List<String> pasos = new ArrayList<>();
            boolean creacion = modo == Modo.CREAR_NUEVA || a.origen() == 0;
            if (creacion) pasos.addAll(sql("esquema.sql"));
            pasos.addAll(sql("migracion-v1-v2.sql"));
            if (!creacion) { pasos.add("DROP TABLE rb_version"); pasos.add("CREATE TABLE rb_version (version INTEGER PRIMARY KEY CHECK(version=2))"); }
            int paso = 0;
            if (modo != Modo.REANUDAR) {
                if (!creacion) { validarEstructura(c, 1); if (!funcional.equals(funcional(c))) throw new IllegalStateException("Contenido v1 cambió"); }
                ejecutar(c, protocolo.getFirst()); frontera.paso("TABLA_MARCADOR");
                id = UUID.randomUUID().toString();
                try (var p = c.prepareStatement("INSERT INTO rb_protocolo VALUES(1,2,?,2,?,?,?,?,?,0,FALSE)")) {
                    p.setInt(1, creacion ? 0 : 1); p.setString(2, id); p.setString(3, a.id()); p.setString(4, a.huella());
                    p.setString(5, a.preservacion() == null ? "" : a.preservacion().toString()); p.setString(6, funcional); p.executeUpdate();
                }
                frontera.paso("MARCADOR");
            } else {
                if (!creacion) validarPreservacion(base, a.archivo());
                // La frontera tabla sin fila se reconoce sólo con estructura exacta y permiso original.
                try (var st = c.createStatement(); var r = st.executeQuery("SELECT * FROM rb_protocolo")) {
                    if (!r.next()) {
                        if (!estructura(c).equals(esperada(creacion, pasos, 0)) || (!creacion && !funcional.equals(funcional(c)))
                                || (creacion && !a.huella().equals("AUSENTE"))) throw new IllegalStateException("Frontera tabla sin fila no acreditada");
                        // Nunca hubo UUID persistido: se emite la primera identidad durable.
                        id = UUID.randomUUID().toString();
                    } else {
                    id = r.getString("base_id"); paso = r.getInt("paso");
                    if (r.getBoolean("completo") && paso != pasos.size()) throw new IllegalStateException("Marcador final contradictorio");
                    String registrada = r.getString("funcional");
                    if ((!creacion && !registrada.equals(funcional)) || r.getInt("protocolo") != 2 || r.getInt("origen") != a.origen() || r.getInt("destino") != 2
                            || !a.id().equals(r.getString("autorizacion")) || !a.huella().equals(r.getString("huella"))
                            || !Objects.equals(a.preservacion() == null ? "" : a.preservacion().toString(), r.getString("preservacion")) || r.next())
                        throw new IllegalStateException("Marcador/permiso incompatible");
                    funcional = registrada;
                    }
                }
                try (var st = c.createStatement(); var r = st.executeQuery("SELECT COUNT(*) FROM rb_protocolo")) {
                    r.next();
                    if (r.getLong(1) == 0) try (var p = c.prepareStatement("INSERT INTO rb_protocolo VALUES(1,2,?,2,?,?,?,?,?,0,FALSE)")) {
                        p.setInt(1, creacion ? 0 : 1); p.setString(2, id); p.setString(3, a.id()); p.setString(4, a.huella());
                        p.setString(5, a.preservacion() == null ? "" : a.preservacion().toString()); p.setString(6, funcional); p.executeUpdate();
                    }
                }
                UUID.fromString(id);
                if (!creacion && (!a.huella().equals(huella(a.preservacion().resolve("taller"))) || !funcional.equals(funcional(c))))
                    throw new IllegalStateException("Preservación/contenido alterado");
            }
            if (paso < 0 || paso > pasos.size()) throw new IllegalStateException("Paso desconocido");
            for (int i = paso; i < pasos.size(); i++) {
                // Sólo precondición exacta o postcondición exacta del siguiente DDL.
                var actual = estructura(c);
                var previa = esperada(creacion, pasos, i);
                var posterior = esperada(creacion, pasos, i + 1);
                if (actual.equals(previa)) { ejecutar(c, pasos.get(i)); frontera.paso("DDL_" + i); }
                else if (!actual.equals(posterior)) throw new IllegalStateException("Estructura intermedia inesperada en paso " + i);
                if (!estructura(c).equals(posterior)) throw new IllegalStateException("Postcondición DDL incumplida");
                ejecutar(c, "UPDATE rb_protocolo SET paso=" + (i + 1) + " WHERE singleton=1"); frontera.paso("PASO_" + i);
            }
            // Semillas e identidad finales son un único DML. Un reintento no reseedea.
            boolean completo;
            try (var st = c.createStatement(); var r = st.executeQuery("SELECT completo FROM rb_protocolo")) { r.next(); completo = r.getBoolean(1); }
            if (!completo) {
                c.setAutoCommit(false);
                try {
                    if (!creacion && !funcional.equals(funcional(c))) throw new IllegalStateException("Preservación funcional incumplida");
                    if (creacion) {
                        ejecutar(c, "INSERT INTO contador VALUES(1,0,0)");
                        for (var tipo : TipoHerramienta.values()) ejecutar(c, "INSERT INTO catalogo VALUES('" + tipo.name() + "')");
                    }
                    try (var p = c.prepareStatement("INSERT INTO rb_contexto VALUES(1,?,2,?,0,?,NULL,NULL)")) {
                        p.setString(1, id); p.setLong(2, creacion ? 0 : new RepositorioTallerJdbc(c).revision()); p.setBoolean(3, !creacion); p.executeUpdate();
                    }
                    var repo = new RepositorioTallerJdbc(c);
                    var errores = new AuditorCoherencia().auditar(repo.cargar(id), repo.historial(), repo.revision(),
                            new Datos(new Metadata(id, creacion ? 0 : repo.revision(), 0, !creacion), List.of(), List.of(), Map.of(),
                                    antiguedad(c), RepositorioRecuperacionJdbc.huérfanas(c)), false);
                    if (!errores.isEmpty()) throw new IllegalStateException("Auditoría final antes de publicar: " + errores);
                    ejecutar(c, "INSERT INTO rb_version VALUES(2)"); ejecutar(c, "UPDATE rb_protocolo SET completo=TRUE WHERE singleton=1");
                    frontera.paso("ANTES_PUBLICAR"); c.commit(); frontera.paso("PUBLICADO");
                } catch (Exception e) { try { c.rollback(); } catch (Exception r) { e.addSuppressed(r); } throw e; }
            }
            verificarVersion(c, 2); validarEstructura(c, 2);
            return id;
    }
    private static String auditarPreservada(GuardiaInstancia guardia, AutorizacionBase a) throws Exception {
        Path trabajo = Files.createTempDirectory(guardia.directorio(), "auditoria-copia-");
        for (var p : inventario(a.preservacion().resolve("taller"))) Files.copy(p, trabajo.resolve(p.getFileName()));
        try (var privada = new ConexionPrivada(DriverManager.getConnection(url(trabajo.resolve("taller")) + ";IFEXISTS=TRUE", "sa", ""))) {
            var c = privada.conexion;
            validarEstructura(c, 1); verificarVersion(c, 1);
            var repo = new RepositorioTallerJdbc(c);
            var errores = new AuditorCoherencia().auditar(repo.cargar("V1"), repo.historial(), repo.revision(),
                    new Datos(new Metadata("V1", repo.revision(), 0, true), List.of(), List.of(), Map.of(), antiguedad(c), RepositorioRecuperacionJdbc.huérfanas(c)), false);
            if (!errores.isEmpty()) throw new IllegalStateException("v1 incoherente: " + errores);
            return funcional(c);
        }
    }
    private static long antiguedad(Connection c) throws SQLException {
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT antiguedad FROM contador")) { if (!r.next()) throw new SQLException("Sin contador"); return r.getLong(1); }
    }
    static void verificarWriteDelay(Connection c) throws SQLException {
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='WRITE_DELAY'")) {
            if (!r.next() || !r.getString(1).equals("0")) throw new SQLException("WRITE_DELAY no es 0");
        }
    }
    static void verificarVersion(Connection c, int version) throws SQLException {
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT version FROM rb_version")) {
            if (!r.next() || r.getInt(1) != version || r.next()) throw new SQLException("Versión desconocida/incompleta");
        }
    }
    /** Huella lógica de todas las filas funcionales, independiente del orden de SELECT. */
    static String funcional(Connection c) throws Exception {
        var filas = new ArrayList<String>();
        for (String tabla : TABLAS_NEGOCIO) try (var st = c.createStatement(); var r = st.executeQuery("SELECT * FROM " + tabla)) {
            while (r.next()) {
                var campos = new ArrayList<String>(); campos.add(tabla);
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                    String v = r.getString(i); campos.add(v == null ? "~" : Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)));
                }
                filas.add(String.join(".", campos));
            }
        }
        Collections.sort(filas); return hash(String.join("\n", filas).getBytes(StandardCharsets.UTF_8));
    }
    private static List<String> estructura(Connection c) throws SQLException {
        var filas = new ArrayList<String>();
        String[] consultas = {
            "SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,DATA_TYPE,IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE,IS_IDENTITY,COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME,ORDINAL_POSITION",
            "SELECT t.TABLE_NAME,t.CONSTRAINT_TYPE,k.COLUMN_NAME,k.ORDINAL_POSITION FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k ON t.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA='PUBLIC' AND t.CONSTRAINT_TYPE<>'CHECK'",
            "SELECT t.TABLE_NAME,ch.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS ch ON ch.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND ch.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA='PUBLIC'",
            "SELECT t.TABLE_NAME,k.COLUMN_NAME,u.TABLE_NAME,u.COLUMN_NAME,k.ORDINAL_POSITION FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS f JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t ON t.CONSTRAINT_SCHEMA=f.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=f.CONSTRAINT_NAME JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA=f.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=f.CONSTRAINT_NAME JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE u ON u.CONSTRAINT_SCHEMA=f.UNIQUE_CONSTRAINT_SCHEMA AND u.CONSTRAINT_NAME=f.UNIQUE_CONSTRAINT_NAME AND u.ORDINAL_POSITION=k.POSITION_IN_UNIQUE_CONSTRAINT WHERE t.TABLE_SCHEMA='PUBLIC'"
        };
        for (int q = 0; q < consultas.length; q++) try (var st = c.createStatement(); var r = st.executeQuery(consultas[q])) {
            while (r.next()) {
                var fila = new StringBuilder(Integer.toString(q));
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) fila.append('|').append(r.getString(i));
                filas.add(fila.toString());
            }
        }
        Collections.sort(filas); return filas;
    }
    private static List<String> esperada(boolean nueva, List<String> pasos, int cantidad) throws Exception {
        try (var memoria = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
            if (!nueva) for (String ddl : sql("esquema.sql")) ejecutar(memoria, ddl.replace("version = 2", "version = 1"));
            for (String ddl : sql("protocolo.sql")) ejecutar(memoria, ddl);
            for (int i = 0; i < cantidad; i++) ejecutar(memoria, pasos.get(i));
            return estructura(memoria);
        }
    }
    private static synchronized List<String> plantilla(int version) throws Exception {
        var existente = PLANTILLAS.get(version); if (existente != null) return existente;
        var pasos = new ArrayList<>(sql("esquema.sql")); pasos.addAll(sql("migracion-v1-v2.sql"));
        List<String> esperada;
        if (version == 2) esperada = esperada(true, pasos, pasos.size());
        else try (var memoria = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")) {
            for (String ddl : sql("esquema.sql")) ejecutar(memoria, ddl.replace("version = 2", "version = 1"));
            esperada = estructura(memoria);
        }
        var plantilla = List.copyOf(esperada); PLANTILLAS.put(version, plantilla); return plantilla;
    }
    static void validarEstructura(Connection c, int version) throws Exception {
        if (!estructura(c).equals(plantilla(version))) throw new SQLException("INCONSISTENCIA: estructura/constraints incompatible");
    }
}
