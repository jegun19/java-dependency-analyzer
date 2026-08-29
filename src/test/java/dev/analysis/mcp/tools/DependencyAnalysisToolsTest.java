package dev.analysis.mcp.tools;

import dev.analysis.mcp.graph.DependencyGraphBuilder;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser.DependencyEdge;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyAnalysisToolsTest {

    private DependencyAnalysisTools tools;

    @BeforeEach
    void setUp() {
        DependencyGraphService graphService = new DependencyGraphService();
        graphService.setGraph(new DependencyGraphBuilder().build(Set.of(
                new DependencyEdge("com.example.Service", "com.example.Repository"),
                new DependencyEdge("com.example.Service", "com.example.Model"),
                new DependencyEdge("com.example.Controller", "com.example.Service"))));
        tools = new DependencyAnalysisTools(graphService);
    }

    @Test
    void getDependencies_returnsCorrectResult() {
        McpSchema.CallToolResult result = tools.getDependencies(null, Map.of("class", "com.example.Service"));

        assertFalse(result.isError());
        assertEquals(2, result.content().size());
        assertTrue(result.content().stream().anyMatch(c -> c.toString().contains("com.example.Repository")));
        assertTrue(result.content().stream().anyMatch(c -> c.toString().contains("com.example.Model")));
    }

    @Test
    void getDependencies_returnsEmptyListForUnknownClass() {
        McpSchema.CallToolResult result = tools.getDependencies(null, Map.of("class", "UNKNOWN"));

        assertFalse(result.isError());
        assertTrue(result.content().isEmpty());
    }

    @Test
    void getDependencies_returnsErrorForMissingParam() {
        McpSchema.CallToolResult result = tools.getDependencies(null, Map.of());

        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("Missing required parameter"));
    }

    @Test
    void getReverseDependencies_returnsCorrectResult() {
        McpSchema.CallToolResult result = tools.getReverseDependencies(null, Map.of("class", "com.example.Service"));

        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertTrue(result.content().get(0).toString().contains("com.example.Controller"));
    }

    @Test
    void getReverseDependencies_returnsErrorForMissingParam() {
        assertTrue(tools.getReverseDependencies(null, Map.of()).isError());
    }

    @Test
    void traceDependencyChain_returnsPathWhenExists() {
        McpSchema.CallToolResult result = tools.traceDependencyChain(null,
                Map.of("from", "com.example.Controller", "to", "com.example.Repository"));

        assertFalse(result.isError());
        assertEquals(3, result.content().size());
    }

    @Test
    void traceDependencyChain_returnsEmptyWhenNoPath() {
        McpSchema.CallToolResult result = tools.traceDependencyChain(null, Map.of("from", "com.example.Model", "to", "com.example.Repository"));

        assertFalse(result.isError());
        assertTrue(result.content().isEmpty());
    }

    @Test
    void traceDependencyChain_returnsErrorForMissingParams() {
        McpSchema.CallToolResult result = tools.traceDependencyChain(null, Map.of());

        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("Missing required parameters"));
    }

    @Test
    void getAllClasses_returnsAllClasses() {
        McpSchema.CallToolResult result = tools.getAllClasses(null, Map.of());

        assertFalse(result.isError());
        assertEquals(4, result.content().size());
    }
}
