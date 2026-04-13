package claude;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clase que representa un grafo dirigido usando listas de adyacencia.
 * Utiliza estructuras thread-safe para soportar operaciones concurrentes.
 */
public class Grafo {
    private final int numVertices;
    private final Map<Integer, List<Integer>> listaAdyacencia;
    
    /**
     * Constructor del grafo
     * @param numVertices Número de vértices del grafo
     */
    public Grafo(int numVertices) {
        this.numVertices = numVertices;
        // Usamos ConcurrentHashMap para operaciones thread-safe
        this.listaAdyacencia = new ConcurrentHashMap<>();
        
        // Inicializamos las listas de adyacencia
        for (int i = 0; i < numVertices; i++) {
            listaAdyacencia.put(i, Collections.synchronizedList(new ArrayList<>()));
        }
    }
    
    /**
     * Agrega una arista dirigida del vértice origen al destino
     * @param origen Vértice de origen
     * @param destino Vértice de destino
     */
    public void agregarArista(int origen, int destino) {
        if (origen >= 0 && origen < numVertices && destino >= 0 && destino < numVertices) {
            listaAdyacencia.get(origen).add(destino);
        } else {
            throw new IllegalArgumentException("Vértices fuera de rango");
        }
    }
    
    /**
     * Agrega una arista no dirigida (bidireccional)
     * @param v1 Primer vértice
     * @param v2 Segundo vértice
     */
    public void agregarAristaNoDirigida(int v1, int v2) {
        agregarArista(v1, v2);
        agregarArista(v2, v1);
    }
    
    /**
     * Obtiene los vecinos de un vértice
     * @param vertice Vértice del cual obtener vecinos
     * @return Lista de vértices adyacentes
     */
    public List<Integer> obtenerVecinos(int vertice) {
        return new ArrayList<>(listaAdyacencia.get(vertice));
    }
    
    /**
     * Obtiene el número de vértices del grafo
     * @return Número de vértices
     */
    public int getNumVertices() {
        return numVertices;
    }
    
    /**
     * Imprime la representación del grafo
     */
    public void imprimirGrafo() {
        System.out.println("\n=== Estructura del Grafo ===");
        for (int i = 0; i < numVertices; i++) {
            System.out.print("Vértice " + i + " -> ");
            List<Integer> vecinos = listaAdyacencia.get(i);
            System.out.println(vecinos);
        }
        System.out.println("============================\n");
    }
}