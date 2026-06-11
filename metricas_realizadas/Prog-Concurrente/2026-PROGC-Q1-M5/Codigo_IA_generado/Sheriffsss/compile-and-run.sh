#!/usr/bin/env bash
set -euo pipefail

COMPILE_ONLY=0
MAIN_CLASS="SheriffsssPackage.Main"

while [[ $# -gt 0 ]]; do
	case "$1" in
		-c|--compile-only) COMPILE_ONLY=1; shift ;;
		-m|--main) MAIN_CLASS="$2"; shift 2 ;;
		-h|--help)
			echo "Uso: $0 [-c|--compile-only] [-m|--main <clase>]"
			exit 0 ;;
		*) echo "Opcion desconocida: $1" >&2; exit 1 ;;
	esac
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$PROJECT_ROOT/src"
RESOURCES_DIR="$PROJECT_ROOT/resources"
OUT_DIR="$PROJECT_ROOT/out"

[[ -f "$SOURCE_DIR/SheriffsssPackage/Main.java" ]] || { echo "No se encontro src/SheriffsssPackage/Main.java" >&2; exit 1; }
[[ -d "$RESOURCES_DIR" ]] || { echo "No se encontro la carpeta resources" >&2; exit 1; }

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

SOURCES_FILE="$(mktemp)"
trap 'rm -f "$SOURCES_FILE"' EXIT
find "$SOURCE_DIR" -name "*.java" > "$SOURCES_FILE"
[[ -s "$SOURCES_FILE" ]] || { echo "No se encontraron fuentes Java en src" >&2; exit 1; }

javac -encoding UTF-8 -d "$OUT_DIR" @"$SOURCES_FILE"

if [[ "$COMPILE_ONLY" -eq 1 ]]; then
	echo "Compilacion OK: $OUT_DIR"
	exit 0
fi

cd "$PROJECT_ROOT"
java -cp "$OUT_DIR:$RESOURCES_DIR" "$MAIN_CLASS"
