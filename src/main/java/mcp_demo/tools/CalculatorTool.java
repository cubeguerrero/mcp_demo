package mcp_demo.tools;

import java.time.Duration;
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

    private static final long DELAY_MS = 5000; // 5 seconds delay

    @Inject
    Pool mysqlClient;

    @Override
    public McpServerFeatures.AsyncToolSpecification getToolSpec() {
        return McpServerFeatures.AsyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder()
                .name("calculator")
                .description("Basic mathematical operations with optional delay simulation")
                .inputSchema("""
                    {
                      "type": "object",
                      "properties": {
                        "operation": {
                          "type": "string", 
                          "enum": ["add", "subtract", "multiply", "divide"]
                        },
                        "a": {"type": "number"},
                        "b": {"type": "number"},
                        "simulateBlocking": {
                          "type": "boolean",
                          "description": "If true, blocks the event loop with Thread.sleep. If false, uses async delay.",
                          "default": false
                        }
                      },
                      "required": ["operation", "a", "b"]
                    }
                    """)
                .build())
            .callHandler((exchange, toolReq) -> {
                String operation = (String) toolReq.arguments().get("operation");
                double a = ((Number) toolReq.arguments().get("a")).doubleValue();
                double b = ((Number) toolReq.arguments().get("b")).doubleValue();
                
                // Get simulateBlocking parameter, default to false
                Boolean simulateBlocking = (Boolean) toolReq.arguments().get("simulateBlocking");
                boolean blocking = simulateBlocking != null && simulateBlocking;

                long startTime = System.currentTimeMillis();
                System.out.println("[" + Thread.currentThread().getName() + "] Calculator request started: " 
                    + operation + "(" + a + ", " + b + ") - blocking=" + blocking);

                double calcResult = switch (operation) {
                    case "add" -> a + b;
                    case "subtract" -> a - b;
                    case "multiply" -> a * b;
                    case "divide" -> a / b;
                    default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
                };

                if (blocking) {
                    // BLOCKING: This will block the Vert.x event loop!
                    // All concurrent requests will queue up and execute sequentially
                    return simulateBlockingDelay(calcResult, startTime);
                } else {
                    // NON-BLOCKING: Uses Reactor's delayElement which doesn't block the event loop
                    // Concurrent requests will execute in parallel
                    return simulateNonBlockingDelay(calcResult, startTime);
                }
            })
            .build();
    }

    /**
     * BLOCKING implementation - uses Thread.sleep which blocks the event loop.
     * This demonstrates the WRONG way to handle long-running tasks in Vert.x.
     */
    private Mono<McpSchema.CallToolResult> simulateBlockingDelay(double calcResult, long startTime) {
        try {
            System.out.println("[" + Thread.currentThread().getName() + "] Starting BLOCKING sleep for " + DELAY_MS + "ms...");
            Thread.sleep(DELAY_MS); // This blocks the event loop!
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("[" + Thread.currentThread().getName() + "] BLOCKING request completed in " + duration + "ms");
        
        return Mono.just(McpSchema.CallToolResult.builder()
            .textContent(List.of(
                String.valueOf(calcResult),
                "Delay type: BLOCKING (Thread.sleep)",
                "Duration: " + duration + "ms"
            ))
            .isError(false)
            .build());
    }

    /**
     * NON-BLOCKING implementation - uses Reactor's delayElement.
     * This demonstrates the CORRECT way to handle long-running tasks in Vert.x.
     */
    private Mono<McpSchema.CallToolResult> simulateNonBlockingDelay(double calcResult, long startTime) {
        System.out.println("[" + Thread.currentThread().getName() + "] Starting NON-BLOCKING delay for " + DELAY_MS + "ms...");
        
        return Mono.just(calcResult)
            .delayElement(Duration.ofMillis(DELAY_MS))
            .map(result -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[" + Thread.currentThread().getName() + "] NON-BLOCKING request completed in " + duration + "ms");
                
                return McpSchema.CallToolResult.builder()
                    .textContent(List.of(
                        String.valueOf(result),
                        "Delay type: NON-BLOCKING (Mono.delayElement)",
                        "Duration: " + duration + "ms"
                    ))
                    .isError(false)
                    .build();
            });
    }
}
