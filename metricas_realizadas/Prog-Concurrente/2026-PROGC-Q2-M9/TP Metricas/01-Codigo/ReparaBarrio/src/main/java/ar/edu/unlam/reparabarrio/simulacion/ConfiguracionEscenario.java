package ar.edu.unlam.reparabarrio.simulacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

/** H6-G1: unidades explícitas, pesos de máscaras M=1/S=2/D=4, tiempos en ms. */
public record ConfiguracionEscenario(long semilla, int ordenes, int trabajadores, List<Recurso> recursos,
        List<Integer> pesos, Llegadas llegadas, long intervaloMinMs, long intervaloMaxMs,
        long duracionMinMs, long duracionMaxMs, int fallosPorDiezMil) {
    public enum Llegadas { SIMULTANEAS, PERIODICAS, ALEATORIAS }
    public ConfiguracionEscenario {
        recursos=List.copyOf(recursos);pesos=List.copyOf(pesos);Objects.requireNonNull(llegadas);
        if(ordenes<0 || ordenes>Integer.MAX_VALUE-8 || trabajadores<1 || trabajadores>Integer.MAX_VALUE-8)
            throw new IllegalArgumentException("N no negativo y W positivo representables requeridos");
        if(pesos.size()!=8 || pesos.stream().anyMatch(p -> p<0) || pesos.stream().mapToLong(Integer::longValue).sum()==0)
            throw new IllegalArgumentException("Ocho pesos no negativos con suma positiva requeridos");
        if(intervaloMinMs<0 || intervaloMaxMs<intervaloMinMs || duracionMinMs<0 || duracionMaxMs<duracionMinMs
                || fallosPorDiezMil<0 || fallosPorDiezMil>10000)
            throw new IllegalArgumentException("Rangos temporales o tasa de fallos inválidos");
        if(llegadas==Llegadas.PERIODICAS && intervaloMinMs!=intervaloMaxMs)
            throw new IllegalArgumentException("Llegadas periódicas requieren intervalo fijo");
        var claves=new HashSet<Recurso.Clave>();
        for(var r:recursos)if(!claves.add(r.clave()))throw new IllegalArgumentException("Recurso duplicado");
        try {
            Math.multiplyExact(duracionMaxMs,1_000_000L);
            Math.multiplyExact(intervaloMaxMs,1_000_000L);
            long ultimo=Math.multiplyExact(Math.max(0,ordenes-1L),intervaloMaxMs);
            Math.multiplyExact(Math.addExact(ultimo,duracionMaxMs),1_000_000L);
        } catch(ArithmeticException e){throw new IllegalArgumentException("Tiempo no representable en nanosegundos",e);}
    }
    public static ConfiguracionEscenario referencia(boolean limitado) {
        return new ConfiguracionEscenario(20261015,100,4,inventario(limitado?2:4,limitado?2:4,
                limitado?1:4,limitado?1:4,limitado?0:4,true),Collections.nCopies(8,1),
                Llegadas.PERIODICAS,10,10,200,400,1000);
    }
    public static List<Recurso> inventario(int tecnicos,int puestos,int multimetros,int soldadores,int taladros,boolean habilitados) {
        int[] cantidades={tecnicos,puestos,multimetros,soldadores,taladros};
        long total=0;for(int n:cantidades){if(n<0)throw new IllegalArgumentException("Cantidad negativa");total+=n;}
        if(total>Integer.MAX_VALUE-8)throw new IllegalArgumentException("Inventario no representable");
        var resultado=new ArrayList<Recurso>();String[] prefijos={"T","P","M","S","D"};
        TipoHerramienta[] tipos={null,null,TipoHerramienta.MULTIMETRO,TipoHerramienta.SOLDADOR,TipoHerramienta.TALADRO};
        for(int k=0;k<5;k++)for(int i=1;i<=cantidades[k];i++)resultado.add(new Recurso(new Recurso.Clave(
                k==0?Recurso.Categoria.TECNICO:k==1?Recurso.Categoria.PUESTO:Recurso.Categoria.HERRAMIENTA,prefijos[k]+i),tipos[k],habilitados));
        return List.copyOf(resultado);
    }
}
