"""Limpia los artefactos generados por ejecuciones anteriores."""

from pathlib import Path

from configuracion.configuracion import cargar_configuracion
from constantes import definiciones


def borrar_archivos_directorios():
    """Elimina los archivos contenidos en los directorios de salida y logs."""
    directorios = [Path("logs"), Path("resultados")]

    for directorio in directorios:
        if not directorio.exists():
            print(f"No existe: {directorio}")
            continue

        for archivo in directorio.rglob("*"):
            if archivo.is_file():
                try:
                    archivo.unlink()
                    print(f"Eliminado: {archivo}")
                except Exception as e:
                    print(f"Error eliminando {archivo}: {e}")


def borrar_archivo_excel():
    """Elimina el libro Excel de métricas si existe."""
    configuracion = cargar_configuracion(definiciones.CONFIGURACION_JSON)

    archivo_excel = Path(configuracion["archivo_excel_salida"])

    if archivo_excel.exists():
        try:
            archivo_excel.unlink()
            print(f"Eliminado: {archivo_excel}")
        except Exception as e:
            print(f"Error eliminando {archivo_excel}: {e}")
    else:
        print(f"No existe: {archivo_excel}")


def main():
    """Ejecuta la limpieza de archivos generados."""
    borrar_archivos_directorios()
    borrar_archivo_excel()
    print("Proceso finalizado.")


if __name__ == "__main__":
    main()
