CREATE TABLE rb_contexto (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    base_id VARCHAR NOT NULL UNIQUE, protocolo INTEGER NOT NULL CHECK(protocolo=2),
    frontera_v1 BIGINT NOT NULL CHECK(frontera_v1>=0),
    revision_tecnica BIGINT NOT NULL CHECK(revision_tecnica>=0),
    adopcion_pendiente BOOLEAN NOT NULL, aceptacion VARCHAR, declaracion VARCHAR
);
CREATE TABLE rb_sesiones (
    id VARCHAR PRIMARY KEY, base_id VARCHAR NOT NULL REFERENCES rb_contexto(base_id),
    abierta BOOLEAN NOT NULL, momento VARCHAR NOT NULL
);
CREATE TABLE rb_intenciones (
    id VARCHAR PRIMARY KEY, base_id VARCHAR NOT NULL REFERENCES rb_contexto(base_id),
    sesion_id VARCHAR NOT NULL REFERENCES rb_sesiones(id),
    clase VARCHAR NOT NULL CHECK(clase IN ('NEGOCIO','LECTURA','INFRAESTRUCTURA')),
    descriptor VARCHAR NOT NULL, revision_base BIGINT NOT NULL CHECK(revision_base>=0),
    resolucion VARCHAR NOT NULL CHECK(resolucion IN ('PENDIENTE','CONFIRMADA','NO_CONFIRMADA','RECHAZADA','SIN_CAMBIO','ERROR_TECNICO','VERIFICADA')),
    evento BIGINT UNIQUE REFERENCES historial(secuencia), diagnostico VARCHAR,
    CHECK((resolucion='CONFIRMADA' AND evento IS NOT NULL AND clase='NEGOCIO')
       OR (resolucion<>'CONFIRMADA' AND evento IS NULL))
);
CREATE TABLE rb_revisiones (
    orden_id VARCHAR PRIMARY KEY REFERENCES ordenes(id),
    sesion_id VARCHAR NOT NULL REFERENCES rb_sesiones(id), motivo VARCHAR NOT NULL, momento VARCHAR NOT NULL,
    cierre_id VARCHAR UNIQUE REFERENCES rb_intenciones(id)
);
