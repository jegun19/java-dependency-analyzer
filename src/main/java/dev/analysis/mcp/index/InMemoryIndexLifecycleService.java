package dev.analysis.mcp.index;

import dev.analysis.mcp.context.AnalysisContext;
import dev.analysis.mcp.graph.DependencyGraphBuilder;
import dev.analysis.mcp.utils.CodebaseScanner;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Coordinates explicit full indexing for one service context.
 *
 * <p>It deliberately has no persistence or background work. Its atomic publication point is
 * retained so a later persistent or asynchronous coordinator can preserve the same query
 * contract: readers observe a complete previous or next graph, never a partial graph.</p>
 *
 * <p>The service serializes status and indexing operations. A failed re-index leaves the last
 * successfully published graph available for queries and records the failure in its status.</p>
 */
public class InMemoryIndexLifecycleService {

    private final AnalysisContext context;
    private final CodebaseScanner scanner;
    private final JavaDependencyParser parser;
    private final DependencyGraphBuilder graphBuilder;
    private final DependencyGraphService graphService;

    private IndexState state = IndexState.UNINDEXED;
    private long revision;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastSyncAt;
    private IndexStatistics statistics;
    private String lastError;

    /**
     * Creates an index coordinator using the supplied indexing collaborators.
     *
     * @param context context whose source root is indexed
     * @param scanner source-file scanner
     * @param parser Java dependency parser
     * @param graphBuilder dependency graph builder
     * @param graphService graph publication and query service
     * @throws NullPointerException if any argument is {@code null}
     */
    public InMemoryIndexLifecycleService(
            AnalysisContext context,
            CodebaseScanner scanner,
            JavaDependencyParser parser,
            DependencyGraphBuilder graphBuilder,
            DependencyGraphService graphService) {
        this.context = Objects.requireNonNull(context, "context");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.graphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder");
        this.graphService = Objects.requireNonNull(graphService, "graphService");
    }

    /** Returns the context owned by this lifecycle service. */
    public AnalysisContext context() {
        return context;
    }

    /** Returns an immutable snapshot of the current lifecycle state. */
    public synchronized IndexStatus status() {
        return currentStatus();
    }

    /** Runs a complete synchronous index unless a ready snapshot may be reused. */
    public synchronized IndexOperationResult index(boolean force) {
        if (state == IndexState.READY && !force) {
            return new IndexOperationResult(currentStatus(), true, true);
        }

        state = IndexState.INDEXING;
        startedAt = Instant.now();
        lastError = null;
        try {
            List<java.nio.file.Path> javaFiles = scanner.scan(context.rootPath());
            Set<JavaDependencyParser.DependencyEdge> dependencies = parser.parse(context.rootPath(), javaFiles);
            Graph<String, DefaultEdge> graph = graphBuilder.build(dependencies);

            graphService.setGraph(graph);
            revision++;
            statistics = new IndexStatistics(javaFiles.size(), graph.vertexSet().size(), graph.edgeSet().size());
            lastSyncAt = Instant.now();
            completedAt = Instant.now();
            state = IndexState.READY;
            return new IndexOperationResult(currentStatus(), false, true);
        } catch (IOException | RuntimeException exception) {
            completedAt = Instant.now();
            lastError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            if (revision == 0) {
                state = IndexState.FAILED;
            } else {
                state = IndexState.READY;
            }
            return new IndexOperationResult(currentStatus(), false, false);
        }
    }

    private IndexStatus currentStatus() {
        return new IndexStatus(
                context, state, revision, startedAt, completedAt, lastSyncAt, statistics, lastError);
    }

}
