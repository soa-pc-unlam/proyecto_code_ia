package ar.edu.unlam.reparabarrio.planificacion;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrdenNaturalIdTest {
    private final OrdenNaturalId comparador = new OrdenNaturalId();
    private final List<String> mezcla = List.of("T10", "T2", "T02", "TEC-12", "TEC-3", "AUX", "T", "t2", "2", "02", "0", "00", "A", "A0", "A00", "A1B2", "A1B10", "T999999999999999999999", "T1000000000000000000000", "T٢", "T-2", "T-10");
    @Test void ejemplosYClaveGlobal() {
        assertEquals(List.of("AUX", "T", "T02", "T2", "T10", "TEC-3", "TEC-12", "t2"),
                List.of("t2", "TEC-12", "T10", "T2", "AUX", "TEC-3", "T02", "T").stream().sorted(comparador).toList());
        assertTrue(comparador.compare("T999999999999999999999", "T1000000000000000000000") < 0);
        assertTrue(comparador.compare("A", "A0") < 0);
        assertTrue(comparador.compare("T2", "T٢") < 0); // Sólo ASCII constituye sufijo decimal.
        assertTrue(comparador.compare("T" + "9".repeat(1000), "T1" + "0".repeat(1000)) < 0);
    }
    @Test void totalidadAntisimetriaIgualdadYTransitividad() {
        for (var a : mezcla) for (var b : mezcla) {
            int ab = comparador.compare(a, b);
            assertEquals(-Integer.signum(ab), Integer.signum(comparador.compare(b, a)));
            assertEquals(a.equals(b), ab == 0);
            for (var c : mezcla) if (ab <= 0 && comparador.compare(b, c) <= 0)
                assertTrue(comparador.compare(a, c) <= 0, () -> a + ", " + b + ", " + c);
        }
        var invertida = new ArrayList<>(mezcla); Collections.reverse(invertida);
        assertEquals(mezcla.stream().sorted(comparador).toList(), invertida.stream().sorted(comparador).toList());
    }
}
