package claude;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación concurrente del algoritmo BFS (Breadth-First Search).
 * Utiliza múltiples threads para explorar el grafo en paralelo,
 * manteniendo el orden por niveles característico de BFS.
 */
public class BFSConcurrente {
    private final Grafo grafo;
    private final int numThreads;
    private final Set<Integer> visitados;
    private final Queue<Integer> cola;
    private final List<Integer> ordenVisita;
    private final AtomicInteger nodosEnProceso;
    private final Object lockImpresion;
    
    /**
     * Constructor del BFS concurrente
     * @param grafo Grafo a recorrer
     * @param numThreads Número de threads a utilizar
     */
    public BFSConcurrente(Grafo grafo, int numThreads) {
        this.grafo = grafo;
        this.numThreads = numThreads;
        // Estructuras thread-safe
        this.visitados = ConcurrentHashMap.newKeySet();
        this.cola = new ConcurrentLinkedQueue<>();
        this.ordenVisita = Collections.synchronizedList(new ArrayList<>());
        this.nodosEnProceso = new AtomicInteger(0);
        this.lockImpresion = new Object();
    }
    
    /**
     * Ejecuta BFS desde un vértice inicial
     * @param inicio Vértice de inicio
     * @return Lista con el orden de visita de los vértices
     */
    public List<Integer> ejecutarBFS(int inicio) {
        if (inicio < 0 || inicio >= grafo.getNumVertices()) {
            throw new IllegalArgumentException("Vértice inicial fuera de rango");
        }
        
        // Limpiamos estructuras anteriores
        visitados.clear();
        cola.clear();
        ordenVisita.clear();
        nodosEnProceso.set(0);
        
        // Marcamos el nodo inicial como visitado y lo agregamos a la cola
        visitados.add(inicio);
        cola.offer(inicio);
        ordenVisita.add(inicio);
        nodosEnProceso.incrementAndGet();
        
        System.out.println("=== Iniciando BFS Concurrente ===");
        System.out.println("Vértice inicial: " + inicio);
        System.out.println("Número de threads: " + numThreads);
        System.out.println("==================================\n");
        
        // Creamos el pool de threads
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Lanzamos los threads trabajadores
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> trabajadorBFS(threadId));
        }
        
        // Esperamos a que termine el procesamiento
        executor.shutdown();
        try {
            // Esperamos hasta 60 segundos para que terminen todos los threads
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\n=== BFS Completado ===");
        System.out.println("Nodos visitados: " + ordenVisita.size());
        System.out.println("======================\n");
        
        return new ArrayList<>(ordenVisita);
    }
    
    /**
     * Método ejecutado por cada thread trabajador
     * @param threadId Identificador del thread
     */
    private void trabajadorBFS(int threadId) {
        while (true) {
            // Intentamos obtener un nodo de la cola
            Integer nodoActual = cola.poll();
            
            if (nodoActual == null) {
                // No hay nodos en la cola
                // Si no hay nodos en proceso, terminamos
                if (nodosEnProceso.get() == 0) {
                    break;
                }
                // Si hay nodos en proceso, esperamos un poco
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            
            // Procesamos el nodo
            procesarNodo(nodoActual, threadId);
            
            // Decrementamos el contador de nodos en proceso
            nodosEnProceso.decrementAndGet();
        }
    }
    
    /**
     * Procesa un nodo del grafo
     * @param nodo Nodo a procesar
     * @param threadId ID del thread que procesa
     */
    private void procesarNodo(int nodo, int threadId) {
        synchronized (lockImpresion) {
            System.out.println("[Thread-" + threadId + "] Procesando vértice: " + nodo);
        }
        
        // Obtenemos los vecinos del nodo actual
        List<Integer> vecinos = grafo.obtenerVecinos(nodo);
        
        // Procesamos cada vecino
        for (Integer vecino : vecinos) {
            // Intentamos marcar el vecino como visitado de forma atómica
            if (visitados.add(vecino)) {
                // Si es la primera vez que lo visitamos
                synchronized (lockImpresion) {
                    System.out.println("[Thread-" + threadId + "] Descubriendo vecino: " + vecino);
                }
                
                // Agregamos a la lista de orden de visita (thread-safe)
                ordenVisita.add(vecino);
                
                // Incrementamos contador y agregamos a la cola
                nodosEnProceso.incrementAndGet();
                cola.offer(vecino);
            }
        }
        
        // Pequeña pausa para visualizar mejor la concurrencia
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Imprime el resultado del recorrido BFS
     * @param resultado Lista con el orden de visita
     */
    public void imprimirResultado(List<Integer> resultado) {
        System.out.println("=== Recorrido BFS ===");
        System.out.print("Orden de visita: ");
        for (int i = 0; i < resultado.size(); i++) {
            System.out.print(resultado.get(i));
            if (i < resultado.size() - 1) {
                System.out.print(" -> ");
            }
        }
        System.out.println("\n=====================\n");
    }
}