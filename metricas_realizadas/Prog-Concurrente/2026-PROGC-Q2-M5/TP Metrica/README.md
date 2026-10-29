# TP Métrica — Banco concurrente generado con IA

Actividad Práctica Obligatoria N°1 · Programación Concurrente · 2.º cuatrimestre 2026

## Contenido

| Archivo | Qué es |
|---|---|
| `Informe_TP_Metrica_v1.04.pdf` | Informe académico (versión de entrega) |
| `Informe_TP_Metrica_v1.04.docx` | Fuente editable del informe |
| `prompt_tecnico.md` | Prompt técnico inicial y prompts de refinamiento |
| `codigo/v1/` | Código generado por la IA en la primera versión |
| `codigo/v2/` | Código refinado (`BancoConcurrente.java`) y pruebas automáticas (`PruebasBanco.java`) |
| `medir.py` | Script de medición de CPU y memoria con psutil |
| `Plantilla-metrica_completa.xlsx` | Planilla de métricas (M1 = v1, M2 = v2) |
| `Rubrica-*_completa.docx` | Rúbricas de funcionalidad y de concurrencia completas |

## Compilar y ejecutar (Java 21)

```bash
cd codigo/v2
javac BancoConcurrente.java PruebasBanco.java
java BancoConcurrente --transferencias 300000
java BancoConcurrente --unsafe --workers 8 --transferencias 300000   # modo de control: debe dar FALLA
java PruebasBanco                                                     # 10 pruebas automáticas
```

La v1 se compila y ejecuta igual desde `codigo/v1` (no incluye pruebas automáticas ni las opciones `--queue` y `--fair`).

## Medir CPU y memoria

```bash
pip install psutil
python medir.py codigo/v1 resultados_v1.json   # fila M1 de la planilla
python medir.py codigo/v2 resultados_v2.json   # fila M2 de la planilla
```
