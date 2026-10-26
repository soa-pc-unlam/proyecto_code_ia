package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static ar.edu.unlam.reparabarrio.persistencia.H4Fixture.ok;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(180)
class RecuperacionH4Test {
    @AfterEach void cerrar() throws Exception { TestContextos.cerrarTodos(); }

    @Test void h4T01_cierreLimpioYReaperturaJVMIndependienteCincoEstados() throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); String filas, uuid; long pid;
        try (var a = new ProcesosH4(raiz, "limpio")) {
            assertEquals(0, a.finalizar(), () -> a.salida.toString()); filas = a.valor("FILAS="); uuid = a.valor("UUID="); pid = a.proceso.pid();
        }
        try (var b = new ProcesosH4(raiz, "verificar", "siguiente")) {
            assertEquals(0, b.finalizar(), () -> b.salida.toString()); assertNotEquals(pid, b.proceso.pid());
            assertEquals(filas, b.valor("FILAS=")); assertEquals(uuid, b.valor("UUID="));
            assertEquals("0", b.valor("REVISIONES=")); assertEquals("2", b.valor("ASIGNACIONES=")); assertEquals("7", b.valor("SIGUIENTE="));
        }
    }
    @Test void h4T02_caidaConDosEnCursoRecuperacionesBYCIdempotentes() throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); String filas, uuid;
        try (var a = new ProcesosH4(raiz, "caida")) {
            a.esperar("FRONTERA=ABIERTA"); filas = a.valor("FILAS="); uuid = a.valor("UUID="); a.terminar();
        }
        for (int i = 0; i < 2; i++) try (var b = new ProcesosH4(raiz, "verificar")) {
            assertEquals(0, b.finalizar(), () -> b.salida.toString()); assertEquals(filas, b.valor("FILAS="));
            assertEquals(uuid, b.valor("UUID=")); assertEquals("2", b.valor("REVISIONES=")); assertEquals("2", b.valor("ASIGNACIONES="));
        }
        try (var f = new H4Fixture(raiz.resolve("taller"), null)) {
            f.evidencia("T02 TERCERA_APERTURA"); f.verificarSesionPersistida();
            var previas = f.vista();
            assertEquals(Set.of("CURSO1", "CURSO2"), previas.dominio().asignaciones().keySet());
            assertEquals("T1", previas.dominio().asignaciones().get("CURSO1").tecnico().id());
            assertEquals("T2", previas.dominio().asignaciones().get("CURSO2").tecnico().id());
            f.orden("NUEVA_SIN_HERRAMIENTAS"); var propuesta = f.propuesta();
            assertEquals("NUEVA_SIN_HERRAMIENTAS", propuesta.ordenId());
            assertEquals("T3", propuesta.recursos().tecnico().id()); assertEquals("P3", propuesta.recursos().puesto().id());
            ok(f.servicio.confirmarInicio(propuesta)); assertEquals(3, f.numero("SELECT COUNT(*) FROM asignaciones"));
            assertNull(f.vista().revisiones().get("CURSO1").cierreId()); assertNull(f.vista().revisiones().get("CURSO2").cierreId());
        }
    }
    private Path caida() throws Exception {
        Path raiz = TestContextos.nuevaRaiz();
        try (var a = new ProcesosH4(raiz, "caida")) { a.esperar("FRONTERA=ABIERTA"); a.terminar(); }
        return raiz;
    }
    @Test void h4T03_revisionRollbackYCarreraDeCierresConReasignacion() throws Exception {
        Path raiz = caida();
        try (var f = new H4Fixture(raiz.resolve("taller"), null)) {
            f.evidencia("T03 ANTES_INTENCION"); f.verificarSesionPersistida();
            String antes = f.filas(); var marca = f.vista().revisiones().get("CURSO1"); assertNull(marca.cierreId());
            f.negocios.reiniciar();
            var control = new H4Fixture.Control("RR_RESOLVERREVISION"); f.negocio.set(control);
            var resultado = f.servicio.finalizar("CURSO1");
            f.evidencia("T03 DESPUES_ROLLBACK");
            System.out.println("H4-T03 resultado=" + resultado + " informe=" + f.contexto.informe());
            assertTrue(control.usado.get(), resultado::diagnostico);
            assertEquals(0, f.negocios.commits.get(), resultado::diagnostico);
            assertEquals(1, f.negocios.rollbacks.get(), resultado::diagnostico);
            assertEquals(ERROR_TECNICO, resultado.estado(), resultado::diagnostico); assertEquals(antes, f.filas());
            assertNull(f.vista().revisiones().get("CURSO1").cierreId()); f.negocio.set(null);
            var pool = Executors.newFixedThreadPool(2); var barrera = new CyclicBarrier(2);
            try {
                var fin = pool.submit(() -> { barrera.await(10, TimeUnit.SECONDS); return f.servicio.finalizar("CURSO1"); });
                var fallo = pool.submit(() -> { barrera.await(10, TimeUnit.SECONDS); return f.servicio.fallar("CURSO1"); });
                var resultados = List.of(fin.get(30, TimeUnit.SECONDS), fallo.get(30, TimeUnit.SECONDS));
                assertEquals(1, resultados.stream().filter(r -> r.estado() == CONFIRMADA).count());
                assertEquals(1, resultados.stream().filter(r -> r.estado() == RECHAZADA).count());
                var cierre = resultados.stream().filter(r -> r.estado() == CONFIRMADA).findFirst().orElseThrow();
                assertEquals(cierre.operacionId(), f.vista().revisiones().get("CURSO1").cierreId());
            } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
            f.orden("NUEVA", TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO); ok(f.servicio.confirmarInicio(f.propuesta()));
            var asignacion = f.vista().dominio().asignaciones().get("NUEVA"); assertNotNull(asignacion);
            assertEquals(RECHAZADA, f.servicio.fallar("CURSO1").estado()); assertEquals(asignacion, f.vista().dominio().asignaciones().get("NUEVA"));
            assertEquals(RECHAZADA, f.servicio.cancelar("CURSO2").estado()); ok(f.servicio.fallar("CURSO2"));
            assertNotNull(f.vista().revisiones().get("CURSO2").cierreId());
        }
    }
    @ParameterizedTest @CsvSource({"REGISTRO,RR_PREPARAR_ANTES", "REGISTRO,INTENCION_DURABLE", "REGISTRO,EVENTO",
            "INICIO,RR_PREPARAR_ANTES", "INICIO,INTENCION_DURABLE", "INICIO,EVENTO",
            "CIERRE,RR_PREPARAR_ANTES", "CIERRE,INTENCION_DURABLE", "CIERRE,EVENTO"})
    void h4T06_caidaAntesDespuesIntencionYEscriturasSinCommit(String accion, String fase) throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); String antes;
        try (var a = new ProcesosH4(raiz, "mutacion", accion, fase)) {
            a.esperar("FRONTERA=" + fase); antes = a.valor("ANTERIOR="); a.terminar();
        }
        try (var b = new ProcesosH4(raiz, "verificar")) {
            assertEquals(0, b.finalizar(), () -> b.salida.toString()); assertEquals(antes, b.valor("FILAS="));
            if (!fase.equals("RR_PREPARAR_ANTES")) assertTrue(b.valor("OPERACIONES=").contains("NO_CONFIRMADA"));
        }
    }
    @ParameterizedTest @CsvSource({"REGISTRO,COMMIT_ANTES", "REGISTRO,COMMIT_INVOCADO", "REGISTRO,COMMIT_DESPUES",
            "INICIO,COMMIT_ANTES", "INICIO,COMMIT_INVOCADO", "INICIO,COMMIT_DESPUES",
            "CIERRE,COMMIT_ANTES", "CIERRE,COMMIT_INVOCADO", "CIERRE,COMMIT_DESPUES"})
    void h4T07_caidasAlrededorCommitSinAfirmarInteriorDeterminista(String accion, String fase) throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); String antes;
        try (var a = new ProcesosH4(raiz, "mutacion", accion, fase)) {
            a.esperar("FRONTERA=" + fase); antes = a.valor("ANTERIOR="); a.terminar();
        }
        try (var f = new H4Fixture(raiz.resolve("taller"), null)) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.informe().ciclo(), () -> f.contexto.informe().toString());
            long evento = f.numero("SELECT COUNT(*) FROM rb_intenciones WHERE resolucion='NO_CONFIRMADA'");
            if (fase.equals("COMMIT_ANTES")) { assertEquals(antes, f.filas()); assertEquals(1, evento); }
            if (fase.equals("COMMIT_DESPUES")) { assertNotEquals(antes, f.filas()); assertEquals(0, evento); }
            // Oráculo independiente: estado anterior o efecto íntegro, filas y testigo exacto.
            boolean confirmado = evento == 0;
            if (confirmado) switch (accion) {
                case "REGISTRO" -> { assertEquals(2, f.numero("SELECT antiguedad FROM contador")); assertEquals(1, f.numero("SELECT COUNT(*) FROM ordenes WHERE id='B' AND estado='PENDIENTE'")); }
                case "INICIO" -> { assertEquals(1, f.numero("SELECT COUNT(*) FROM asignaciones")); assertEquals(2, f.numero("SELECT COUNT(*) FROM herramientas_activas")); assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_revisiones WHERE cierre_id IS NULL")); }
                case "CIERRE" -> { assertEquals(0, f.numero("SELECT COUNT(*) FROM asignaciones")); assertEquals(0, f.numero("SELECT COUNT(*) FROM herramientas_activas")); assertEquals(1, f.numero("SELECT COUNT(*) FROM ordenes WHERE id='A' AND estado='FINALIZADA'")); }
            } else assertEquals(antes, f.filas());
            assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_intenciones i JOIN historial h ON i.evento=h.secuencia WHERE i.id<>h.operacion_id"));
            System.out.println("H4-T07 " + fase + " interior JDBC no acreditado; anterior/completo confirmado=" + confirmado);
        }
    }
    @ParameterizedTest @CsvSource({"REGISTRO,COMMIT_ANTES", "REGISTRO,COMMIT_DESPUES", "INICIO,COMMIT_ANTES", "INICIO,COMMIT_DESPUES", "CIERRE,COMMIT_ANTES", "CIERRE,COMMIT_DESPUES"})
    void h4T08_commitInstrumentadoReconciliacionExacta(String accion, String fase) throws Exception {
        try (var f = new H4Fixture()) {
            f.capacidad(1); f.herramienta("M", TipoHerramienta.MULTIMETRO, true); f.herramienta("D", TipoHerramienta.TALADRO, true); f.orden("A", TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO);
            var p = f.propuesta(); if (accion.equals("CIERRE")) ok(f.servicio.confirmarInicio(p));
            String antes = f.filas(); f.negocios.reiniciar(); f.tecnicas.reiniciar(); f.negocio.set(new H4Fixture.Control(fase));
            var r = switch (accion) { case "REGISTRO" -> f.servicio.registrarOrden("B", "H4 B", List.of()); case "INICIO" -> f.servicio.confirmarInicio(p); default -> f.servicio.finalizar("A"); };
            assertEquals(PENDIENTE_VERIFICACION, r.estado()); assertNull(r.valor()); assertNotNull(r.operacionId());
            assertEquals(1, f.negocios.aperturas.get()); assertEquals(1, f.negocios.commits.get()); assertEquals(0, f.negocios.rollbacks.get());
            int tecnicas = f.tecnicas.aperturas.get(); assertEquals(2, tecnicas); // intención + diagnóstico, nunca simultáneas.
            assertEquals(PENDIENTE_VERIFICACION, f.servicio.consultar().estado()); assertEquals(PENDIENTE_VERIFICACION, f.servicio.cancelar("A").estado());
            assertEquals(1, f.negocios.aperturas.get()); assertEquals(tecnicas, f.tecnicas.aperturas.get()); f.negocio.set(null);
            var informe = f.contexto.recuperacion().recuperar(); assertEquals(ContextoTaller.Ciclo.HABILITADO, informe.ciclo());
            assertEquals(fase.equals("COMMIT_DESPUES") ? "CONFIRMADA" : "NO_CONFIRMADA", informe.operaciones().get(r.operacionId()));
            if (fase.equals("COMMIT_ANTES")) assertEquals(antes, f.filas());
            else assertEquals(1, f.numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + r.operacionId() + "'"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"RR_PREPARAR", "COMMIT_ANTES", "COMMIT_DESPUES"})
    void h4T09_intencionFallidaNoIniciaNegocio(String fase) throws Exception {
        try (var f = new H4Fixture()) {
            String antes = f.filas(); f.infraestructura.set(new H4Fixture.Control(fase));
            var r = f.servicio.registrarOrden("A", "H4 A", List.of()); assertEquals(PENDIENTE_VERIFICACION, r.estado());
            assertEquals(0, f.negocios.aperturas.get()); assertEquals(antes, f.filas());
            f.infraestructura.set(null); assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.recuperacion().recuperar().ciclo());
        }
    }
    @ParameterizedTest @ValueSource(strings={"RR_RESOLVER", "RR_RESOLVERREVISION"})
    void h4T09_resolucionAtomicaFallidaRevierteTodo(String fase) throws Exception {
        try (var f = new H4Fixture()) {
            f.capacidad(1); f.orden("A"); ok(f.servicio.confirmarInicio(f.propuesta())); String antes = f.filas();
            f.negocio.set(new H4Fixture.Control(fase)); assertEquals(ERROR_TECNICO, f.servicio.finalizar("A").estado());
            assertEquals(antes, f.filas()); assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_intenciones WHERE resolucion='PENDIENTE'"));
        }
    }
    @Test void h4T09_diagnosticoFallidoConservaTestigoYSesionPrevia() throws Exception {
        try (var f = new H4Fixture()) {
            f.negocio.set(new H4Fixture.Control("COMMIT_DESPUES")); f.infraestructura.set(new H4Fixture.Control("RR_DIAGNOSTICAR"));
            var r = f.servicio.registrarOrden("A", "H4 A", List.of()); assertEquals(PENDIENTE_VERIFICACION, r.estado());
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_sesiones WHERE abierta=TRUE"));
            assertEquals(1, f.numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + r.operacionId() + "'"));
            f.negocio.set(null); f.infraestructura.set(null); assertEquals("CONFIRMADA", f.contexto.recuperacion().recuperar().operaciones().get(r.operacionId()));
        }
    }
    @Test void h4T12_lecturaBNoEscribeYRollbackFallidoBloquea() throws Exception {
        try (var f = new H4Fixture()) {
            f.orden("A"); long intentos = f.numero("SELECT COUNT(*) FROM rb_intenciones"); String antes = f.filas();
            f.tecnicas.reiniciar(); f.negocios.reiniciar(); f.vista();
            assertEquals(0, f.tecnicas.aperturas.get()); assertEquals(0, f.negocios.commits.get()); assertEquals(1, f.negocios.rollbacks.get());
            assertEquals(intentos, f.numero("SELECT COUNT(*) FROM rb_intenciones")); assertEquals(antes, f.filas());
            f.negocio.set(new H4Fixture.Control("ROLLBACK")); var r = f.servicio.consultar();
            assertEquals(PENDIENTE_VERIFICACION, r.estado()); assertNull(r.valor());
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_intenciones WHERE id='" + r.operacionId() + "' AND clase='LECTURA'"));
            int aperturas = f.negocios.aperturas.get(); assertEquals(PENDIENTE_VERIFICACION, f.servicio.consultar().estado()); assertEquals(aperturas, f.negocios.aperturas.get());
            f.negocio.set(null); assertEquals("VERIFICADA", f.contexto.recuperacion().recuperar().operaciones().get(r.operacionId()));
        }
    }
    @Test void h4T12_cierreIncompletoRetieneGuardiaHastaTerminarConexion() throws Exception {
        try (var f = new H4Fixture()) {
            f.negocio.set(new H4Fixture.Control("CIERRE_ANTES", true, true, s -> { }));
            var r = f.servicio.consultar(); assertEquals(ERROR_TECNICO, r.estado()); assertNull(r.valor());
            assertThrows(Exception.class, f.contexto::close);
            assertThrows(Exception.class, () -> ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.adquirir(f.base.getParent()));
            int aperturas = f.tecnicas.aperturas.get(); assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.recuperacion().recuperar().ciclo());
            assertEquals(aperturas, f.tecnicas.aperturas.get()); f.negocio.set(null);
        }
        Path base = TestContextos.nuevaRaiz().resolve("taller");
        var tecnicas = new TestContextos.ContadoresFase();
        var parcial = TestContextos.abrir(base, fabrica -> () -> {
            var real = fabrica.abrir();
            throw new ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo.AperturaIncompleta(real, new SQLException("Configuración/cierre incompleto de ensayo"));
        }, tecnicas::decorar, new CoordinadorTaller(), null, TestContextos.solicitud(base));
        tecnicas.reiniciar();
        assertEquals(ERROR_TECNICO, parcial.servicio().consultar().estado());
        assertEquals(ContextoTaller.Ciclo.BLOQUEADO, parcial.recuperacion().recuperar().ciclo()); assertEquals(0, tecnicas.aperturas.get());
        assertThrows(Exception.class, () -> ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.adquirir(base.getParent()));
        parcial.close();
    }
    @Test void h4T14_contextoSanoAvanzaMientrasOtroIncierto() throws Exception {
        try (var a = new H4Fixture(); var b = new H4Fixture()) {
            a.negocio.set(new H4Fixture.Control("COMMIT_DESPUES")); assertEquals(PENDIENTE_VERIFICACION, a.servicio.registrarOrden("A", "H4 A", List.of()).estado());
            b.capacidad(1); b.orden("A"); var p = b.propuesta();
            assertEquals(PENDIENTE_VERIFICACION, a.servicio.confirmarInicio(p).estado()); ok(b.servicio.confirmarInicio(p));
            assertNotEquals(a.contexto.identidad(), b.contexto.identidad()); assertEquals(1, a.negocios.aperturas.get());
            a.negocio.set(null); a.contexto.recuperacion().recuperar(); assertEquals(RECHAZADA, a.servicio.confirmarInicio(p).estado());
        }
    }
    @Test void h4T16_mismoUuidDescriptorTestigoResolucionYDistintosIntentos() throws Exception {
        try (var f = new H4Fixture()) {
            f.capacidad(1); f.herramienta("M", TipoHerramienta.MULTIMETRO, true); f.orden("A", TipoHerramienta.MULTIMETRO);
            var p = f.propuesta(); var inicio = f.servicio.confirmarInicio(p); ok(inicio);
            var rechazo = f.servicio.confirmarInicio(p); assertEquals(RECHAZADA, rechazo.estado()); assertNotEquals(inicio.operacionId(), rechazo.operacionId());
            var fin = f.servicio.finalizar("A"); ok(fin); assertNotEquals(inicio.operacionId(), fin.operacionId());
            var clave = p.recursos().tecnico(); var sinCambio = f.servicio.cambiarHabilitacion(clave, true); assertEquals(SIN_CAMBIO, sinCambio.estado());
            ok(f.servicio.cambiarHabilitacion(clave, false)); f.orden("B"); ok(f.servicio.cancelar("B"));
            assertEquals(0, f.numero("SELECT COUNT(*) FROM historial h LEFT JOIN rb_intenciones i ON i.id=h.operacion_id WHERE i.id IS NULL OR i.evento<>h.secuencia OR i.resolucion<>'CONFIRMADA'"));
            assertEquals(0, f.numero("SELECT COUNT(*) FROM historial WHERE operacion_id IN ('" + rechazo.operacionId() + "','" + sinCambio.operacionId() + "')"));
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_intenciones WHERE id='" + inicio.operacionId() + "' AND evento=(SELECT secuencia FROM historial WHERE operacion_id='" + inicio.operacionId() + "')"));
            try (var c = f.conexion(); var st = c.createStatement()) {
                assertThrows(SQLException.class, () -> st.executeUpdate("INSERT INTO rb_intenciones SELECT * FROM rb_intenciones WHERE id='" + inicio.operacionId() + "'"));
            }
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.recuperacion().recuperar().ciclo());
        }
    }
    @Test void h4T18_confirmadaConCierrePosteriorFallidoConservaValor() throws Exception {
        try (var f = new H4Fixture()) {
            f.negocio.set(new H4Fixture.Control("CIERRE_DESPUES")); var r = f.servicio.registrarOrden("A", "H4 A", List.of());
            ok(r); assertNotNull(r.valor()); assertTrue(r.diagnostico().contains("Fallo al cerrar conexión"));
            assertEquals(1, f.numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + r.operacionId() + "'"));
            f.negocio.set(null); assertEquals(1, f.vista().dominio().ordenes().size());
        }
    }
    @ParameterizedTest @ValueSource(strings={"RR_ABRIRSESION", "COMMIT_ANTES", "COMMIT_DESPUES"})
    void h4T09_marcaAbiertaFallidaNoHabilita(String fase) throws Exception {
        Path base = TestContextos.nuevaRaiz().resolve("taller");
        try (var f = new H4Fixture(base, null, new H4Fixture.Control(fase))) {
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.informe().ciclo());
            assertNull(f.servicio.consultar().valor()); assertEquals(0, f.negocios.aperturas.get());
            f.infraestructura.set(null); assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.recuperacion().recuperar().ciclo());
        }
    }
    @Test void h4T09_revisionFallidaConservaSesionAbiertaYAsignaciones() throws Exception {
        Path raiz = caida();
        var control = new H4Fixture.Control("RR_REVISAR");
        try (var f = new H4Fixture(raiz.resolve("taller"), null, control)) {
            assertTrue(control.usado.get(), f.contexto.informe()::toString);
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.informe().ciclo());
            assertEquals(2, f.numero("SELECT COUNT(*) FROM asignaciones")); assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_revisiones"));
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_sesiones WHERE abierta=TRUE"));
            f.evidencia("T09 DESPUES_REVISION_REVERTIDA");
            f.infraestructura.set(null); var recuperado = f.contexto.recuperacion().recuperar();
            f.evidencia("T09 DESPUES_RECUPERAR");
            System.out.println("H4-T09 informe=" + recuperado);
            assertEquals(ContextoTaller.Ciclo.HABILITADO, recuperado.ciclo(), recuperado::toString);
            assertEquals(2, f.numero("SELECT COUNT(*) FROM asignaciones"));
            assertEquals(2, f.numero("SELECT COUNT(*) FROM rb_revisiones WHERE cierre_id IS NULL"));
            assertEquals(Set.of("CURSO1", "CURSO2"), recuperado.revisiones().keySet());
            assertEquals(2, f.vista().revisiones().values().stream().filter(r -> r.cierreId() == null).count());
        }
    }
    @Test void h4T09_marcaLimpiaFallidaNoOcultaInterrupcion() throws Exception {
        Path base;
        try (var f = new H4Fixture()) {
            base = f.base; f.capacidad(1); f.orden("A"); ok(f.servicio.confirmarInicio(f.propuesta()));
            f.infraestructura.set(new H4Fixture.Control("RR_LIMPIARSESION")); f.contexto.close();
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_sesiones WHERE abierta=TRUE"));
        }
        try (var f = new H4Fixture(base, null)) { assertNull(f.vista().revisiones().get("A").cierreId()); assertEquals(1, f.numero("SELECT COUNT(*) FROM asignaciones")); }
    }
    @Test void h4T12_caidaAntesDeDiagnosticoNoInventaIdDeLectura() throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); String antes;
        try (var a = new ProcesosH4(raiz, "lectura")) { a.esperar("FRONTERA=ANTES_DIAGNOSTICO_LECTURA"); antes = a.valor("ANTERIOR="); a.terminar(); }
        try (var f = new H4Fixture(raiz.resolve("taller"), null)) {
            assertEquals(antes, f.filas()); assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_intenciones WHERE clase='LECTURA'"));
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.informe().ciclo());
        }
    }
    @Test void h4T08_perdidaRealConexionTrasEscrituraRollbackImposible() throws Exception {
        Path base = TestContextos.nuevaRaiz().resolve("taller"); var cuenta = new java.util.concurrent.atomic.AtomicInteger();
        var contexto = TestContextos.abrir(base, fabrica -> () -> {
            var real = fabrica.abrir(); cuenta.incrementAndGet();
            var repo = (ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioTaller) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioTaller.class}, (p, m, a) -> {
                Object valor; try { valor = m.invoke(real.repositorio(), a); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                if (m.getName().equals("evento")) { real.close(); throw new SQLException("Conexión perdida después de escribir", "08006"); }
                return valor;
            });
            return new ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo() {
                public ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioTaller repositorio() { return repo; }
                public ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion recuperacion() { return real.recuperacion(); }
                public void commit() throws Exception { real.commit(); }
                public void rollback() throws Exception { real.rollback(); }
                public void close() throws Exception { real.close(); }
                public boolean terminada() { return real.terminada(); }
            };
        }, java.util.function.UnaryOperator.identity(), new CoordinadorTaller(), null, TestContextos.solicitud(base));
        var r = contexto.servicio().registrarOrden("A", "Conexión perdida", List.of());
        assertEquals(PENDIENTE_VERIFICACION, r.estado()); assertNull(r.valor());
        assertEquals(PENDIENTE_VERIFICACION, contexto.servicio().consultar().estado()); assertEquals(1, cuenta.get());
        assertEquals("NO_CONFIRMADA", contexto.recuperacion().recuperar().operaciones().get(r.operacionId()));
        try (var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM ordenes")) { rs.next(); assertEquals(0, rs.getLong(1)); }
    }
    @Test void h4T09_resolucionSinEfectoFallidaPreservaResultadoYBloqueaSalud() throws Exception {
        try (var f = new H4Fixture()) {
            f.capacidad(1); String antes = f.filas();
            f.infraestructura.set(new H4Fixture.Control("RR_RESOLVER"));
            var r = f.servicio.cambiarHabilitacion(new Recurso.Clave(Recurso.Categoria.TECNICO, "T1"), true);
            assertEquals(SIN_CAMBIO, r.estado()); assertEquals(antes, f.filas()); assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.ciclo());
            int negocios = f.negocios.aperturas.get(), tecnicas = f.tecnicas.aperturas.get();
            assertNull(f.servicio.consultar().valor()); assertEquals(negocios, f.negocios.aperturas.get()); assertEquals(tecnicas, f.tecnicas.aperturas.get());
            f.infraestructura.set(null); assertEquals("NO_CONFIRMADA", f.contexto.recuperacion().recuperar().operaciones().get(r.operacionId()));
        }
    }
    @Test void h4T18_confirmadaConConexionDudosaBloqueaSinDegradarNegocio() throws Exception {
        try (var f = new H4Fixture()) {
            f.negocio.set(new H4Fixture.Control("CIERRE_ANTES", true, true, s -> { }));
            var r = f.servicio.registrarOrden("A", "H4 A", List.of()); ok(r); assertNotNull(r.valor());
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.ciclo()); assertNull(f.servicio.consultar().valor());
            assertThrows(Exception.class, f.contexto::close); f.negocio.set(null);
            f.contexto.close();
            try (var reabierta = new H4Fixture(f.base, null)) {
                assertEquals("CONFIRMADA", reabierta.contexto.informe().operaciones().get(r.operacionId()));
                assertEquals(1, reabierta.numero("SELECT COUNT(*) FROM ordenes WHERE id='A'"));
            }
        }
    }
}
