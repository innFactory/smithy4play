# MCP (Model Context Protocol) Support

smithy4play now includes built-in support for the Model Context Protocol (MCP), enabling LLMs to interact with your Smithy-defined APIs through a standardized interface.

## Overview

MCP allows LLMs to discover and call your API endpoints as tools. By annotating your Smithy operations with MCP traits, you can expose them through a dedicated `/mcp` endpoint that provides:

- Tool discovery (`/mcp/tools`) - Lists all available MCP-enabled operations
- Tool execution (`/mcp/call`) - Executes tools by proxying to your original endpoints

## MCP Traits

The following MCP traits are available to annotate your operations:

### @mcpTool
Marks an operation as available through MCP.

```smithy
@mcpTool
@http(method: "GET", uri: "/user/{id}")
operation GetUser {
    input: GetUserRequest,
    output: GetUserResponse
}
```

### @mcpName
Overrides the default operation name for the tool.

```smithy
@mcpTool
@mcpName("get_user_by_id")
operation GetUser { ... }
```

### @mcpDescription
Provides a description for the tool.

```smithy
@mcpTool
@mcpDescription("Retrieves user information by ID")
operation GetUser { ... }
```

### @mcpCategories
Specifies categories for the tool (defaults to the service name).

```smithy
@mcpTool
@mcpCategories(["users", "management"])
operation GetUser { ... }
```

## Example Usage

### 1. Define your Smithy service with MCP annotations:

```smithy
$version: "2"
namespace com.example

use de.innfactory.mcp#mcpTool
use de.innfactory.mcp#mcpName
use de.innfactory.mcp#mcpDescription

@simpleRestJson
service UserService {
    version: "1.0",
    operations: [GetUser, CreateUser]
}

@mcpTool
@mcpName("get_user")
@mcpDescription("Retrieves a user by their ID")
@http(method: "GET", uri: "/user/{id}")
operation GetUser {
    input: GetUserRequest,
    output: GetUserResponse
}

@mcpTool
@mcpName("create_user")
@mcpDescription("Creates a new user")
@http(method: "POST", uri: "/user")
operation CreateUser {
    input: CreateUserRequest,
    output: CreateUserResponse
}
```

### 2. Implement your controller:

```scala
@Singleton
@AutoRouting
class UserController @Inject()(implicit
    cc: ControllerComponents,
    ec: ExecutionContext
) extends UserServiceGen[ContextRoute] {
    
    override def getUser(id: String): ContextRoute[GetUserResponse] = {
        // Your implementation
    }
    
    override def createUser(request: CreateUserRequest): ContextRoute[CreateUserResponse] = {
        // Your implementation  
    }
}
```

### 3. The MCP endpoints are automatically available:

- `GET /mcp/tools` - Lists all MCP-enabled operations
- `POST /mcp/call` - Executes a specific tool

## MCP Endpoints

### List Tools
`GET /mcp/tools`

Returns a list of all available tools:

```json
{
  "tools": [
    {
      "name": "get_user",
      "description": "Retrieves a user by their ID",
      "inputSchema": {
        "type": "object",
        "properties": {
          "id": {"type": "string"}
        },
        "required": ["id"]
      }
    }
  ]
}
```

### Call Tool
`POST /mcp/call`

Executes a tool with the provided arguments:

```json
{
  "name": "get_user",
  "arguments": {
    "id": "123"
  }
}
```

Response:
```json
{
  "content": [
    {
      "type": "text",
      "text": "User retrieved successfully"
    }
  ],
  "isError": false
}
```

## Integration

MCP support is automatically integrated when using the `AutoRouter`. The MCP endpoints are added alongside your regular API routes, maintaining the same security and middleware configuration.

## Authentication

MCP tool calls respect the same authentication and authorization mechanisms as your original endpoints. Bearer tokens and other headers are passed through to the underlying operations.