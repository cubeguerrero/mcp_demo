package mcp_demo.tools;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.vertx.sqlclient.Pool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mcp_demo.cdi.McpTool;
import mcp_demo.cdi.ToolProvider;
import reactor.core.publisher.Mono;

@ApplicationScoped
@McpTool
public class CalculatorTool implements ToolProvider {

    @Inject
    Pool mysqlClient;

    @Override
    public McpServerFeatures.AsyncToolSpecification getToolSpec() {
        return McpServerFeatures.AsyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder()
                .name("calculator")
                .description("Basic mathematical operations")
                .inputSchema("""
                    {
                      "type": "object",
                      "properties": {
                        "operation": {
                          "type": "string", 
                          "enum": ["add", "subtract", "multiply", "divide"]
                        },
                        "a": {"type": "number"},
                        "b": {"type": "number"}
                      },
                      "required": ["operation", "a", "b"]
                    }
                    """)
                .build())
            .callHandler((exchange, toolReq) -> {
                String operation = (String) toolReq.arguments().get("operation");
                double a = ((Number) toolReq.arguments().get("a")).doubleValue();
                double b = ((Number) toolReq.arguments().get("b")).doubleValue();

                double calcResult = switch (operation) {
                    case "add" -> a + b;
                    case "subtract" -> a - b;
                    case "multiply" -> a * b;
                    case "divide" -> a / b;
                    default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
                };

                return Mono.fromCompletionStage(
                    mysqlClient.query("SELECT 1=1 AS result")
                        .execute()
                        .toCompletionStage()
                ).map(rows -> {
                    var row = rows.iterator().next();
                    String dbResult = "DB check: " + row.getInteger("result");
                    return McpSchema.CallToolResult.builder()
                        .textContent(List.of(String.valueOf(calcResult), dbResult))
                        .isError(false)
                        .build();
                }).onErrorResume(err -> Mono.just(McpSchema.CallToolResult.builder()
                    .textContent(List.of(String.valueOf(calcResult), "DB error: " + err.getMessage()))
                    .isError(false)
                    .build()));
            })
            .build();
    }
}
