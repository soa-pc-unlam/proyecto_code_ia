import sys
import subprocess
import csv
from pathlib import Path

def generar_resumen(csv_file, txt_file):
    import csv

    columnas = [
        "NLOC",
        "CCN",
        "token_count",
        "parameter_count",
        "length",
        "location",
        "file",
        "function_name",
        "long_name",
        "start_line",
        "end_line"
    ]

    total_ccn = 0
    total_nloc = 0
    total_funcs = 0
    funciones = []

    with open(csv_file, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f, fieldnames=columnas)

        for row in reader:
            try:
                ccn = float(row["CCN"])
                nloc = float(row["NLOC"])

                total_ccn += ccn
                total_nloc += nloc
                total_funcs += 1

                funciones.append(
                    (
                        row["function_name"],
                        row["file"],
                        ccn,
                        nloc
                    )
                )
            except:
                pass

    funciones.sort(key=lambda x: x[2], reverse=True)

    promedio_ccn = total_ccn / total_funcs if total_funcs > 0 else 0
    promedio_nloc = total_nloc / total_funcs if total_funcs > 0 else 0

    with open(txt_file, "w", encoding="utf-8") as out:
        out.write("=====================================\n")
        out.write("RESUMEN LIZARD\n")
        out.write("=====================================\n\n")

        out.write(f"Cantidad de funciones: {total_funcs}\n")
        out.write(f"CCN total: {total_ccn:.2f}\n")
        out.write(f"CCN promedio: {promedio_ccn:.2f}\n")
        out.write(f"NLOC total: {total_nloc:.2f}\n")
        out.write(f"NLOC promedio: {promedio_nloc:.2f}\n\n")

        out.write("Funciones más complejas:\n")
        out.write("-------------------------------------\n")

        for f in funciones[:10]:
            out.write(
                f"CCN={f[2]:.0f} | "
                f"NLOC={f[3]:.0f} | "
                f"{f[0]} | "
                f"{f[1]}\n"
            )

    print(f"Resumen generado: {txt_file}")
    
    
def main():

    if len(sys.argv) != 5:
        print(
            "Uso:\n"
            "python run_lizar_cyclomatic_complexity.py "
            "<lenguaje> <path_codigo> "
            "<archivo_csv> <archivo_txt>\n\n"
            "----------------------------\n"
            "Parametro lenguaje:\n"
            "----------------------------\n"
            "java\n"
            "kotlin\n"
            "python\n"
            "cpp"
            
        )
        sys.exit(1)

    lenguaje = sys.argv[1].lower()
    path_codigo = sys.argv[2]
    archivo_csv = sys.argv[3]
    archivo_txt = sys.argv[4]

    lenguajes_validos = {
        "java": "java",
        "kotlin": "kotlin",
        "python": "python",
        "cpp": "cpp"
    }

    if lenguaje not in lenguajes_validos:
        print(
            "Lenguajes válidos: "
            "java, kotlin, python, cpp"
        )
        sys.exit(1)

    if not Path(path_codigo).exists():
        print(f"No existe: {path_codigo}")
        sys.exit(1)

    comando = [
        "lizard",
        path_codigo,
        "--languages",
        lenguajes_validos[lenguaje],
        "--csv"
    ]

    try:

        with open(archivo_csv, "w", encoding="utf-8") as salida:

            resultado = subprocess.run(
                comando,
                stdout=salida,
                stderr=subprocess.PIPE,
                text=True
            )

        if resultado.returncode != 0:
            print(resultado.stderr)
            sys.exit(1)

        print(f"CSV generado: {archivo_csv}")

        generar_resumen(
            archivo_csv,
            archivo_txt
        )

    except FileNotFoundError:
        print(
            "No se encontró Lizard. "
            "Verifique que esté instalado y en PATH."
        )
        sys.exit(1)


if __name__ == "__main__":
    main()