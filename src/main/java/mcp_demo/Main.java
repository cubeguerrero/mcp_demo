package mcp_demo;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import io.vertx.core.Vertx;

public class Main {
    public static void main(String[] args) {
        Weld weld = new Weld();
        // Explicitly add packages to scan (works around Gradle's split output directories)
        weld.addPackages(true, MainVerticle.class);
        
        try (WeldContainer container = weld.initialize()) {
            MainVerticle verticle = container.select(MainVerticle.class).get();
            Vertx vertx = Vertx.vertx();
            vertx.deployVerticle(verticle);
            
            // Keep application running
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

