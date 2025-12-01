package mcp_demo.tools;

import java.util.List;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

public class CalculatorTool {
  public static McpServerFeatures.AsyncToolSpecification create() {
    return McpServerFeatures.AsyncToolSpecification.builder()
      .tool(McpSchema.Tool.builder()
        .name("calculator")
        .description("Basic mathematical operations")
        .inputSchema("""
          {
            "type": "object",
            "properties": {
              "operation": {
                "type": "string",\s
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

        double result = switch(operation) {
          case "add" -> a + b;
          case "subtract" -> a - b;
          case "multiply" -> a * b;
          case "divide" -> a / b;
          default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };

        return Mono.just(McpSchema.CallToolResult.builder()
          .textContent(List.of(String.valueOf(result)))
          .isError(false)
          .build());
      })
      .build();
  }
}
