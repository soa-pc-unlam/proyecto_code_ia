package ar.edu.unlam.reparabarrio.metricas;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

/** Derivación pura. Las tablas de intervalos son proyecciones, nunca inventario. */
public final class CalculadorEstadisticas {
    public EstadisticasEscenario calcular(HechosConfirmados.Captura c,long t0,long fin,boolean vigente) {
        var estados=new EnumMap<EstadoOrden,Long>(EstadoOrden.class);
        for(var e:EstadoOrden.values())estados.put(e,c.taller().dominio().ordenes().stream().filter(o->o.estado()==e).count());
        long procesadas=estados.get(EstadoOrden.FINALIZADA)+estados.get(EstadoOrden.FALLIDA);
        String incompleta=!vigente?"Resultado técnico: captura histórica; vigentes N/D":c.incompleta();
        var esperas=new LinkedHashMap<String,Double>();var utilizaciones=new LinkedHashMap<Recurso.Clave,EstadisticasEscenario.Utilizacion>();
        Double promedio=null,duracion=null;
        try {
            if(incompleta!=null)throw new IllegalStateException(incompleta);
            if(fin-t0<0 || c.nanos()-fin<0)throw new IllegalStateException("Período temporal inválido");
            if(!c.taller().historial().equals(c.hechos().stream().map(HechosConfirmados.Hecho::evento).toList()))throw new IllegalStateException("Historial y hechos no coinciden");
            var registros=new HashMap<String,Long>();var habilitadoDesde=new HashMap<Recurso.Clave,Long>();
            var ocupadoDesde=new HashMap<Recurso.Clave,Long>();var hab=new HashMap<Recurso.Clave,Long>();var ocu=new HashMap<Recurso.Clave,Long>();
            long anterior=0;boolean primero=true;long revision=0;
            for(var h:c.hechos()) {
                if(!h.contexto().equals(c.taller().dominio().contexto()) || h.revision()!=++revision || !primero && h.nanos()-anterior<0 || c.nanos()-h.nanos()<0)
                    throw new IllegalStateException("Orden/contexto/revisión de evidencia inválidos");
                primero=false;anterior=h.nanos();var e=h.evento();long t=h.nanos();
                switch(e.accion()) {
                    case "REGISTRO_ORDEN" -> registros.put(e.ordenId(),t);
                    case "REGISTRO_RECURSO" -> {
                        for(var r:e.recursos()){hab.put(r,0L);ocu.put(r,0L);if(Boolean.TRUE.equals(h.descriptor().habilitado()))habilitadoDesde.put(r,t);}
                    }
                    case "HABILITACION" -> {for(var r:e.recursos()){if(habilitadoDesde.putIfAbsent(r,t)!=null)throw new IllegalStateException("Habilitación duplicada");}}
                    case "INHABILITACION" -> {for(var r:e.recursos()){var desde=habilitadoDesde.remove(r);if(desde==null || ocupadoDesde.containsKey(r))throw new IllegalStateException("Inhabilitación incoherente");hab.merge(r,intervalo(desde,t,t0,fin),Math::addExact);}}
                    case "INICIO" -> {
                        var registro=registros.get(e.ordenId());if(registro==null || t-registro<0 || esperas.putIfAbsent(e.ordenId(),(t-registro)/1_000_000.0)!=null)throw new IllegalStateException("Inicio sin registro o repetido");
                        for(var r:e.recursos()){if(!habilitadoDesde.containsKey(r) || ocupadoDesde.putIfAbsent(r,t)!=null)throw new IllegalStateException("Ocupación incompatible");}
                    }
                    case "FINALIZADA","FALLIDA" -> {for(var r:e.recursos()){var desde=ocupadoDesde.remove(r);if(desde==null)throw new IllegalStateException("Cierre sin inicio");ocu.merge(r,intervalo(desde,t,t0,fin),Math::addExact);}}
                    case "CANCELADA" -> { }
                    default -> throw new IllegalStateException("Acción sin evidencia temporal soportada");
                }
            }
            if(revision!=c.taller().revision())throw new IllegalStateException("Revisión final sin evidencia");
            habilitadoDesde.forEach((r,desde)->hab.merge(r,intervalo(desde,fin,t0,fin),Math::addExact));
            ocupadoDesde.forEach((r,desde)->ocu.merge(r,intervalo(desde,fin,t0,fin),Math::addExact));
            for(var r:c.taller().dominio().recursos()) {
                Long h=hab.get(r.clave()),o=ocu.get(r.clave());
                if(h==null || o==null || o<0 || o>h)throw new IllegalStateException("Intervalos incompletos o utilización inválida");
                utilizaciones.put(r.clave(),new EstadisticasEscenario.Utilizacion(o,h,h==0?null:100.0*o/h));
            }
            promedio=esperas.isEmpty()?null:esperas.values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            duracion=(fin-t0)/1_000_000_000.0;
        } catch(RuntimeException e){incompleta=e.getMessage();esperas.clear();utilizaciones.clear();}
        return new EstadisticasEscenario(c.taller().revision(),t0,fin,estados,procesadas,promedio,duracion,esperas,utilizaciones,incompleta);
    }
    private static long intervalo(long desde,long hasta,long inicio,long fin) {
        // Comparaciones relativas al mismo origen: System.nanoTime puede ser negativo.
        return Math.max(0,Math.min(hasta-inicio,fin-inicio)-Math.max(0,desde-inicio));
    }
}
