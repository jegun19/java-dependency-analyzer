package dev.analysis.mcp;

import dev.analysis.mcp.utils.CodebaseScanner;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser;
import dev.analysis.mcp.constants.GeneralConstant;
import dev.analysis.mcp.graph.DependencyGraphBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analysis.mcp.tools.DependencyAnalysisTools;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the MCP Dependency Analyzer server.
 *
 * <p>This application provides MCP tools for analyzing Java class dependencies
 * within a given repository. It scans Java source files, parses imports and type
 * references, builds a dependency graph, and exposes the following MCP tools:</p>
 *
 * <ul>
 *   <li>{@code get_dependencies} - Get all classes that a given class depends on</li>
 *   <li>{@code get_reverse_dependencies} - Get all classes that depend on a given class</li>
 *   <li>{@code trace_dependency_chain} - Trace a dependency path between two classes</li>
 * </ul>
 *
 * <p>The server communicates via STDIO using the MCP protocol.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * java -jar mcp-dependency-analyzer.jar --repoPath=/path/to/java/repo
 * </pre>
 */
public class McpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    /**
     * Starts the MCP Dependency Analyzer server.
     *
     * @param args command-line arguments. Supports {@code --repoPath=/path/to/repo}
     *             or a positional argument for the repository path.
     * @throws Exception if the server fails to start
     */
    public static void main(String[] args) throws Exception {
        Path repoRoot = resolveRepoRoot(args);
        log.info("Analyzing repository: {}", repoRoot);

        CodebaseScanner scanner = new CodebaseScanner();
        JavaDependencyParser parser = new JavaDependencyParser();
        DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder();
        DependencyGraphService graphService = new DependencyGraphService();

        List<Path> javaFiles = scanner.scan(repoRoot);
        log.info("Found {} Java files", javaFiles.size());

        var dependencies = parser.parse(repoRoot, javaFiles);
        var graph = graphBuilder.build(dependencies);
        graphService.setGraph(graph);

        DependencyAnalysisTools tools = new DependencyAnalysisTools(graphService);

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);
        var syncToolSpecifications = getSyncToolSpecifications(tools);

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(GeneralConstant.SERVER_NAME, GeneralConstant.SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(syncToolSpecifications)
                .build();

        log.info("Starting Dependency Analyzer MCP Server...");
    }

    /**
     * Resolves the repository root path from command-line arguments.
     *
     * @param args command-line arguments
     * @return the resolved absolute path to the repository root
     * @throws IllegalArgumentException if no valid path is provided
     */
    private static Path resolveRepoRoot(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(GeneralConstant.ARG_REPO_PATH_PREFIX)) {
                String value = arg.substring(GeneralConstant.ARG_REPO_PATH_PREFIX.length());
                if (!value.isBlank()) {
                    return Path.of(value).toAbsolutePath().normalize();
                }
            }
        }

        if (args.length > 0 && !args[0].startsWith("--")) {
            return Path.of(args[0]).toAbsolutePath().normalize();
        }

        throw new IllegalArgumentException(GeneralConstant.USAGE_MESSAGE);
    }

    /**
     * Creates the list of MCP tool specifications for the server.
     *
     * @param tools the tools instance that handles tool execution
     * @return list of sync tool specifications
     */
    private static List<McpServerFeatures.SyncToolSpecification> getSyncToolSpecifications(DependencyAnalysisTools tools) {
        var classSchema = new McpSchema.JsonSchema(
                GeneralConstant.SCHEMA_TYPE_OBJECT,
                java.util.Map.of(GeneralConstant.PARAM_CLASS, java.util.Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING)),
                List.of(GeneralConstant.PARAM_CLASS),
                null,
                null,
                null
        );

        var chainSchema = new McpSchema.JsonSchema(
                GeneralConstant.SCHEMA_TYPE_OBJECT,
                java.util.Map.of(
                        GeneralConstant.PARAM_FROM, java.util.Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING),
                        GeneralConstant.PARAM_TO, java.util.Map.of(GeneralConstant.SCHEMA_TYPE_STRING, GeneralConstant.SCHEMA_TYPE_STRING)
                ),
                List.of(GeneralConstant.PARAM_FROM, GeneralConstant.PARAM_TO),
                null,
                null,
                null
        );

        var getDepsTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(GeneralConstant.TOOL_GET_DEPENDENCIES, GeneralConstant.TOOL_GET_DEPENDENCIES_DESC, null, classSchema, null, null, null),
                tools::getDependencies
        );

        var getReverseDepsTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(GeneralConstant.TOOL_GET_REVERSE_DEPENDENCIES, GeneralConstant.TOOL_GET_REVERSE_DEPENDENCIES_DESC, null, classSchema, null, null, null),
                tools::getReverseDependencies
        );

        var traceChainTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(GeneralConstant.TOOL_TRACE_DEPENDENCY_CHAIN, GeneralConstant.TOOL_TRACE_DEPENDENCY_CHAIN_DESC, null, chainSchema, null, null, null),
                tools::traceDependencyChain
        );

        return List.of(getDepsTool, getReverseDepsTool, traceChainTool);
    }
}