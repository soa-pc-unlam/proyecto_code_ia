package ar.edu.unlam.reparabarrio.simulacion;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GeneradorCargaTest {
    static ConfiguracionEscenario config(int n,int w,List<Integer> pesos,ConfiguracionEscenario.Llegadas l,long min,long max,int fallos) {
        return new ConfiguracionEscenario(20261015,n,w,ConfiguracionEscenario.inventario(2,2,1,1,0,true),pesos,l,min,max,0,5,fallos);
    }
    @Test void entradaCompletaCienYNulaReproducible() {
        for(var l:ConfiguracionEscenario.Llegadas.values())for(int n:new int[]{0,100}) {
            var c=config(n,8,Collections.nCopies(8,1),l,2,l==ConfiguracionEscenario.Llegadas.ALEATORIAS?7:2,1000);
            var a=new GeneradorCarga().generar(c);assertEquals(a,new GeneradorCarga().generar(c));assertEquals(n,a.ordenes().size());
            long anterior=0;for(var o:a.ordenes()){assertTrue(o.llegadaMs()>=anterior);anterior=o.llegadaMs();assertTrue(o.duracionMs()<=5);}
            if(n>0)assertEquals(0,a.ordenes().getFirst().llegadaMs());
            if(n==100)assertEquals(8,a.ordenes().stream().map(CargaEscenario.EntradaOrden::requisitos).distinct().count());
        }
    }
    @Test void extremosDeMezclaYFallos() {
        for(int mascara=0;mascara<8;mascara++)for(int tasa:new int[]{0,10000}) {
            var pesos=new ArrayList<>(Collections.nCopies(8,0));pesos.set(mascara,1);
            var carga=new GeneradorCarga().generar(config(100,1,pesos,ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,tasa));
            for(var o:carga.ordenes()){assertEquals(Integer.bitCount(mascara),o.requisitos().size());assertEquals(tasa==10000,o.falla());}
        }
    }
    @Test void invalidaAntesDeAbrirContexto() {
        assertThrows(IllegalArgumentException.class,()->config(-1,1,Collections.nCopies(8,1),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,0));
        assertThrows(IllegalArgumentException.class,()->config(1,0,Collections.nCopies(8,1),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,0));
        assertThrows(IllegalArgumentException.class,()->config(1,1,Collections.nCopies(8,0),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,0));
        assertThrows(IllegalArgumentException.class,()->config(100,1,Collections.nCopies(8,1),ConfiguracionEscenario.Llegadas.PERIODICAS,Long.MAX_VALUE,Long.MAX_VALUE,0));
        assertThrows(IllegalArgumentException.class,()->config(1,1,Collections.nCopies(8,1),ConfiguracionEscenario.Llegadas.ALEATORIAS,5,2,10001));
    }
}
