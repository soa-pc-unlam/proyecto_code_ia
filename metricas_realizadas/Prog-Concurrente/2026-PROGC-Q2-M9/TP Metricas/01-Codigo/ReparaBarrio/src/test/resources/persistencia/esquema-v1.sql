CREATE TABLE IF NOT EXISTS rb_version (version INTEGER PRIMARY KEY CHECK (version = 1));
CREATE TABLE IF NOT EXISTS catalogo (tipo VARCHAR PRIMARY KEY CHECK (tipo IN ('MULTIMETRO','SOLDADOR','TALADRO')));
CREATE TABLE IF NOT EXISTS contador (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    antiguedad BIGINT NOT NULL CHECK (antiguedad >= 0),
    revision BIGINT NOT NULL CHECK (revision >= 0)
);
CREATE TABLE IF NOT EXISTS ordenes (
    id VARCHAR PRIMARY KEY, descripcion VARCHAR NOT NULL,
    secuencia BIGINT NOT NULL UNIQUE CHECK (secuencia > 0),
    estado VARCHAR NOT NULL CHECK (estado IN ('PENDIENTE','EN_CURSO','FINALIZADA','FALLIDA','CANCELADA'))
);
CREATE TABLE IF NOT EXISTS requisitos (
    orden_id VARCHAR REFERENCES ordenes(id), tipo VARCHAR REFERENCES catalogo(tipo),
    PRIMARY KEY (orden_id, tipo)
);
CREATE TABLE IF NOT EXISTS tecnicos (id VARCHAR PRIMARY KEY, habilitado BOOLEAN NOT NULL);
CREATE TABLE IF NOT EXISTS puestos (id VARCHAR PRIMARY KEY, habilitado BOOLEAN NOT NULL);
CREATE TABLE IF NOT EXISTS herramientas (
    id VARCHAR PRIMARY KEY, tipo VARCHAR NOT NULL REFERENCES catalogo(tipo), habilitado BOOLEAN NOT NULL,
    UNIQUE (id, tipo)
);
CREATE TABLE IF NOT EXISTS asignaciones (
    orden_id VARCHAR PRIMARY KEY REFERENCES ordenes(id),
    tecnico_id VARCHAR NOT NULL UNIQUE REFERENCES tecnicos(id),
    puesto_id VARCHAR NOT NULL UNIQUE REFERENCES puestos(id)
);
CREATE TABLE IF NOT EXISTS herramientas_activas (
    orden_id VARCHAR NOT NULL REFERENCES asignaciones(orden_id),
    tipo VARCHAR NOT NULL, herramienta_id VARCHAR NOT NULL UNIQUE,
    PRIMARY KEY (orden_id, tipo),
    FOREIGN KEY (orden_id, tipo) REFERENCES requisitos(orden_id, tipo),
    FOREIGN KEY (herramienta_id, tipo) REFERENCES herramientas(id, tipo)
);
CREATE TABLE IF NOT EXISTS historial (
    secuencia BIGINT PRIMARY KEY CHECK (secuencia > 0),
    operacion_id VARCHAR NOT NULL UNIQUE,
    orden_id VARCHAR REFERENCES ordenes(id), accion VARCHAR NOT NULL, momento VARCHAR NOT NULL
);
CREATE TABLE IF NOT EXISTS historial_recursos (
    evento BIGINT NOT NULL REFERENCES historial(secuencia),
    categoria VARCHAR NOT NULL CHECK (categoria IN ('TECNICO','PUESTO','HERRAMIENTA')),
    id VARCHAR NOT NULL,
    tecnico_id VARCHAR REFERENCES tecnicos(id),
    puesto_id VARCHAR REFERENCES puestos(id),
    herramienta_id VARCHAR REFERENCES herramientas(id),
    PRIMARY KEY (evento, categoria, id),
    CHECK ((categoria = 'TECNICO' AND tecnico_id = id AND tecnico_id IS NOT NULL AND puesto_id IS NULL AND herramienta_id IS NULL)
        OR (categoria = 'PUESTO' AND puesto_id = id AND puesto_id IS NOT NULL AND tecnico_id IS NULL AND herramienta_id IS NULL)
        OR (categoria = 'HERRAMIENTA' AND herramienta_id = id AND herramienta_id IS NOT NULL AND tecnico_id IS NULL AND puesto_id IS NULL))
);
