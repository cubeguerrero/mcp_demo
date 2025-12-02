# MCP Demo

This is a demo application that uses [vertx-mcp](https://github.com/Kinotic-Foundation/vertx-mcp). Which integrates the Java SDK of the model context protocol with Vertx. It uses the async version of `McpServer` provided by the SDK.

### Things to note about vertx-mcp

1. Last commit was 4 months ago.
2. Works only with Java 21
3. Works only with Vertx 4.5

## Prerequisite

We need a local MySQL running. I chose to run it in Docker so you can execute the following to have a MySQL docker container running:

```
▶ docker run --name=local-mysql -e MYSQL_DATABASE=mcp_demo -e MYSQL_USER=cubeguerrero -e MYSQL_PASSWORD=password -e MYSQL_ROOT_PASSWORD=password -p 3306:3306 mysql
```

## Build/Run

The test assumes that there's a local server running at port 3001. Launch the MCP server by running

```
./gradlew run
```

## Run the test

### Non-blocking

```
./gradlew test --tests "mcp_demo.McpConcurrentConnectionTest.testConcurrentConnectionsNonBlocking" --info
```

### Blocking

```
./gradlew test --tests "mcp_demo.McpConcurrentConnectionTest.testConcurrentConnectionsBlocking" --info
```

