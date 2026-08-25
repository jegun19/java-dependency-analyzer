package dev.analysis.mcp.tools;

import dev.analysis.mcp.utils.DependencyGraphService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyAnalysisToolsTest {

    @Mock
    private DependencyGraphService graphService;

    private DependencyAnalysisTools tools;

    @BeforeEach
    void setUp() {
        tools = new DependencyAnalysisTools(graphService);
    }

    @Test
    void getDependencies_returnsCorrectResult() {
        when(graphService.getDependencies("com.example.Service")).thenReturn(List.of("com.example.Repository", "com.example.Model"));

        McpSchema.CallToolResult result = tools.getDependencies(null, Map.of("class", "com.example.Service"));

        assertFalse(result.isError());
        assertEquals(2, result.content().size());
        assertTrue(result.content().stream().anyMatch(c -> c.toString().contains("com.example.Repository")));
        assertTrue(result.content().stream().anyMatch(c -> c.toString().contains("com.example.Model")));
    }

    @Test
    void getDependencies_returnsEmptyListForUnknownClass() {
        when(graphService.getDependencies("UNKNOWN")).thenReturn(List.of());

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
        when(graphService.getReverseDependencies("com.example.Repository")).thenReturn(List.of("com.example.Service"));

        McpSchema.CallToolResult result = tools.getReverseDependencies(null, Map.of("class", "com.example.Repository"));

        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertTrue(result.content().get(0).toString().contains("com.example.Service"));
    }

    @Test
    void getReverseDependencies_returnsErrorForMissingParam() {
        McpSchema.CallToolResult result = tools.getReverseDependencies(null, Map.of());

        assertTrue(result.isError());
    }

    @Test
    void traceDependencyChain_returnsPathWhenExists() {
        List<String> path = List.of("com.example.Controller", "com.example.Service", "com.example.Repository");
        when(graphService.traceDependencyPath("com.example.Controller", "com.example.Repository")).thenReturn(Optional.of(path));

        McpSchema.CallToolResult result = tools.traceDependencyChain(null, Map.of("from", "com.example.Controller", "to", "com.example.Repository"));

        assertFalse(result.isError());
        assertEquals(3, result.content().size());
    }

    @Test
    void traceDependencyChain_returnsEmptyWhenNoPath() {
        when(graphService.traceDependencyPath("A", "B")).thenReturn(Optional.empty());

        McpSchema.CallToolResult result = tools.traceDependencyChain(null, Map.of("from", "A", "to", "B"));

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
        when(graphService.allClasses()).thenReturn(Set.of("com.example.B", "com.example.A", "com.example.C"));

        McpSchema.CallToolResult result = tools.getAllClasses(null, Map.of());

        assertFalse(result.isError());
        assertEquals(3, result.content().size());
    }

    @Test
    void getAllClasses_returnsEmptySetWhenNoClasses() {
        when(graphService.allClasses()).thenReturn(Set.of());

        McpSchema.CallToolResult result = tools.getAllClasses(null, Map.of());

        assertFalse(result.isError());
        assertTrue(result.content().isEmpty());
    }
}
