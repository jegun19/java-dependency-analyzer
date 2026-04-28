package dev.analysis.mcp.tools;

import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.constants.GeneralConstant;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides MCP tool implementations for dependency analysis.
 *
 * <p>This class implements the MCP tool handlers that process tool call requests
 * from clients and return dependency analysis results.</p>
 *
 * <p>Each tool method takes an exchange and arguments map, following the MCP SDK
 * convention for sync tool handlers.</p>
 */
public class DependencyAnalysisTools {

    private final DependencyGraphService graphService;

    /**
     * Creates a new DependencyAnalysisTools instance.
     *
     * @param graphService the dependency graph service to query for analysis
     */
    public DependencyAnalysisTools(DependencyGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * Gets all classes that the specified class depends on (outgoing dependencies).
     *
     * <p>This tool returns the direct dependencies of a given class by examining
     * the dependency graph edges.</p>
     *
     * @param exchange the MCP server exchange context
     * @param arguments tool arguments containing {@code class} - the fully qualified class name
     * @return the tool result containing list of dependency class names
     */
    public McpSchema.CallToolResult getDependencies(McpSyncServerExchange exchange, java.util.Map<String, Object> arguments) {
        String className = extractString(arguments, GeneralConstant.PARAM_CLASS);
        if (className == null || className.isBlank()) {
            return errorResult(GeneralConstant.ERROR_MISSING_CLASS_PARAM);
        }

        List<String> dependencies = graphService.getDependencies(className);
        List<McpSchema.Content> contents = new ArrayList<>();
        for (String dep : dependencies) {
            contents.add(new McpSchema.TextContent(dep));
        }
        return new McpSchema.CallToolResult(contents, false);
    }

    /**
     * Gets all classes that depend on the specified class (incoming dependencies).
     *
     * <p>This tool returns all classes that have the given class as a dependency,
     * effectively finding reverse dependencies.</p>
     *
     * @param exchange the MCP server exchange context
     * @param arguments tool arguments containing {@code class} - the fully qualified class name
     * @return the tool result containing list of dependent class names
     */
    public McpSchema.CallToolResult getReverseDependencies(McpSyncServerExchange exchange, java.util.Map<String, Object> arguments) {
        String className = extractString(arguments, GeneralConstant.PARAM_CLASS);
        if (className == null || className.isBlank()) {
            return errorResult(GeneralConstant.ERROR_MISSING_CLASS_PARAM);
        }

        List<String> dependencies = graphService.getReverseDependencies(className);
        List<McpSchema.Content> contents = new ArrayList<>();
        for (String dep : dependencies) {
            contents.add(new McpSchema.TextContent(dep));
        }
        return new McpSchema.CallToolResult(contents, false);
    }

    /**
     * Traces a dependency path from one class to another.
     *
     * <p>This tool finds and returns the shortest dependency path between two classes
     * if such a path exists in the dependency graph.</p>
     *
     * @param exchange the MCP server exchange context
     * @param arguments tool arguments containing {@code from} - starting class,
     *                   and {@code to} - target class
     * @return the tool result containing the dependency path or empty if no path exists
     */
    public McpSchema.CallToolResult traceDependencyChain(McpSyncServerExchange exchange, java.util.Map<String, Object> arguments) {
        String from = extractString(arguments, GeneralConstant.PARAM_FROM);
        String to = extractString(arguments, GeneralConstant.PARAM_TO);
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            return errorResult(GeneralConstant.ERROR_MISSING_FROM_TO_PARAMS);
        }

        List<McpSchema.Content> contents = new ArrayList<>();
        graphService.traceDependencyPath(from, to).ifPresent(path -> {
            for (String node : path) {
                contents.add(new McpSchema.TextContent(node));
            }
        });
        return new McpSchema.CallToolResult(contents, false);
    }

    private String extractString(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Creates an error result with the given message.
     *
     * @param message the error message
     * @return error tool result
     */
    private McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true);
    }
}