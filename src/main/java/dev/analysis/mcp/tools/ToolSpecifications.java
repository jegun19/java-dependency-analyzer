package dev.analysis.mcp.tools;

import dev.analysis.mcp.constants.GeneralConstant;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Builds the MCP tool catalog exposed by the dependency analyzer.
 *
 * <p>This is an application-level factory around the official MCP SDK types
 * {@link McpSchema.Tool} and {@link McpServerFeatures.SyncToolSpecification}. The SDK provides
 * the objects used to describe and register a tool, but it does not know which application
 * handler, name, description, or JSON schema belong together. This class owns that mapping in
 * one deterministic list.</p>
 *
 * <p>Tool behavior remains in {@link DependencyAnalysisTools}; this class only describes and
 * wires that behavior to MCP. Keeping registration here prevents {@code McpServerApplication}
 * from accumulating schema and handler details, so startup remains focused on context
 * resolution, lifecycle construction, transport setup, and server startup. It also gives a
 * later MCP SDK migration one application-level registration boundary to update.</p>
 *
 * <p>The factory uses the SDK's {@code SyncToolSpecification.builder()} API. Its callback adapter
 * translates the current handler signature, which receives an argument map, from the SDK
 * callback signature, which receives a {@code CallToolRequest}. Constructing the catalog is
 * side-effect free: it does not scan, parse, or index a project.</p>
 */
public final class ToolSpecifications {

    private ToolSpecifications() {}

    /**
     * Creates the complete, ordered tool catalog for one handler instance.
     *
     * @param tools handler implementation receiving calls for all registered tools
     * @return immutable MCP tool specifications ready for
     *         {@code McpServer.sync(...).tools(...)}
     * @throws NullPointerException if {@code tools} is {@code null}
     */
    public static List<McpServerFeatures.SyncToolSpecification> create(DependencyAnalysisTools tools) {
        java.util.Objects.requireNonNull(tools, "tools");
        McpSchema.JsonSchema classSchema = schema(
                Map.of(GeneralConstant.PARAM_CLASS,
                        Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING)),
                List.of(GeneralConstant.PARAM_CLASS));
        McpSchema.JsonSchema chainSchema = schema(
                Map.of(
                        GeneralConstant.PARAM_FROM, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING),
                        GeneralConstant.PARAM_TO, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING)),
                List.of(GeneralConstant.PARAM_FROM, GeneralConstant.PARAM_TO));
        McpSchema.JsonSchema noArgSchema = schema(Map.of(), List.of());
        McpSchema.JsonSchema indexSchema = schema(
                Map.of(
                        GeneralConstant.PARAM_PROJECT_PATH, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING),
                        GeneralConstant.PARAM_CONTEXT_ID, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING),
                        GeneralConstant.PARAM_FORCE, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_BOOLEAN)),
                List.of());
        McpSchema.JsonSchema statusSchema = schema(
                Map.of(
                        GeneralConstant.PARAM_PROJECT_PATH, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING),
                        GeneralConstant.PARAM_CONTEXT_ID, Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING)),
                List.of());

        return List.of(
                specification(GeneralConstant.TOOL_GET_DEPENDENCIES, GeneralConstant.TOOL_GET_DEPENDENCIES_DESC, classSchema, tools::getDependencies),
                specification(GeneralConstant.TOOL_GET_REVERSE_DEPENDENCIES, GeneralConstant.TOOL_GET_REVERSE_DEPENDENCIES_DESC, classSchema, tools::getReverseDependencies),
                specification(GeneralConstant.TOOL_TRACE_DEPENDENCY_CHAIN, GeneralConstant.TOOL_TRACE_DEPENDENCY_CHAIN_DESC, chainSchema, tools::traceDependencyChain),
                specification(GeneralConstant.TOOL_ALL_CLASSES, GeneralConstant.TOOL_ALL_CLASSES_DESC, noArgSchema, tools::getAllClasses),
                specification(GeneralConstant.TOOL_INDEX_PROJECT, GeneralConstant.TOOL_INDEX_PROJECT_DESC, indexSchema, tools::indexProject),
                specification(GeneralConstant.TOOL_INDEX_STATUS, GeneralConstant.TOOL_INDEX_STATUS_DESC, statusSchema, tools::indexStatus),
                specification(GeneralConstant.TOOL_SYNC_PROJECT, GeneralConstant.TOOL_SYNC_PROJECT_DESC, statusSchema, tools::syncProject));
    }

    /** Creates an object-shaped JSON schema for a tool's argument object. */
    private static McpSchema.JsonSchema schema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema(GeneralConstant.SCHEMA_TYPE_OBJECT, properties, required, null, null, null);
    }

    /** Creates one SDK specification and adapts the handler's argument-map signature to it. */
    private static McpServerFeatures.SyncToolSpecification specification(
            String name,
            String description,
            McpSchema.JsonSchema schema,
            BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> callback) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(new McpSchema.Tool(name, description, null, schema, null, null, null))
                .callHandler((exchange, request) -> callback.apply(exchange, request.arguments()))
                .build();
    }
}
