package com.example.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConcurrentBreadthFirstSearchTest {

    @Test
    void bfsOnLinearGraphVisitsNodesInOrder() {
        Graph<Integer> graph = new Graph<>();
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(3, 4);

        try (ConcurrentBreadthFirstSearch<Integer> bfs = new ConcurrentBreadthFirstSearch<>()) {
            List<Integer> order = bfs.traverse(graph, 1);
            assertEquals(List.of(1, 2, 3, 4), order);
        }
    }

    @Test
    void bfsOnGraphWithBranchingKeepsLevelOrder() {
        Graph<String> graph = new Graph<>();
        graph.addUndirectedEdge("A", "B");
        graph.addUndirectedEdge("A", "C");
        graph.addUndirectedEdge("B", "D");
        graph.addUndirectedEdge("B", "E");
        graph.addUndirectedEdge("C", "F");
        graph.addUndirectedEdge("C", "G");

        try (ConcurrentBreadthFirstSearch<String> bfs = new ConcurrentBreadthFirstSearch<>(4)) {
            List<String> order = bfs.traverse(graph, "A");
            assertEquals(List.of("A", "B", "C", "D", "E", "F", "G"), order);
        }
    }

    @Test
    void bfsThrowsWhenStartNodeMissing() {
        Graph<Integer> graph = new Graph<>();
        graph.addNode(1);

        try (ConcurrentBreadthFirstSearch<Integer> bfs = new ConcurrentBreadthFirstSearch<>()) {
            assertThrows(IllegalArgumentException.class, () -> bfs.traverse(graph, 99));
        }
    }
}
