#!/system/bin/sh

PACKAGE=$1
CPU_COLUMN=$2

limpiar_pantalla() {
    clear
}

mostrar_uso() {
    echo "Uso: sh memoria_app.sh <nombre_paquete> <numero_columna_cpu>"
    echo "Ejemplo: sh memoria_app.sh com.example.sensores 9"
    echo ""
    echo "Para saber la columna de %CPU, ejecutar primero:"
    echo "top"
}

validar_parametros() {
    if [ -z "$PACKAGE" ] || [ -z "$CPU_COLUMN" ]; then
        mostrar_uso
        exit 1
    fi
}

obtener_info_dispositivo() {
    MODEL=$(getprop ro.product.model)
    ANDROID=$(getprop ro.build.version.release)
	CORE_AMOUNT=$(grep -c processor /proc/cpuinfo)

    MEM_TOTAL_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    MEM_TOTAL_MB=$(awk -v mem="$MEM_TOTAL_KB" 'BEGIN {printf "%.2f", mem/1024}')
}

obtener_memoria_app() {
    PSS_KB=$(dumpsys meminfo "$PACKAGE" 2>/dev/null | grep "TOTAL PSS:" | awk '{print $3}')

    if [ -z "$PSS_KB" ]; then
        echo "No se pudo obtener información para el paquete: $PACKAGE"
        exit 1
    fi

    PSS_MB=$(awk -v pss="$PSS_KB" 'BEGIN {printf "%.2f", pss/1024}')
    RAM_PERCENT=$(awk -v pss="$PSS_KB" -v mem="$MEM_TOTAL_KB" 'BEGIN {printf "%.4f", (pss/mem)*100}')
}

obtener_cpu_promedio() {

     CPU_AVG=$(top -d 1 -n 10 | grep "$PACKAGE" | awk -v col="$CPU_COLUMN" '
    {
        sum += $col
        n++
    }
    END {
        if (n > 0)
            printf "%.2f", sum/n
        else
            printf "0.00"
    }')

}

mostrar_resultado() {
    limpiar_pantalla

    echo "========================================"
    echo "Información del dispositivo"
    echo "========================================"
    echo "Modelo: $MODEL"
    echo "Android: $ANDROID"
    echo "Cant. Nucleos: $CORE_AMOUNT"
	echo "RAM Total: $MEM_TOTAL_MB MB"
	

    echo ""
    echo "========================================"
    echo "Información de la aplicación"
    echo "========================================"
    echo "Paquete: $PACKAGE"
    echo "PSS: $PSS_MB MB"
    echo "Porcentaje RAM utilizada: $RAM_PERCENT %"
    echo "CPU promedio: $CPU_AVG %"

    echo ""
    echo "========================================"
}

main() {
    limpiar_pantalla
    validar_parametros

    obtener_info_dispositivo
    obtener_memoria_app

    echo "Realizando muestreo, por favor espere..."

    obtener_cpu_promedio
    mostrar_resultado
}

main