package mcp_demo;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.mcp.McpVerticle;
import io.vertx.ext.mcp.transport.VertxMcpStreamableServerTransportProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import mcp_demo.cdi.McpTool;
import mcp_demo.cdi.ToolProvider;

@ApplicationScoped
public class MainVerticle extends AbstractVerticle {

    @Inject
    @McpTool
    Instance<ToolProvider> toolProviders;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("MCP_PORT", "3001"));
        String baseUrl = System.getenv().getOrDefault("MCP_BASE_URL", "http://localhost:3001");

        Vertx vertx = Vertx.vertx();

        // Create transport
        var transport = VertxMcpStreamableServerTransportProvider.builder()
            .objectMapper(new ObjectMapper())
            .mcpEndpoint("/mcp")
            .vertx(vertx)
            .build();

        // Collect all tool specifications from CDI-managed providers
        var toolSpecs = new ArrayList<McpServerFeatures.AsyncToolSpecification>();
        toolProviders.forEach(provider -> toolSpecs.add(provider.getToolSpec()));

        // Create MCP server specification
        var mcpServerSpec = McpServer.async(transport)
            .serverInfo("production-server", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .tools(toolSpecs.toArray(new McpServerFeatures.AsyncToolSpecification[0]));

        // Deploy the verticle
        vertx.deployVerticle(new McpVerticle(port, transport, mcpServerSpec), ar -> {
            if (ar.succeeded()) {
                System.out.println("MCP Server started on port " + port);
                System.out.println("Endpoint: " + baseUrl + "/sse");
                System.out.println("Health check: " + baseUrl + "/health");
                startPromise.complete();
            } else {
                System.err.println("Failed to start MCP Server: " + ar.cause());
                startPromise.fail(ar.cause());
            }
        });
    }
}
