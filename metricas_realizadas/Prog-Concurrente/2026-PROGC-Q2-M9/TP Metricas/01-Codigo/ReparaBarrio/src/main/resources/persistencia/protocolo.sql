CREATE TABLE rb_protocolo (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    protocolo INTEGER NOT NULL CHECK(protocolo=2),
    origen INTEGER NOT NULL CHECK(origen IN (0,1)), destino INTEGER NOT NULL CHECK(destino=2),
    base_id VARCHAR NOT NULL, autorizacion VARCHAR NOT NULL, huella VARCHAR NOT NULL,
    preservacion VARCHAR NOT NULL, funcional VARCHAR NOT NULL, paso INTEGER NOT NULL CHECK(paso>=0),
    completo BOOLEAN NOT NULL
);
