package dev.analysis.mcp.graph;
import dev.analysis.mcp.utils.JavaDependencyParser.DependencyEdge;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphBuilderTest {

    private DependencyGraphBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DependencyGraphBuilder();
    }

    @Test
    void build_createsGraphFromEdges() {
        Set<DependencyEdge> edges = Set.of(
            new DependencyEdge("A", "B"),
            new DependencyEdge("A", "C"),
            new DependencyEdge("B", "C")
        );

        Graph<String, DefaultEdge> graph = builder.build(edges);

        assertEquals(3, graph.vertexSet().size());
        assertTrue(graph.containsVertex("A"));
        assertTrue(graph.containsVertex("B"));
        assertTrue(graph.containsVertex("C"));

        assertTrue(graph.containsEdge("A", "B"));
        assertTrue(graph.containsEdge("A", "C"));
        assertTrue(graph.containsEdge("B", "C"));
    }

    @Test
    void build_createsEmptyGraphFromEmptyEdges() {
        Graph<String, DefaultEdge> graph = builder.build(Set.of());

        assertTrue(graph.vertexSet().isEmpty());
        assertTrue(graph.edgeSet().isEmpty());
    }

    @Test
    void build_handlesSelfReferencingEdges() {
        DependencyEdge selfRef = new DependencyEdge("A", "A");
        DependencyEdge normalEdge = new DependencyEdge("A", "B");
        java.util.Set<DependencyEdge> edges = new java.util.HashSet<>();
        edges.add(selfRef);
        edges.add(normalEdge);

        Graph<String, DefaultEdge> graph = builder.build(edges);

        assertEquals(2, graph.vertexSet().size());
        assertTrue(graph.containsVertex("A"));
        assertTrue(graph.containsVertex("B"));
        assertTrue(graph.containsEdge("A", "B"));
        assertTrue(graph.containsEdge("A", "A"));
    }

    @Test
    void build_handlesDuplicateEdges() {
        java.util.Set<DependencyEdge> edges = new java.util.HashSet<>();
        edges.add(new DependencyEdge("A", "B"));
        edges.add(new DependencyEdge("A", "B"));

        Graph<String, DefaultEdge> graph = builder.build(edges);

        assertEquals(2, graph.vertexSet().size());
        assertEquals(1, graph.edgeSet().size());
    }

    @Test
    void build_handlesComplexGraph() {
        Set<DependencyEdge> edges = Set.of(
            new DependencyEdge("Controller", "Service"),
            new DependencyEdge("Controller", "Utility"),
            new DependencyEdge("Service", "Repository"),
            new DependencyEdge("Repository", "Database")
        );

        Graph<String, DefaultEdge> graph = builder.build(edges);

        assertEquals(5, graph.vertexSet().size());
        assertEquals(4, graph.edgeSet().size());
        assertTrue(graph.containsEdge("Controller", "Service"));
        assertTrue(graph.containsEdge("Controller", "Utility"));
        assertTrue(graph.containsEdge("Service", "Repository"));
        assertTrue(graph.containsEdge("Repository", "Database"));
    }
}
