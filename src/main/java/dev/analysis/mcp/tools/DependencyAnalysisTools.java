package dev.analysis.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analysis.mcp.constants.GeneralConstant;
import dev.analysis.mcp.context.AnalysisContext;
import dev.analysis.mcp.index.IndexOperationResult;
import dev.analysis.mcp.index.IndexStatus;
import dev.analysis.mcp.index.InMemoryIndexLifecycleService;
import dev.analysis.mcp.utils.DependencyGraphService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DependencyGraphService graphService;
    private final InMemoryIndexLifecycleService indexLifecycleService;

    /**
     * Creates a new DependencyAnalysisTools instance.
     *
     * @param graphService the dependency graph service to query for analysis
     */
    public DependencyAnalysisTools(DependencyGraphService graphService) {
        this(graphService, null);
    }

    /** Creates tool handlers backed by the explicit in-memory index lifecycle. */
    public DependencyAnalysisTools(
            DependencyGraphService graphService,
            InMemoryIndexLifecycleService indexLifecycleService) {
        this.graphService = graphService;
        this.indexLifecycleService = indexLifecycleService;
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
        if (!isIndexed()) {
            return errorResult(GeneralConstant.ERROR_PROJECT_NOT_INDEXED);
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
        if (!isIndexed()) {
            return errorResult(GeneralConstant.ERROR_PROJECT_NOT_INDEXED);
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
        if (!isIndexed()) {
            return errorResult(GeneralConstant.ERROR_PROJECT_NOT_INDEXED);
        }

        List<McpSchema.Content> contents = new ArrayList<>();
        graphService.traceDependencyPath(from, to).ifPresent(path -> {
            for (String node : path) {
                contents.add(new McpSchema.TextContent(node));
            }
        });
        return new McpSchema.CallToolResult(contents, false);
    }

    /**
     * Returns all classes discovered in the codebase.
     *
     * @param exchange the MCP server exchange context
     * @param arguments tool arguments (none required)
     * @return the tool result containing all class names
     */
    public McpSchema.CallToolResult getAllClasses(McpSyncServerExchange exchange, java.util.Map<String, Object> arguments) {
        if (!isIndexed()) {
            return errorResult(GeneralConstant.ERROR_PROJECT_NOT_INDEXED);
        }
        List<String> classes = graphService.allClasses().stream().sorted().toList();
        List<McpSchema.Content> contents = new ArrayList<>();
        for (String className : classes) {
            contents.add(new McpSchema.TextContent(className));
        }
        return new McpSchema.CallToolResult(contents, false);
    }

    /** Explicitly builds or reuses the active service's in-memory dependency graph. */
    public McpSchema.CallToolResult indexProject(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        McpSchema.CallToolResult contextError = validateActiveContext(arguments);
        if (contextError != null) {
            return contextError;
        }
        if (indexLifecycleService == null) {
            return structuredError("INTERNAL_ERROR", "Index lifecycle is not configured.");
        }

        IndexOperationResult result = indexLifecycleService.index(extractBoolean(arguments, GeneralConstant.PARAM_FORCE));
        if (!result.successful()) {
            return structuredError("INTERNAL_ERROR", "Indexing failed: " + result.status().lastError(), result.status());
        }
        return structuredSuccess(result.status(), Map.of("reused", result.reused()));
    }

    /** Returns readiness and the latest complete revision for the active service context. */
    public McpSchema.CallToolResult indexStatus(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        McpSchema.CallToolResult contextError = validateActiveContext(arguments);
        if (contextError != null) {
            return contextError;
        }
        if (indexLifecycleService == null) {
            return structuredError("INTERNAL_ERROR", "Index lifecycle is not configured.");
        }
        return structuredSuccess(indexLifecycleService.status(), Map.of());
    }

    private String extractString(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean isIndexed() {
        return indexLifecycleService == null || indexLifecycleService.status().isReady();
    }

    private McpSchema.CallToolResult validateActiveContext(Map<String, Object> arguments) {
        if (indexLifecycleService == null) {
            return null;
        }
        AnalysisContext activeContext = indexLifecycleService.context();
        String contextId = extractString(arguments, GeneralConstant.PARAM_CONTEXT_ID);
        if (contextId != null && !contextId.isBlank() && !contextId.equals(activeContext.id())) {
            return structuredError("INVALID_ARGUMENT", "contextId must identify the active service context: " + activeContext.id());
        }

        String projectPath = extractString(arguments, GeneralConstant.PARAM_PROJECT_PATH);
        if (projectPath != null && !projectPath.isBlank()) {
            try {
                if (!java.nio.file.Path.of(projectPath).toAbsolutePath().normalize().equals(activeContext.rootPath())) {
                    return structuredError("INVALID_ARGUMENT", "projectPath must match the active service root: " + activeContext.rootPath());
                }
            } catch (RuntimeException exception) {
                return structuredError("INVALID_ARGUMENT", "projectPath is not a valid local path.");
            }
        }
        return null;
    }

    private McpSchema.CallToolResult structuredSuccess(IndexStatus indexStatus, Map<String, Object> additionalData) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("indexState", indexStatus.state().name());
        data.put("revision", indexStatus.revision());
        data.put("startedAt", indexStatus.startedAt() == null ? null : indexStatus.startedAt().toString());
        data.put("completedAt", indexStatus.completedAt() == null ? null : indexStatus.completedAt().toString());
        data.put("lastError", indexStatus.lastError());
        if (indexStatus.statistics() != null) {
            data.put("fileCount", indexStatus.statistics().fileCount());
            data.put("nodeCount", indexStatus.statistics().nodeCount());
            data.put("edgeCount", indexStatus.statistics().edgeCount());
        }
        data.putAll(additionalData);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("context", indexStatus.context().id());
        response.put("scope", "service");
        response.put("project", indexStatus.context().displayName());
        response.put("data", data);
        response.put("warnings", List.of());
        response.put("truncated", false);
        return jsonResult(response, false);
    }

    private McpSchema.CallToolResult structuredError(String code, String message) {
        return structuredError(code, message, null);
    }

    private McpSchema.CallToolResult structuredError(String code, String message, IndexStatus indexStatus) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("code", code);
        response.put("message", message);
        if (indexStatus != null) {
            response.put("context", indexStatus.context().id());
            response.put("revision", indexStatus.revision());
        }
        return jsonResult(response, true);
    }

    private McpSchema.CallToolResult jsonResult(Map<String, Object> response, boolean isError) {
        try {
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(OBJECT_MAPPER.writeValueAsString(response))), isError);
        } catch (JsonProcessingException exception) {
            return errorResult("Unable to serialize MCP response.");
        }
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
