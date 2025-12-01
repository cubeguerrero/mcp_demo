package mcp_demo.cdi;

import io.modelcontextprotocol.server.McpServerFeatures;

public interface ToolProvider {
    McpServerFeatures.AsyncToolSpecification getToolSpec();
}

