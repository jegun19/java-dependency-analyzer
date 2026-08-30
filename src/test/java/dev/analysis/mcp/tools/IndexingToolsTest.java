package dev.analysis.mcp.tools;

import dev.analysis.mcp.context.AnalysisContext;
import dev.analysis.mcp.graph.DependencyGraphBuilder;
import dev.analysis.mcp.index.InMemoryIndexLifecycleService;
import dev.analysis.mcp.utils.CodebaseScanner;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndexingToolsTest {

    private DependencyAnalysisTools tools;
    private AnalysisContext context;

    @BeforeEach
    void setUp() {
        context = AnalysisContext.service(Path.of("src/test/resources/fixtures").toAbsolutePath());
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService lifecycle = new InMemoryIndexLifecycleService(
                context, new CodebaseScanner(), new JavaDependencyParser(), new DependencyGraphBuilder(), graphService);
        tools = new DependencyAnalysisTools(graphService, lifecycle);
    }

    @Test
    void queryBeforeIndexReturnsActionableError() {
        McpSchema.CallToolResult result = tools.getDependencies(null, Map.of("class", "com.example.service.OrderService"));

        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("Run index_project first"));
    }

    @Test
    void statusAndIndexReturnStructuredJsonAndEnableLegacyQueries() {
        McpSchema.CallToolResult before = tools.indexStatus(null, Map.of());
        assertFalse(before.isError());
        assertTrue(before.content().get(0).toString().contains("UNINDEXED"));

        McpSchema.CallToolResult indexed = tools.indexProject(null, Map.of("force", true));
        assertFalse(indexed.isError());
        assertTrue(indexed.content().get(0).toString().contains("READY"));
        assertTrue(indexed.content().get(0).toString().contains("\"reused\":false"));

        McpSchema.CallToolResult query = tools.getDependencies(null, Map.of("class", "com.example.service.OrderService"));
        assertFalse(query.isError());
        assertFalse(query.content().isEmpty());
    }

    @Test
    void syncProjectReturnsErrorWhenSyncLifecycleNotConfigured() {
        McpSchema.CallToolResult result = tools.syncProject(null, Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("Sync lifecycle is not configured"));
    }

    @Test
    void lifecycleToolsRejectAnotherContextOrProjectPath() {
        assertTrue(tools.indexStatus(null, Map.of("contextId", "other-context")).isError());
        assertTrue(tools.indexProject(null, Map.of("projectPath", context.rootPath().resolve("other").toString())).isError());
        assertTrue(tools.syncProject(null, Map.of("contextId", "other-context")).isError());
        assertTrue(tools.syncProject(null, Map.of("projectPath", context.rootPath().resolve("other").toString())).isError());
    }

    @Test
    void toolSpecificationsExposeExplicitIndexLifecycleTools() {
        assertTrue(ToolSpecifications.create(tools).stream().anyMatch(specification ->
                specification.tool().name().equals("index_project")));
        assertTrue(ToolSpecifications.create(tools).stream().anyMatch(specification ->
                specification.tool().name().equals("index_status")));
        assertTrue(ToolSpecifications.create(tools).stream().anyMatch(specification ->
                specification.tool().name().equals("sync_project")));
    }
}
