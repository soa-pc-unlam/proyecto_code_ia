package com.example.graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Representa un grafo dirigido utilizando listas de adyacencia.
 * @param <T> tipo de dato almacenado en cada nodo
 */
public class Graph<T> {

    private final Map<T, List<T>> adjacency = new ConcurrentHashMap<>();

    /**
     * Agrega un nodo al grafo.
     * Si el nodo ya existe, la operación no tiene efecto.
     *
     * @param value valor del nodo
     */
    public void addNode(T value) {
        adjacency.computeIfAbsent(value, key -> new CopyOnWriteArrayList<>());
    }

    /**
     * Agrega una arista dirigida desde {@code from} hacia {@code to}.
     * Ambos nodos se crean si todavía no existen.
     *
     * @param from nodo origen
     * @param to   nodo destino
     */
    public void addEdge(T from, T to) {
        addNode(from);
        addNode(to);
        adjacency.get(from).add(to);
    }

    /**
     * Agrega una arista no dirigida entre los nodos indicados.
     *
     * @param nodeA primer nodo
     * @param nodeB segundo nodo
     */
    public void addUndirectedEdge(T nodeA, T nodeB) {
        addEdge(nodeA, nodeB);
        addEdge(nodeB, nodeA);
    }

    /**
     * Retorna los vecinos (adyacentes) de un nodo.
     * Devuelve una lista vacía si el nodo no existe o no tiene adyacentes.
     *
     * @param node nodo consultado
     * @return lista inmodificable de vecinos
     */
    public List<T> getNeighbors(T node) {
        List<T> neighbors = adjacency.get(node);
        if (neighbors == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(neighbors);
    }

    /**
     * Indica si el nodo existe en el grafo.
     *
     * @param node nodo buscado
     * @return {@code true} si existe, en caso contrario {@code false}
     */
    public boolean containsNode(T node) {
        return adjacency.containsKey(node);
    }

    /**
     * Devuelve el conjunto de nodos registrados en el grafo.
     *
     * @return conjunto inmodificable de nodos
     */
    public Set<T> getNodes() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }
}
