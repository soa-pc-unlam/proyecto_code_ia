package ar.edu.unlam.reparabarrio.simulacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

/** Consumo G1 por orden: intervalo sólo en ALEATORIAS (también i=0), máscara,
 * duración (incluso fija), fallo (incluso 0/10000). Random nunca sale del generador. */
public final class GeneradorCarga {
    public CargaEscenario generar(ConfiguracionEscenario c) {
        var random=new Random(c.semilla());var ordenes=new ArrayList<CargaEscenario.EntradaOrden>();long llegada=0;
        long suma=c.pesos().stream().mapToLong(Integer::longValue).sum();
        TipoHerramienta[] tipos={TipoHerramienta.MULTIMETRO,TipoHerramienta.SOLDADOR,TipoHerramienta.TALADRO};
        for(int i=0;i<c.ordenes();i++) {
            long intervalo=c.llegadas()==ConfiguracionEscenario.Llegadas.ALEATORIAS?
                    c.intervaloMinMs()+random.nextLong(c.intervaloMaxMs()-c.intervaloMinMs()+1):c.intervaloMinMs();
            if(i>0 && c.llegadas()!=ConfiguracionEscenario.Llegadas.SIMULTANEAS)llegada=Math.addExact(llegada,intervalo);
            long eleccion=random.nextLong(suma);int mascara=0;
            while(eleccion>=c.pesos().get(mascara))eleccion-=c.pesos().get(mascara++);
            var req=EnumSet.noneOf(TipoHerramienta.class);for(int k=0;k<3;k++)if((mascara&(1<<k))!=0)req.add(tipos[k]);
            long duracion=c.duracionMinMs()+random.nextLong(c.duracionMaxMs()-c.duracionMinMs()+1);
            boolean falla=random.nextInt(10000)<c.fallosPorDiezMil();
            ordenes.add(new CargaEscenario.EntradaOrden("O"+(i+1),"Orden ficticia "+(i+1),req,llegada,duracion,falla));
        }
        return new CargaEscenario("H6-G1",c,ordenes);
    }
}
