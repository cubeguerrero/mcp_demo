package mcp_demo;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent connection test for MCP server.
 * Creates multiple McpAsyncClient instances, each making a tool call to test
 * the server's connection handling capacity and event loop behavior.
 * 
 * Prerequisites:
 * - MCP server must be running on http://localhost:3001/mcp
 * - MySQL must be running (calculator tool has DB dependency)
 * 
 * Test scenarios:
 * - simulateBlocking=true: Demonstrates event loop blocking (sequential execution)
 * - simulateBlocking=false: Demonstrates proper async handling (concurrent execution)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class McpConcurrentConnectionTest {

    private static final int CONCURRENT_CLIENTS = 100; // Reduced for blocking test (would take 50s with 10 clients)
    private static final int SERVER_PORT = 3001;
    private static final String SERVER_URL = "http://localhost:" + SERVER_PORT + "/mcp";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120); // Increased for blocking tests

    private List<McpAsyncClient> clients;

    @BeforeAll
    void setUp() {
        clients = new ArrayList<>();
        System.out.println("Connecting to MCP server at " + SERVER_URL);
        System.out.println("Ensure the server is running before executing these tests.");
    }

    @AfterAll
    void tearDown() {
        System.out.println("Cleaning up clients...");
        
        // Close all clients gracefully
        if (clients != null) {
            for (McpAsyncClient client : clients) {
                try {
                    client.closeGracefully().block(Duration.ofSeconds(5));
                } catch (Exception e) {
                    System.err.println("Error closing client: " + e.getMessage());
                }
            }
            clients.clear();
        }
        
        System.out.println("Cleanup complete.");
    }

    @Test
    @DisplayName("Concurrent MCP clients with BLOCKING delay - demonstrates event loop blocking")
    void testConcurrentConnectionsBlocking() throws Exception {
        System.out.println("\n=== BLOCKING TEST ===");
        System.out.println("Creating " + CONCURRENT_CLIENTS + " concurrent MCP clients with BLOCKING delay...");
        System.out.println("Expected: Requests execute SEQUENTIALLY (event loop blocked)");
        System.out.println("Expected duration: ~" + (CONCURRENT_CLIENTS * 5) + " seconds\n");
        
        runConcurrentTest(true); // simulateBlocking = true
    }

    @Test
    @DisplayName("Concurrent MCP clients with NON-BLOCKING delay - demonstrates proper async")
    void testConcurrentConnectionsNonBlocking() throws Exception {
        System.out.println("\n=== NON-BLOCKING TEST ===");
        System.out.println("Creating " + CONCURRENT_CLIENTS + " concurrent MCP clients with NON-BLOCKING delay...");
        System.out.println("Expected: Requests execute CONCURRENTLY (event loop free)");
        System.out.println("Expected duration: ~5-10 seconds\n");
        
        runConcurrentTest(false); // simulateBlocking = false
    }

    private void runConcurrentTest(boolean simulateBlocking) throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Mono<CallToolResult>> callMonos = new ArrayList<>();

        // Create clients and prepare tool calls
        for (int i = 0; i < CONCURRENT_CLIENTS; i++) {
            final int clientIndex = i;
            
            // Create transport for each client
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(SERVER_URL)
                .build();
            
            // Create async client
            McpAsyncClient client = McpClient.async(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
            
            clients.add(client);
            
            // Initialize client and prepare tool call
            Mono<CallToolResult> callMono = client.initialize()
                .then(Mono.defer(() -> {
                    // Create calculator request with simulateBlocking parameter
                    CallToolRequest request = new CallToolRequest(
                        "calculator",
                        Map.of(
                            "operation", "add",
                            "a", clientIndex,
                            "b", 1,
                            "simulateBlocking", simulateBlocking
                        )
                    );
                    return client.callTool(request);
                }))
                .doOnSuccess(result -> {
                    successCount.incrementAndGet();
                    System.out.println("Client " + clientIndex + " succeeded: " + extractResult(result));
                })
                .doOnError(error -> {
                    errorCount.incrementAndGet();
                    System.err.println("Client " + clientIndex + " failed: " + error.getMessage());
                })
                .onErrorResume(e -> Mono.empty());
            
            callMonos.add(callMono);
        }

        System.out.println("Executing " + CONCURRENT_CLIENTS + " concurrent tool calls (blocking=" + simulateBlocking + ")...");
        long startTime = System.currentTimeMillis();

        // Execute all calls concurrently using Flux.merge
        Flux.merge(callMonos)
            .collectList()
            .block(Duration.ofMinutes(10)); // Increased timeout for blocking test

        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\n=== Test Results (blocking=" + simulateBlocking + ") ===");
        System.out.println("Total clients: " + CONCURRENT_CLIENTS);
        System.out.println("Successful calls: " + successCount.get());
        System.out.println("Failed calls: " + errorCount.get());
        System.out.println("Total duration: " + duration + "ms");
        System.out.println("Average per request: " + (duration / CONCURRENT_CLIENTS) + "ms");
        
        if (simulateBlocking) {
            System.out.println("\nBLOCKING ANALYSIS:");
            System.out.println("- If duration is close to " + (CONCURRENT_CLIENTS * 5000) + "ms, event loop was blocked");
            System.out.println("- Requests executed sequentially due to Thread.sleep blocking");
        } else {
            System.out.println("\nNON-BLOCKING ANALYSIS:");
            System.out.println("- If duration is close to 5000-10000ms, event loop was NOT blocked");
            System.out.println("- Requests executed concurrently using async delay");
        }

        // Assertions
        assertTrue(successCount.get() > 0, "At least some requests should succeed");
        assertEquals(CONCURRENT_CLIENTS, successCount.get() + errorCount.get(), 
            "All requests should complete (either success or error)");
        
        // We expect all to succeed
        assertEquals(CONCURRENT_CLIENTS, successCount.get(), 
            "All " + CONCURRENT_CLIENTS + " concurrent requests should succeed");
    }

    @Test
    @DisplayName("Verify calculator tool results are correct")
    void testCalculatorResultsCorrectness() throws Exception {
        System.out.println("Testing calculator result correctness with concurrent clients...");
        
        int testClients = 10; // Use fewer clients for result verification
        List<Mono<CallToolResult>> callMonos = new ArrayList<>();
        Map<Integer, Double> expectedResults = new java.util.concurrent.ConcurrentHashMap<>();

        for (int i = 0; i < testClients; i++) {
            final int clientIndex = i;
            final double a = clientIndex * 10;
            final double b = clientIndex + 5;
            expectedResults.put(clientIndex, a + b);
            
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(SERVER_URL)
                .build();
            
            McpAsyncClient client = McpClient.async(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
            
            clients.add(client);
            
            Mono<CallToolResult> callMono = client.initialize()
                .then(Mono.defer(() -> {
                    CallToolRequest request = new CallToolRequest(
                        "calculator",
                        Map.of(
                            "operation", "add",
                            "a", a,
                            "b", b
                        )
                    );
                    return client.callTool(request);
                }))
                .doOnSuccess(result -> {
                    String resultStr = extractResult(result);
                    double actualResult = Double.parseDouble(resultStr.split("\n")[0]);
                    double expected = expectedResults.get(clientIndex);
                    System.out.println("Client " + clientIndex + ": " + a + " + " + b + " = " + actualResult + " (expected: " + expected + ")");
                    assertEquals(expected, actualResult, 0.001, 
                        "Calculator result should be correct for client " + clientIndex);
                });
            
            callMonos.add(callMono);
        }

        Flux.merge(callMonos)
            .collectList()
            .block(Duration.ofMinutes(1));

        System.out.println("Calculator correctness test completed.");
    }

    private String extractResult(CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "null";
        }
        
        var content = result.content().get(0);
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        return content.toString();
    }
}

