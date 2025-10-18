$version: "2"
namespace testDefinitions.test

use alloy#simpleRestJson
use de.innfactory.mcp#mcpTool
use de.innfactory.mcp#mcpName
use de.innfactory.mcp#mcpDescription

@simpleRestJson
service MCPExampleControllerService {
    version: "0.0.1",
    operations: [
        GetUser,
        CreateUser
    ]
}

@auth([])
@readonly
@http(method: "GET", uri: "/user/{id}", code: 200)
@mcpTool
@mcpName("get_user")
@mcpDescription("Retrieves a user by their ID")
operation GetUser {
    input: GetUserRequest,
    output: GetUserResponse
}

@auth([])
@http(method: "POST", uri: "/user", code: 201)
@mcpTool
@mcpName("create_user")
@mcpDescription("Creates a new user")
operation CreateUser {
    input: CreateUserRequest,
    output: CreateUserResponse
}

structure GetUserRequest {
    @httpLabel
    @required
    id: String
}

structure GetUserResponse {
    @required
    id: String,
    @required
    name: String,
    email: String
}

structure CreateUserRequest {
    @httpPayload
    @required
    body: CreateUserBody
}

structure CreateUserBody {
    @required
    name: String,
    @required
    email: String
}

structure CreateUserResponse {
    @required
    id: String,
    @required
    name: String,
    @required
    email: String
}