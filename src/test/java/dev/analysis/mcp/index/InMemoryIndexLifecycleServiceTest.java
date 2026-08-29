package dev.analysis.mcp.index;

import dev.analysis.mcp.context.AnalysisContext;
import dev.analysis.mcp.graph.DependencyGraphBuilder;
import dev.analysis.mcp.utils.CodebaseScanner;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIndexLifecycleServiceTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures").toAbsolutePath();

    @Test
    void startsUnindexed_thenBuildsAndReusesCompleteSnapshot() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = lifecycle(new CodebaseScanner(), graphService);

        assertEquals(IndexState.UNINDEXED, lifecycle.status().state());

        IndexOperationResult initial = lifecycle.index(false);
        assertTrue(initial.successful());
        assertFalse(initial.reused());
        assertEquals(IndexState.READY, initial.status().state());
        assertEquals(1, initial.status().revision());
        assertTrue(initial.status().statistics().fileCount() > 0);
        assertFalse(graphService.allClasses().isEmpty());

        IndexOperationResult reused = lifecycle.index(false);
        assertTrue(reused.successful());
        assertTrue(reused.reused());
        assertEquals(1, reused.status().revision());

        IndexOperationResult forced = lifecycle.index(true);
        assertTrue(forced.successful());
        assertFalse(forced.reused());
        assertEquals(2, forced.status().revision());
    }

    @Test
    void failedRefreshPreservesPreviousCompleteGraph() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = lifecycle(new SucceedsThenFailsScanner(), graphService);
        assertTrue(lifecycle.index(false).successful());
        int nodeCount = graphService.allClasses().size();

        IndexOperationResult refresh = lifecycle.index(true);
        assertFalse(refresh.successful());
        assertEquals(IndexState.READY, refresh.status().state());
        assertEquals(1, refresh.status().revision());
        assertEquals(nodeCount, graphService.allClasses().size());
    }

    @Test
    void firstIndexFailureTransitionsToFailedState() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = lifecycle(new AlwaysFailsScanner(), graphService);

        IndexOperationResult result = lifecycle.index(false);

        assertFalse(result.successful());
        assertEquals(IndexState.FAILED, result.status().state());
        assertEquals(0, result.status().revision());
        assertNotNull(result.status().lastError());
    }

    @Test
    void parserFailureOnFirstAttemptTransitionsToFailedState() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = new InMemoryIndexLifecycleService(
                AnalysisContext.service(FIXTURES),
                new CodebaseScanner(),
                new FailingParser(),
                new DependencyGraphBuilder(),
                graphService);

        IndexOperationResult result = lifecycle.index(false);

        assertFalse(result.successful());
        assertEquals(IndexState.FAILED, result.status().state());
        assertEquals(0, result.status().revision());
        assertNotNull(result.status().lastError());
    }

    @Test
    void builderFailureOnFirstAttemptTransitionsToFailedState() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = new InMemoryIndexLifecycleService(
                AnalysisContext.service(FIXTURES),
                new CodebaseScanner(),
                new JavaDependencyParser(),
                new FailingBuilder(),
                graphService);

        IndexOperationResult result = lifecycle.index(false);

        assertFalse(result.successful());
        assertEquals(IndexState.FAILED, result.status().state());
        assertEquals(0, result.status().revision());
        assertNotNull(result.status().lastError());
    }

    @Test
    void exceptionWithNullMessageRecordsSimpleClassName() {
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = new InMemoryIndexLifecycleService(
                AnalysisContext.service(FIXTURES),
                new CodebaseScanner(),
                new JavaDependencyParser(),
                new NullMessageFailingBuilder(),
                graphService);

        IndexOperationResult result = lifecycle.index(false);

        assertFalse(result.successful());
        assertEquals(IndexState.FAILED, result.status().state());
        assertEquals("NullPointerException", result.status().lastError());
    }

    private InMemoryIndexLifecycleService lifecycle(CodebaseScanner scanner, DependencyGraphService graphService) {
        return new InMemoryIndexLifecycleService(
                AnalysisContext.service(FIXTURES), scanner, new JavaDependencyParser(), new DependencyGraphBuilder(), graphService);
    }

    private static final class SucceedsThenFailsScanner extends CodebaseScanner {
        private boolean firstCall = true;

        @Override
        public List<Path> scan(Path repoRoot) throws IOException {
            if (firstCall) {
                firstCall = false;
                return super.scan(repoRoot);
            }
            throw new IOException("deliberate scan failure");
        }
    }

    private static final class AlwaysFailsScanner extends CodebaseScanner {
        @Override
        public List<Path> scan(Path repoRoot) throws IOException {
            throw new IOException("scan always fails");
        }
    }

    private static final class FailingParser extends JavaDependencyParser {
        @Override
        public Set<JavaDependencyParser.DependencyEdge> parse(Path repoRoot, List<Path> javaFiles) {
            throw new RuntimeException("parser failure");
        }
    }

    private static final class FailingBuilder extends DependencyGraphBuilder {
        @Override
        public org.jgrapht.Graph<String, org.jgrapht.graph.DefaultEdge> build(Set<JavaDependencyParser.DependencyEdge> edges) {
            throw new RuntimeException("builder failure");
        }
    }

    private static final class NullMessageFailingBuilder extends DependencyGraphBuilder {
        @Override
        public org.jgrapht.Graph<String, org.jgrapht.graph.DefaultEdge> build(Set<JavaDependencyParser.DependencyEdge> edges) {
            throw new NullPointerException();
        }
    }
}
