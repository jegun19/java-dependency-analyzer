package dev.analysis.mcp.graph;

import dev.analysis.mcp.utils.JavaDependencyParser;
import dev.analysis.mcp.utils.JavaDependencyParser.DependencyEdge;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Builds a directed graph from dependency edges.
 *
 * <p>This class converts a set of dependency edges, as extracted by the
 * {@link JavaDependencyParser}, into a JGraphT
 * directed graph structure suitable for graph algorithms.</p>
 *
 * <p>The resulting graph has class names as vertices and dependency relationships
 * as directed edges.</p>
 */
public class DependencyGraphBuilder {

    /**
     * Builds a directed graph from the given dependency edges.
     *
     * @param edges the set of dependency edges to convert
     * @return a directed graph with class names as vertices
     */
    public Graph<String, DefaultEdge> build(Set<DependencyEdge> edges) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (DependencyEdge edge : edges) {
            graph.addVertex(edge.from());
            graph.addVertex(edge.to());
            graph.addEdge(edge.from(), edge.to());
        }

        return graph;
    }
}