"""Pruebas del flujo compartido y de la selección de entradas por plataforma."""

import unittest
from contextlib import ExitStack
from unittest.mock import patch

import main_pc
import main_esp32_android
from constantes import definiciones
from procesamiento import procesamiento_comun as comun


class ProcesamientoComunTest(unittest.TestCase):
    """Verifica rutas, opciones y liberación de recursos sin ejecutar analizadores."""

    def setUp(self):
        """Sustituye las operaciones externas del flujo por dobles de prueba."""
        self.patches = ExitStack()
        self.addCleanup(self.patches.close)
        nombres = (
            'cargar_configuracion', 'cargar_proyectos', 'crear_directorio',
            'configurar_logger', 'limitar_cpu', 'crear_o_abrir_excel_salida',
            'abrir_excel_entrada', 'gestionar_procesamiento_proyectos',
            'finalizar_libro',
        )
        self.operaciones = {
            nombre: self.patches.enter_context(patch.object(comun, nombre))
            for nombre in nombres
        }
        self.configuracion = {
            definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_ENTRADA: 'entrada_pc.xlsx',
            definiciones.TEXTO_CAMPO_ARCHIVO_EXCEL_SALIDA: 'salida_pc.xlsx',
            definiciones.TEXTO_CAMPO_CARPETA_RESULTADOS: 'resultados',
            definiciones.TEXTO_CAMPO_CARPETA_LOGS: 'logs',
        }
        self.operaciones['cargar_configuracion'].return_value = self.configuracion

    def test_pc_conserva_configuracion(self):
        """PC conserva las rutas y opciones de la configuración original."""
        main_pc.main()
        self.operaciones['cargar_configuracion'].assert_called_once_with(
            definiciones.CONFIGURACION_JSON
        )
        self.operaciones['cargar_proyectos'].assert_called_once_with(
            definiciones.DATOS_PC_PROYECTOS_JSON
        )
        self.operaciones['abrir_excel_entrada'].assert_called_once_with(
            'entrada_pc.xlsx'
        )
        self.operaciones['finalizar_libro'].assert_called_once_with(
            self.operaciones['crear_o_abrir_excel_salida'].return_value,
            'salida_pc.xlsx', incluir_graficos=True,
        )
        self.verificar_cierre_libros()

    def test_esp32_android_utiliza_sus_rutas(self):
        """ESP32 y Android usan entradas y destinos separados de PC."""
        main_esp32_android.main()
        self.operaciones['cargar_proyectos'].assert_called_once_with(
            definiciones.DATOS_ESP32_ANDROID_PROYECTOS_JSON
        )
        self.operaciones['abrir_excel_entrada'].assert_called_once_with(
            definiciones.DATOS_ESP32_ANDROID_EXCEL
        )
        self.operaciones['finalizar_libro'].assert_called_once_with(
            self.operaciones['crear_o_abrir_excel_salida'].return_value,
            definiciones.REPORTE_ESP32_ANDROID_EXCEL, incluir_graficos=True,
        )
        self.operaciones['configurar_logger'].assert_called_once_with(
            definiciones.CARPETA_LOGS_ESP32_ANDROID
        )
        self.operaciones['crear_directorio'].assert_any_call(
            definiciones.CARPETA_RESULTADOS_ESP32_ANDROID
        )
        self.operaciones['gestionar_procesamiento_proyectos'].assert_called_once_with(
            self.operaciones['cargar_proyectos'].return_value,
            self.configuracion,
            self.operaciones['crear_o_abrir_excel_salida'].return_value,
            self.operaciones['abrir_excel_entrada'].return_value,
            self.operaciones['configurar_logger'].return_value,
        )
        self.verificar_cierre_libros()

    def test_opciones_cpu_y_graficos(self):
        """El llamador puede elegir el límite de CPU y omitir gráficos."""
        comun.ejecutar_analisis(
            'config.json', 'proyectos.json', porcentaje_cpu=0.5,
            incluir_graficos=False,
        )
        self.operaciones['limitar_cpu'].assert_called_once_with(
            0.5, self.operaciones['configurar_logger'].return_value
        )
        self.assertFalse(
            self.operaciones['finalizar_libro'].call_args.kwargs['incluir_graficos']
        )

    def test_error_procesamiento_cierra_libros(self):
        """Una excepción al procesar cierra ambos libros y registra el error."""
        self.operaciones['gestionar_procesamiento_proyectos'].side_effect = (
            RuntimeError('fallo de procesamiento')
        )
        main_pc.main()
        self.operaciones['finalizar_libro'].assert_not_called()
        self.operaciones['configurar_logger'].return_value.error.assert_called_once()
        self.verificar_cierre_libros()

    def test_error_apertura_entrada_cierra_salida(self):
        """Si la entrada no abre, se libera el libro de salida ya abierto."""
        self.operaciones['abrir_excel_entrada'].side_effect = FileNotFoundError()
        main_pc.main()
        self.operaciones['crear_o_abrir_excel_salida'].return_value.close.assert_called_once()
        self.operaciones['gestionar_procesamiento_proyectos'].assert_not_called()

    def test_error_guardado_cierra_libros(self):
        """Un reporte bloqueado conserva el aviso y libera los libros."""
        self.operaciones['finalizar_libro'].side_effect = PermissionError()
        main_pc.main()
        self.operaciones['configurar_logger'].return_value.error.assert_called_once_with(
            'No se pudo guardar el informe. Cierre el archivo Excel si está abierto.'
        )
        self.verificar_cierre_libros()

    def verificar_cierre_libros(self):
        """Comprueba que ambos libros se cierran exactamente una vez."""
        for nombre in ('crear_o_abrir_excel_salida', 'abrir_excel_entrada'):
            self.operaciones[nombre].return_value.close.assert_called_once()


if __name__ == '__main__':
    unittest.main()
