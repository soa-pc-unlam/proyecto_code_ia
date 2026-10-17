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


def borrar_archivo_excel(configuracion=None):
    """Elimina el libro Excel de métricas si existe."""
    configuracion = cargar_configuracion(configuracion)

    archivo_excel = Path(configuracion["archivo_excel_salida"])

    if archivo_excel.exists():
        try:
            archivo_excel.unlink()
            print(f"Eliminado: {archivo_excel}")
        except Exception as e:
            print(f"Error eliminando {archivo_excel}: {e}")
    else:
        print(f"No existe: {archivo_excel}")


def borrar_archivos_excel():
    """Elimina los libros Excel de métricas para ambos tipos de proyecto."""
    for tipo_proyecto in [definiciones.TIPO_PROYECTO_PC, definiciones.TIPO_PROYECTO_ESP32_ANDROID]:
        if tipo_proyecto == definiciones.TIPO_PROYECTO_PC:
            configuracion = definiciones.CONFIGURACION_JSON_PC
        elif tipo_proyecto == definiciones.TIPO_PROYECTO_ESP32_ANDROID:
            configuracion = definiciones.CONFIGURACION_JSON_ESP32_ANDROID
        borrar_archivo_excel(configuracion)
        
def main():
    """Ejecuta la limpieza de archivos generados."""
    borrar_archivos_directorios()
    borrar_archivos_excel()
    print("Proceso finalizado.")


if __name__ == "__main__":
    main()
