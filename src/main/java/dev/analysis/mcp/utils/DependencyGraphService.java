package dev.analysis.mcp.utils;

import dev.analysis.mcp.constants.GeneralConstant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;

/**
 * Service for querying and analyzing the dependency graph.
 *
 * <p>This service provides operations on the dependency graph including:</p>
 *
 * <ul>
 *   <li>Finding direct dependencies of a class</li>
 *   <li>Finding reverse dependencies (classes that depend on a given class)</li>
 *   <li>Tracing dependency paths between classes</li>
 *   <li>Detecting circular dependencies</li>
 * </ul>
 *
 * <p>The graph is set once during initialization and accessed in a thread-safe manner.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Build and set the graph
 * DependencyGraphService service = new DependencyGraphService();
 * service.setGraph(dependencyGraph);
 *
 * // Query dependencies
 * List<String> deps = service.getDependencies("com.example.Service");
 *
 * // Query reverse dependencies
 * List<String> usedBy = service.getReverseDependencies("com.example.Repository");
 *
 * // Trace dependency path
 * Optional<List<String>> path = service.traceDependencyPath("com.example.Controller", "com.example.Database");
 * }</pre>
 *
 * <h2>Graph Structure Example:</h2>
 * <p>Given this dependency graph:</p>
 * <pre>
 *     Controller ---&gt; Service ---&gt; Repository ---&gt; Database
 *         |              |
 *         +--------------+---&gt; Utility
 * </pre>
 * <p>Queries would return:</p>
 * <ul>
 *   <li>{@code getDependencies("Controller")} → ["Service", "Utility"]</li>
 *   <li>{@code getReverseDependencies("Service")} → ["Controller"]</li>
 *   <li>{@code traceDependencyPath("Controller", "Database")} → ["Controller", "Service", "Repository", "Database"]</li>
 * </ul>
 */
public class DependencyGraphService {

    private volatile Graph<String, DefaultEdge> graph;

    /**
     * Sets the dependency graph for this service.
     *
     * <p>This method should be called once before performing any queries.
     * The graph is stored as a volatile field for thread-safe access.</p>
     *
     * @param graph the dependency graph to use for queries (from JGraphT)
     */
    public void setGraph(Graph<String, DefaultEdge> graph) {
        this.graph = graph;
    }

    /**
     * Gets all classes that the specified class depends on (outgoing edges).
     *
     * <p>This method returns classes that are direct dependencies of the given class.
     * The results are sorted alphabetically and deduplicated.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Given: Controller depends on [Service, Utility]
     * List<String> deps = service.getDependencies("com.example.Controller");
     * // Returns: ["com.example.Service", "com.example.Utility"]
     * }</pre>
     *
     * @param className the fully qualified class name (e.g., "com.example.MyClass")
     * @return a sorted list of class names that the given class depends on,
     *         or an empty list if the class is not in the graph
     */
    public List<String> getDependencies(String className) {
        Graph<String, DefaultEdge> g = requireGraph();
        if (!g.containsVertex(className)) {
            return List.of();
        }
        return g.outgoingEdgesOf(className).stream()
            .map(g::getEdgeTarget)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Gets all classes that depend on the specified class (incoming edges).
     *
     * <p>This method returns all classes that have the given class as a dependency,
     * effectively finding reverse dependencies. Results are sorted alphabetically.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Given: [Controller, Service] both depend on Utility
     * List<String> usedBy = service.getReverseDependencies("com.example.Utility");
     * // Returns: ["com.example.Controller", "com.example.Service"]
     * }</pre>
     *
     * @param className the fully qualified class name
     * @return a sorted list of class names that depend on the given class,
     *         or an empty list if the class is not in the graph
     */
    public List<String> getReverseDependencies(String className) {
        Graph<String, DefaultEdge> g = requireGraph();
        if (!g.containsVertex(className)) {
            return List.of();
        }
        return g.incomingEdgesOf(className).stream()
            .map(g::getEdgeSource)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Traces the shortest dependency path from one class to another.
     *
     * <p>This method uses Dijkstra's algorithm to find the shortest path between
     * two classes. If no path exists, an empty Optional is returned.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Given: Controller -&gt; Service -&gt; Repository -&gt; Database
     * Optional<List<String>> path = service.traceDependencyPath("Controller", "Database");
     * // Returns: Optional.of(["Controller", "Service", "Repository", "Database"])
     *
     * // No path exists
     * Optional<List<String>> noPath = service.traceDependencyPath("Controller", "Unrelated");
     * // Returns: Optional.empty()
     * }</pre>
     *
     * @param from the starting class name (fully qualified)
     * @param to the target class name (fully qualified)
     * @return an optional containing the path as a list of class names if a path exists,
     *         or empty if no path exists or either class is not in the graph
     */
    public Optional<List<String>> traceDependencyPath(String from, String to) {
        Graph<String, DefaultEdge> g = requireGraph();
        if (!g.containsVertex(from) || !g.containsVertex(to)) {
            return Optional.empty();
        }

        GraphPath<String, DefaultEdge> path = new DijkstraShortestPath<>(g).getPath(from, to);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.of(Collections.unmodifiableList(new ArrayList<>(path.getVertexList())));
    }

    /**
     * Detects all classes involved in circular dependencies.
     *
     * <p>This method uses a cycle detector to find all classes that are part of
     * at least one circular dependency in the graph.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Given: A -&gt; B -&gt; C -&gt; A (cycle)
     * Set<String> cycles = service.detectCycles();
     * // Returns: ["A", "B", "C"]
     * }</pre>
     *
     * @return a set of class names that are part of dependency cycles
     */
    public Set<String> detectCycles() {
        Graph<String, DefaultEdge> g = requireGraph();
        return new CycleDetector<>(g).findCycles();
    }

    /**
     * Checks if the graph contains the specified class.
     *
     * <p>This is a convenience method to check class existence before performing
     * other queries to avoid unnecessary processing.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@-code
     * if (service.hasClass("com.example.MyClass")) {
     *     List<String> deps = service.getDependencies("com.example.MyClass");
     * }
     * }</pre>
     *
     * @param className the fully qualified class name
     * @return true if the class exists in the graph, false otherwise
     */
    public boolean hasClass(String className) {
        return requireGraph().containsVertex(className);
    }

    /**
     * Gets all classes in the dependency graph.
     *
     * <p>This returns the complete set of vertices in the graph, representing
     * all classes that have been discovered in the codebase.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Set<String> allClasses = service.allClasses();
     * System.out.println("Total classes: " + allClasses.size());
     * }</pre>
     *
     * @return a set of all class names in the graph
     */
    public Set<String> allClasses() {
        return requireGraph().vertexSet();
    }

    /**
     * Ensures the graph is initialized before use.
     *
     * @return the initialized graph
     * @throws IllegalStateException if the graph has not been set
     */
    private Graph<String, DefaultEdge> requireGraph() {
        Graph<String, DefaultEdge> g = graph;
        if (g == null) {
            throw new IllegalStateException(GeneralConstant.ERROR_GRAPH_NOT_INITIALIZED);
        }
        return g;
    }
}