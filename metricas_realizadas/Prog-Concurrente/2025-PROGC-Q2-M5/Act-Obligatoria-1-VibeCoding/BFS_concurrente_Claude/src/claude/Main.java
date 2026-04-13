package claude;

/**
 * Clase principal con lotes de prueba para verificar el algoritmo BFS concurrente
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║   ALGORITMO BFS CONCURRENTE - JAVA 17              ║");
        System.out.println("║   Búsqueda en Anchura con Paralelismo             ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");
        
        // Ejecutar ambos lotes de prueba
        ejecutarLotePrueba1();
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        ejecutarLotePrueba2();
    }
    
    /**
     * LOTE DE PRUEBA 1: Grafo en forma de árbol
     * Estructura:
     *         0
     *       / | \
     *      1  2  3
     *     / \    |
     *    4   5   6
     *        |
     *        7
     */
    private static void ejecutarLotePrueba1() {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║        LOTE DE PRUEBA 1: Grafo tipo Árbol         ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        // Crear grafo con 8 vértices
        Grafo grafo1 = new Grafo(8);
        
        // Construir el árbol
        grafo1.agregarArista(0, 1);
        grafo1.agregarArista(0, 2);
        grafo1.agregarArista(0, 3);
        grafo1.agregarArista(1, 4);
        grafo1.agregarArista(1, 5);
        grafo1.agregarArista(3, 6);
        grafo1.agregarArista(5, 7);
        
        // Mostrar estructura
        grafo1.imprimirGrafo();
        
        // Ejecutar BFS con 3 threads
        BFSConcurrente bfs1 = new BFSConcurrente(grafo1, 3);
        var resultado1 = bfs1.ejecutarBFS(0);
        
        // Mostrar resultado
        bfs1.imprimirResultado(resultado1);
        
        // Verificar resultado esperado
        System.out.println("Resultado esperado por niveles:");
        System.out.println("Nivel 0: [0]");
        System.out.println("Nivel 1: [1, 2, 3]");
        System.out.println("Nivel 2: [4, 5, 6]");
        System.out.println("Nivel 3: [7]");
        System.out.println("\nNOTA: El orden dentro de cada nivel puede variar debido a la concurrencia,");
        System.out.println("pero todos los nodos de un nivel deben aparecer antes que los del siguiente.\n");
        
        // Validar resultado
        validarBFS(resultado1, grafo1, 0);
    }
    
    /**
     * LOTE DE PRUEBA 2: Grafo complejo con ciclos
     * Estructura:
     *         0 ←→ 1
     *        ↙ ↘   ↓
     *       2 ←→ 3 ← 4
     *       ↓    ↓
     *       5 → 6 ← 7
     *           ↓
     *           8
     */
    private static void ejecutarLotePrueba2() {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║      LOTE DE PRUEBA 2: Grafo con Ciclos           ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        // Crear grafo con 9 vértices
        Grafo grafo2 = new Grafo(9);
        
        // Construir grafo complejo con ciclos
        grafo2.agregarAristaNoDirigida(0, 1);  // 0 ←→ 1
        grafo2.agregarArista(0, 2);            // 0 → 2
        grafo2.agregarArista(0, 3);            // 0 → 3
        grafo2.agregarAristaNoDirigida(2, 3);  // 2 ←→ 3
        grafo2.agregarArista(1, 4);            // 1 → 4
        grafo2.agregarArista(4, 3);            // 4 → 3
        grafo2.agregarArista(2, 5);            // 2 → 5
        grafo2.agregarArista(3, 6);            // 3 → 6
        grafo2.agregarArista(5, 6);            // 5 → 6
        grafo2.agregarArista(7, 6);            // 7 → 6
        grafo2.agregarArista(6, 8);            // 6 → 8
        
        // Mostrar estructura
        grafo2.imprimirGrafo();
        
        // Ejecutar BFS con 4 threads
        BFSConcurrente bfs2 = new BFSConcurrente(grafo2, 4);
        var resultado2 = bfs2.ejecutarBFS(0);
        
        // Mostrar resultado
        bfs2.imprimirResultado(resultado2);
        
        // Verificar resultado esperado
        System.out.println("Resultado esperado por niveles desde vértice 0:");
        System.out.println("Nivel 0: [0]");
        System.out.println("Nivel 1: [1, 2, 3]");
        System.out.println("Nivel 2: [4, 5, 6]");
        System.out.println("Nivel 3: [8]");
        System.out.println("Nodo 7 no es alcanzable desde 0");
        System.out.println("\nNOTA: El orden dentro de cada nivel puede variar debido a la concurrencia.\n");
        
        // Validar resultado
        validarBFS(resultado2, grafo2, 0);
        
        // Prueba adicional: BFS desde el vértice 7
        System.out.println("\n--- Prueba adicional: BFS desde vértice 7 ---");
        BFSConcurrente bfs3 = new BFSConcurrente(grafo2, 4);
        var resultado3 = bfs3.ejecutarBFS(7);
        bfs3.imprimirResultado(resultado3);
        System.out.println("Desde vértice 7 solo son alcanzables: [7, 6, 8]");
        validarBFS(resultado3, grafo2, 7);
    }
    
    /**
     * Valida que el recorrido BFS sea correcto:
     * - Todos los nodos visitados son únicos
     * - Se respeta el orden por niveles
     * @param resultado Lista con el recorrido
     * @param grafo Grafo recorrido
     * @param inicio Vértice inicial
     */
    private static void validarBFS(java.util.List<Integer> resultado, Grafo grafo, int inicio) {
        System.out.println("--- Validación del Resultado ---");
        
        // Verificar que no hay duplicados
        java.util.Set<Integer> unicos = new java.util.HashSet<>(resultado);
        if (unicos.size() == resultado.size()) {
            System.out.println("✓ No hay nodos duplicados");
        } else {
            System.out.println("✗ ERROR: Hay nodos duplicados");
        }
        
        // Verificar que el primer nodo es el inicial
        if (!resultado.isEmpty() && resultado.get(0) == inicio) {
            System.out.println("✓ El primer nodo es el vértice inicial (" + inicio + ")");
        } else {
            System.out.println("✗ ERROR: El primer nodo no es el vértice inicial");
        }
        
        // Verificar orden por niveles usando BFS secuencial de referencia
        java.util.Map<Integer, Integer> distancias = calcularDistanciasBFS(grafo, inicio);
        boolean ordenCorrecto = true;
        
        for (int i = 0; i < resultado.size() - 1; i++) {
            int nodoActual = resultado.get(i);
            int nodoSiguiente = resultado.get(i + 1);
            
            int distActual = distancias.getOrDefault(nodoActual, Integer.MAX_VALUE);
            int distSiguiente = distancias.getOrDefault(nodoSiguiente, Integer.MAX_VALUE);
            
            // El siguiente nodo debe estar en el mismo nivel o en el siguiente
            if (distSiguiente > distActual + 1) {
                ordenCorrecto = false;
                break;
            }
        }
        
        if (ordenCorrecto) {
            System.out.println("✓ Se respeta el orden por niveles (característica de BFS)");
        } else {
            System.out.println("✗ ERROR: No se respeta el orden por niveles");
        }
        
        System.out.println("Total de nodos alcanzados: " + resultado.size());
        System.out.println("--------------------------------\n");
    }
    
    /**
     * Calcula las distancias desde un vértice inicial usando BFS secuencial
     * (método de referencia para validación)
     */
    private static java.util.Map<Integer, Integer> calcularDistanciasBFS(Grafo grafo, int inicio) {
        java.util.Map<Integer, Integer> distancias = new java.util.HashMap<>();
        java.util.Queue<Integer> cola = new java.util.LinkedList<>();
        java.util.Set<Integer> visitados = new java.util.HashSet<>();
        
        cola.offer(inicio);
        visitados.add(inicio);
        distancias.put(inicio, 0);
        
        while (!cola.isEmpty()) {
            int nodo = cola.poll();
            int distActual = distancias.get(nodo);
            
            for (int vecino : grafo.obtenerVecinos(nodo)) {
                if (!visitados.contains(vecino)) {
                    visitados.add(vecino);
                    distancias.put(vecino, distActual + 1);
                    cola.add(vecino);
                }
            }
        }
        
        return distancias;
    }
}