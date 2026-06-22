from pathlib import Path


def borrar_archivos_directorios():
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
    archivo_excel = Path("metricas_calidad.xlsx")

    if archivo_excel.exists():
        try:
            archivo_excel.unlink()
            print(f"Eliminado: {archivo_excel}")
        except Exception as e:
            print(f"Error eliminando {archivo_excel}: {e}")
    else:
        print(f"No existe: {archivo_excel}")


def main():
    borrar_archivos_directorios()
    borrar_archivo_excel()
    print("Proceso finalizado.")


if __name__ == "__main__":
    main()