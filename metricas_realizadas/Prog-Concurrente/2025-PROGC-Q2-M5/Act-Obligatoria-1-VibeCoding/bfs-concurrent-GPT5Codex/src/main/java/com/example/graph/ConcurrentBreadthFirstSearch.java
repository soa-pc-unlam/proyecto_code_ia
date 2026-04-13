package com.example.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementación concurrente del algoritmo de búsqueda en anchura (BFS).
 * Permite procesar los nodos de cada nivel en paralelo manteniendo el orden de recorrido propio del BFS.
 *
 * @param <T> tipo de valor almacenado en los nodos del grafo
 */
public class ConcurrentBreadthFirstSearch<T> implements AutoCloseable {

    private final ExecutorService executor;
    private final boolean ownsExecutor;

    /**
     * Crea un BFS concurrente con un número de hilos igual al número de núcleos disponibles o 2, el mayor.
     */
    public ConcurrentBreadthFirstSearch() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Crea un BFS concurrente usando un pool de hilos fijo con el paralelismo indicado.
     *
     * @param parallelism cantidad de hilos a utilizar (debe ser mayor o igual a 1)
     */
    public ConcurrentBreadthFirstSearch(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("El paralelismo debe ser al menos 1");
        }
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.ownsExecutor = true;
    }

    /**
     * Crea un BFS concurrente que utilizará el {@link ExecutorService} indicado.
     * La instancia NO cierra el executor al finalizar.
     *
     * @param executor servicio executor a utilizar
     */
    public ConcurrentBreadthFirstSearch(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "El executor no puede ser nulo");
        this.ownsExecutor = false;
    }

    /**
     * Ejecuta el recorrido BFS desde el nodo inicial dado.
     *
     * @param graph grafo a recorrer
     * @param start nodo inicial
     * @return lista inmodificable con el orden de visita de los nodos
     */
    public List<T> traverse(Graph<T> graph, T start) {
        Objects.requireNonNull(graph, "El grafo no puede ser nulo");
        Objects.requireNonNull(start, "El nodo inicial no puede ser nulo");

        if (!graph.containsNode(start)) {
            throw new IllegalArgumentException("El nodo inicial no existe en el grafo");
        }

        Set<T> visited = ConcurrentHashMap.newKeySet();
        List<T> traversalOrder = new ArrayList<>();

        List<T> frontier = new ArrayList<>();
        frontier.add(start);
        visited.add(start);

        while (!frontier.isEmpty()) {
            traversalOrder.addAll(frontier);

            List<CompletableFuture<List<T>>> futures = new ArrayList<>(frontier.size());
            for (T node : frontier) {
                futures.add(CompletableFuture.supplyAsync(() -> exploreNeighbors(graph, node, visited), executor));
            }

            List<T> nextFrontier = new ArrayList<>();
            for (CompletableFuture<List<T>> future : futures) {
                try {
                    nextFrontier.addAll(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("La ejecución del BFS fue interrumpida", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Error en la exploración de vecinos", e.getCause());
                }
            }

            frontier = nextFrontier;
        }

        return Collections.unmodifiableList(traversalOrder);
    }

    private List<T> exploreNeighbors(Graph<T> graph, T node, Set<T> visited) {
        List<T> neighbors = graph.getNeighbors(node);
        List<T> newlyDiscovered = new ArrayList<>();
        for (T neighbor : neighbors) {
            if (visited.add(neighbor)) {
                newlyDiscovered.add(neighbor);
            }
        }
        return newlyDiscovered;
    }

    @Override
    public void close() {
        if (!ownsExecutor) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
