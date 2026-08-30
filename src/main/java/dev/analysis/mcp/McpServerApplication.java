package dev.analysis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analysis.mcp.application.StartupContextResolver;
import dev.analysis.mcp.constants.GeneralConstant;
import dev.analysis.mcp.context.AnalysisContext;
import dev.analysis.mcp.graph.DependencyGraphBuilder;
import dev.analysis.mcp.index.InMemoryIndexLifecycleService;
import dev.analysis.mcp.sync.SyncLifecycleService;
import dev.analysis.mcp.sync.WatchServiceManager;
import dev.analysis.mcp.tools.DependencyAnalysisTools;
import dev.analysis.mcp.tools.ToolSpecifications;
import dev.analysis.mcp.utils.CodebaseScanner;
import dev.analysis.mcp.utils.DependencyGraphService;
import dev.analysis.mcp.utils.JavaDependencyParser;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the MCP transport and registers explicit, manual indexing tools. */
public class McpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    public static void main(String[] args) throws IOException {
        AnalysisContext context = StartupContextResolver.resolve(args);
        DependencyGraphService graphService = new DependencyGraphService();
        InMemoryIndexLifecycleService indexLifecycle = new InMemoryIndexLifecycleService(
                context,
                new CodebaseScanner(),
                new JavaDependencyParser(),
                new DependencyGraphBuilder(),
                graphService);
        SyncLifecycleService syncLifecycle = new SyncLifecycleService(indexLifecycle);
        WatchServiceManager watchServiceManager = new WatchServiceManager(
                context.rootPath(), syncLifecycle::markDirty, syncLifecycle::triggerSync, syncLifecycle::requestFullSync);
        watchServiceManager.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            watchServiceManager.close();
            syncLifecycle.shutdown();
        }, "dependency-analyzer-shutdown"));
        DependencyAnalysisTools tools = new DependencyAnalysisTools(graphService, indexLifecycle, syncLifecycle);

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);
        McpServer.sync(transportProvider)
                .serverInfo(GeneralConstant.SERVER_NAME, GeneralConstant.SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
                .tools(ToolSpecifications.create(tools))
                .build();

        log.info("Dependency Analyzer MCP Server started for context {} at {}. Run index_project to analyze sources.",
                context.id(), context.rootPath());
    }
}
