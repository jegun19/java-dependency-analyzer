# MCP Dependency Analyzer (Java)

A Java 17 MCP (Model Context Protocol) server that performs **static dependency analysis** on a Java codebase and exposes the results through **JSON-RPC tools over STDIO**. Built with the official MCP Java SDK.

## Requirements

- Java 17
- Maven 3.9+

## Build

```bash
mvn -q clean compile
```

## Run

Provide the repository root path to analyze via `--repoPath`.

```bash
mvn -q exec:java -Dexec.mainClass="dev.analysis.mcp.McpServerApplication" -Dexec.args="--repoPath=/path/to/java/repo"
```

Or run the shaded JAR:

```bash
mvn -q package
java -jar target/mcp-dependency-analyzer-0.0.1-SNAPSHOT.jar --repoPath=/path/to/java/repo
```

The server starts its JSON-RPC loop immediately. It does not scan source files during startup.
Run `index_project` when you want to build the in-memory dependency graph. The index is lost when
the process stops; local persistence and incremental synchronization are planned separately.

## MCP Tools

The server exposes six tools:

### index_project

Builds the dependency graph for the active service context. It accepts optional `projectPath`,
`contextId`, and `force` arguments. In this single-service release, supplied path and context
values must match the startup `--repoPath`; set `force=true` to rebuild an already ready index.

### index_status

Returns JSON status for the active index, including `UNINDEXED`, `READY`, or `FAILED`, revision,
timestamps, and file/node/edge counts.

Before a successful `index_project` call, dependency-query tools return an actionable
`PROJECT_NOT_INDEXED` error rather than empty results.

### get_dependencies

Returns all classes that a given class depends on (direct dependencies).

### get_reverse_dependencies

Returns all classes that depend on a given class (reverse dependencies).

### trace_dependency_chain

Traces a dependency path from one class to another.

### all_classes

Returns all classes discovered in the codebase.

## Example JSON-RPC Requests

```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_dependencies","arguments":{"class":"com.example.service.OrderService"}}}
```

```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"index_project","arguments":{"force":true}}}
```

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_reverse_dependencies","arguments":{"class":"com.example.service.PaymentService"}}}
```

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"trace_dependency_chain","arguments":{"from":"com.example.controller.OrderController","to":"com.example.repository.PaymentRepository"}}}
```

## Response Format

- On success: `{ "jsonrpc": "2.0", "id": <id>, "result": { ... } }`
- On error: `{ "jsonrpc": "2.0", "id": <id>, "error": { "code": ..., "message": ..., "data": ... } }`

## Client Configuration

To use this server with an MCP client, add the following configuration to your client's MCP settings.

### OpenCode

Add to OpenCode's MCP configuration:

```json
{
  "mcp": {
    "java-dependency-analyzer": {
      "type": "local",
      "command": [
        "java",
        "-jar",
        "/path/to/mcp-dependency-analyzer/target/mcp-dependency-analyzer-0.0.1-SNAPSHOT.jar",
        "--repoPath=/path/to/java/repo"
      ],
      "enabled": true
    }
  }
}
```

### Windsurf

Add to Windsurf's MCP configuration:

```json
{
  "mcpServers": {
    "java-dependency-analyzer": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-dependency-analyzer/target/mcp-dependency-analyzer-0.0.1-SNAPSHOT.jar",
        "--repoPath=/path/to/java/repo"
      ]
    }
  }
}
```


**Note**: Replace `/path/to/mcp-dependency-analyzer/` with the actual path to your built JAR, and `/path/to/java/repo` with the Java codebase you want to analyze.
