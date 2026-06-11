# Ejecucion con Docker Compose

Esta configuracion levanta el sistema completo en contenedores:

- `db`: PostgreSQL 18 con schema y seed inicial.
- `api`: backend FastAPI en `http://localhost:8000`.
- `load-test`: batch opcional para simular reservas concurrentes.

## Levantar la aplicacion

```powershell
docker compose up --build
```

Luego abrir:

```text
http://localhost:8000
```

Usuario de prueba:

```text
test_user / test123
```

## Ejecutar el batch concurrente

Con la aplicacion levantada:

```powershell
docker compose --profile test run --rm load-test
```

El batch envia solicitudes concurrentes contra el backend y muestra tiempos de
respuesta, reservas exitosas y reservas rechazadas por conflicto.

## Medir CPU y memoria

En otra terminal, mientras se ejecuta el batch:

```powershell
docker compose stats
```

Registrar para el informe:

- CPU del contenedor `ticket-api`.
- Memoria del contenedor `ticket-api`.
- CPU y memoria del contenedor `ticket-db`.
- Tiempo total informado por el batch.
- Cantidad de reservas exitosas y fallidas.

## Ejecutar prueba de rendimiento

La prueba de rendimiento envia muchas reservas concurrentes sobre asientos
distintos y calcula latencias:

```powershell
docker compose --profile test run --rm perf-test
```

Parametros configurables:

```powershell
$env:TOTAL_REQUESTS=100
$env:CONCURRENCY=25
docker compose --profile test run --rm perf-test
```

Registrar para el informe:

- Tiempo total.
- Throughput en requests por segundo.
- Latencia promedio.
- Latencia maxima.
- Latencia p95.
- Reservas exitosas y fallidas.

## Reiniciar datos desde cero

Si se quiere limpiar la base de datos y volver a cargar schema + seed:

```powershell
docker compose down -v
docker compose up --build
```

El parametro `-v` elimina el volumen de PostgreSQL, por eso borra los datos.
