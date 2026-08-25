package dev.analysis.mcp.utils;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphServiceTest {

    private DependencyGraphService service;
    private Graph<String, DefaultEdge> graph;

    @BeforeEach
    void setUp() {
        service = new DependencyGraphService();
        graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Build a simple graph:
        // A -> B -> C
        // A -> C
        // D -> C
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");
        graph.addEdge("D", "C");

        service.setGraph(graph);
    }

    @Test
    void getDependencies_returnsOutgoingEdges() {
        var deps = service.getDependencies("A");
        assertEquals(2, deps.size());
        assertTrue(deps.contains("B"));
        assertTrue(deps.contains("C"));
    }

    @Test
    void getDependencies_returnsEmptyForLeafNode() {
        var deps = service.getDependencies("C");
        assertTrue(deps.isEmpty());
    }

    @Test
    void getDependencies_returnsEmptyForUnknownClass() {
        var deps = service.getDependencies("UNKNOWN");
        assertTrue(deps.isEmpty());
    }

    @Test
    void getDependencies_returnsSortedList() {
        var deps = service.getDependencies("A");
        assertEquals("B", deps.get(0));
        assertEquals("C", deps.get(1));
    }

    @Test
    void getReverseDependencies_returnsIncomingEdges() {
        var reverseDeps = service.getReverseDependencies("C");
        assertEquals(3, reverseDeps.size());
        assertTrue(reverseDeps.contains("A"));
        assertTrue(reverseDeps.contains("B"));
        assertTrue(reverseDeps.contains("D"));
    }

    @Test
    void getReverseDependencies_returnsEmptyForRootNode() {
        var reverseDeps = service.getReverseDependencies("A");
        assertTrue(reverseDeps.isEmpty());
    }

    @Test
    void getReverseDependencies_returnsEmptyForUnknownClass() {
        var reverseDeps = service.getReverseDependencies("UNKNOWN");
        assertTrue(reverseDeps.isEmpty());
    }

    @Test
    void traceDependencyPath_returnsPathWhenExists() {
        Optional<java.util.List<String>> path = service.traceDependencyPath("B", "C");
        assertTrue(path.isPresent());
        assertEquals(2, path.get().size());
        assertEquals("B", path.get().get(0));
        assertEquals("C", path.get().get(1));
    }

    @Test
    void traceDependencyPath_returnsShortestPath() {
        Optional<java.util.List<String>> path = service.traceDependencyPath("A", "C");
        assertTrue(path.isPresent());
        assertEquals(2, path.get().size());
        assertEquals("A", path.get().get(0));
        assertEquals("C", path.get().get(1));
    }

    @Test
    void traceDependencyPath_returnsEmptyWhenNoPath() {
        Optional<java.util.List<String>> path = service.traceDependencyPath("D", "A");
        assertFalse(path.isPresent());
    }

    @Test
    void traceDependencyPath_returnsEmptyForUnknownClass() {
        Optional<java.util.List<String>> path = service.traceDependencyPath("UNKNOWN", "A");
        assertFalse(path.isPresent());
    }

    @Test
    void allClasses_returnsAllVertices() {
        Set<String> classes = service.allClasses();
        assertEquals(4, classes.size());
        assertTrue(classes.contains("A"));
        assertTrue(classes.contains("B"));
        assertTrue(classes.contains("C"));
        assertTrue(classes.contains("D"));
    }

    @Test
    void throwsExceptionWhenGraphNotInitialized() {
        DependencyGraphService emptyService = new DependencyGraphService();
        assertThrows(IllegalStateException.class, () -> emptyService.getDependencies("A"));
        assertThrows(IllegalStateException.class, () -> emptyService.getReverseDependencies("A"));
        assertThrows(IllegalStateException.class, () -> emptyService.allClasses());
    }
}
