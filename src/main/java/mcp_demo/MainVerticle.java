package mcp_demo;

import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.mcp.McpVerticle;
import io.vertx.ext.mcp.transport.VertxMcpStreamableServerTransportProvider;
import io.modelcontextprotocol.server.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import mcp_demo.tools.CalculatorTool;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("MCP_PORT", "4001"));
    String baseUrl = System.getenv().getOrDefault("MCP_BASE_URL", "http://localhost:4001");

    Vertx vertx = Vertx.vertx();

    // Create transport
    var transport = VertxMcpStreamableServerTransportProvider.builder()
      .objectMapper(new ObjectMapper())
      .mcpEndpoint("/mcp")
      .vertx(vertx)
      .build();

    // Create MCP server specification
    var mcpServerSpec = McpServer.async(transport)
      .serverInfo("production-server", "1.0.0")
      .capabilities(ServerCapabilities.builder().tools(true).build())
      .tools(CalculatorTool.create());

    // Deploy the verticle
    vertx.deployVerticle(new McpVerticle(port, transport, mcpServerSpec), ar -> {
      if (ar.succeeded()) {
        System.out.println("MCP Server started on port " + port);
        System.out.println("Endpoint: " + baseUrl + "/sse");
        System.out.println("Health check: " + baseUrl + "/health");
      } else {
        System.err.println("Failed to start MCP Server: " + ar.cause());
        System.exit(1);
      }
    });
  }
}
