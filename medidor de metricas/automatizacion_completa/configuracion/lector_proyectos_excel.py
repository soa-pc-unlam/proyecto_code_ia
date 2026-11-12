"""Carga y valida proyectos definidos en la hoja Datos_Proyectos."""

import unicodedata

from constantes import definiciones
from modelos.modelos import Proyecto


class LectorProyectosExcel:
    """Convierte las filas de un libro de entrada abierto en objetos Proyecto."""


    CAMPOS_OBLIGATORIOS = frozenset({
        "codigo", "nombre_proyecto", "ruta_codigo", "herramienta_ia",
        "modelo_ia", "lenguaje",
    })

    @staticmethod
    def _normalizar(texto):
        texto = str(texto or "").strip().casefold()
        return "".join(
            caracter for caracter in unicodedata.normalize("NFD", texto)
            if unicodedata.category(caracter) != "Mn"
        )

    def cargar(self, libro):
        """Lee la hoja una sola vez, sin modificar el libro recibido.

        Los tres campos de dispositivo son opcionales para el perfil PC.
        Una fila completamente vacía se ignora; las filas incompletas fallan
        con un mensaje que indica el número de fila.
        """
        nombre_hoja = definiciones.HOJA_DATOS_PROYECTOS
        if nombre_hoja not in libro.sheetnames:
            raise ValueError(f"No existe la hoja '{nombre_hoja}'")

        filas = iter(libro[nombre_hoja].iter_rows(values_only=True))
        encabezados = next(filas, None)
        if encabezados is None:
            raise ValueError(f"La hoja '{nombre_hoja}' está vacía")

        columnas = {}
        for indice, encabezado in enumerate(encabezados):
            if encabezado is None or not str(encabezado).strip():
                continue
            clave = self._normalizar(encabezado)
            if clave in columnas:
                raise ValueError(f"Encabezado duplicado: '{encabezado}'")
            columnas[clave] = indice

        indices = {
            campo: columnas.get(self._normalizar(titulo))
            for campo, titulo in definiciones.HOJA_DATOS_PROYECTOS_ENTRADA.items()
        }
        for campo in self.CAMPOS_OBLIGATORIOS:
            if indices[campo] is None:
                raise ValueError(
                    f"Falta la columna '{definiciones.HOJA_DATOS_PROYECTOS_ENTRADA[campo]}' "
                    f"en la hoja '{nombre_hoja}'"
                )

        proyectos = []
        codigos = set()
        for numero_fila, fila in enumerate(filas, start=2):
            if all(valor is None or str(valor).strip() == "" for valor in fila):
                continue

            def obtener(campo):
                indice = indices[campo]
                if indice is None or indice >= len(fila):
                    return ""
                valor = fila[indice]
                return "" if valor is None else str(valor).strip()

            datos = {campo: obtener(campo) for campo in definiciones.HOJA_DATOS_PROYECTOS_ENTRADA}
            for campo in self.CAMPOS_OBLIGATORIOS:
                if not datos[campo]:
                    raise ValueError(
                        f"Fila {numero_fila}: campo obligatorio "
                        f"'{definiciones.HOJA_DATOS_PROYECTOS_ENTRADA[campo]}' vacío"
                    )
            codigo = datos["codigo"]
            if codigo in codigos:
                raise ValueError(
                    f"Fila {numero_fila}: código de proyecto duplicado: {codigo}"
                )
            codigos.add(codigo)
            proyectos.append(Proyecto(**datos))

        return proyectos
