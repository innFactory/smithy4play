$version: "2"
namespace testDefinitions.test

use alloy#simpleRestJson
use de.innfactory.mcp#mcpTool
use de.innfactory.mcp#mcpName
use de.innfactory.mcp#mcpDescription
use de.innfactory.mcp#mcpCategories

@mixin
structure PaginatedResult {
    @required
    total: Integer
    nextCursor: String
}

@mixin
structure PaginationParams {
    @httpQuery("cursor")
    cursor: String

    @httpQuery("limit")
    @default(50)
    @range(min: 1, max: 100)
    limit: Integer
}

@mixin
structure SearchParams {
    @httpQuery("query")
    query: String
}

@mixin
structure CustomerIdMixin {
    @httpLabel
    @required
    customerId: String
}

@httpBearerAuth
@simpleRestJson
service MCPExampleControllerService {
    version: "0.0.1",
    operations: [
        ListCustomers,
        GetCustomer,
        CreateCustomer,
        UpdateCustomer,
        DeleteCustomer,
        SearchCustomers
    ]
}

// List customers with pagination and search
@readonly
@http(method: "GET", uri: "/api/v1/customers", code: 200)
@mcpTool
@mcpName("list_customers")
@mcpDescription("Lists customers with pagination and optional search")
@mcpCategories(["customers", "list", "pagination"])
operation ListCustomers {
    input := with [PaginationParams, SearchParams] {}
    output := {
        @required
        @httpPayload
        body: PaginatedCustomers
    }
}

// Get single customer by ID
@readonly
@http(method: "GET", uri: "/api/v1/customers/{customerId}", code: 200)
@mcpTool
@mcpName("get_customer")
@mcpDescription("Retrieves a customer by their unique ID")
@mcpCategories(["customers", "retrieve"])
operation GetCustomer {
    input := with [CustomerIdMixin] {}
    output := {
        @required
        @httpPayload
        body: Customer
    }
}

// Create new customer
@http(method: "POST", uri: "/api/v1/customers", code: 201)
@mcpTool
@mcpName("create_customer")
@mcpDescription("Creates a new customer")
@mcpCategories(["customers", "create"])
operation CreateCustomer {
    input := {
        @required
        @httpPayload
        body: CustomerCreateDto
    }
    output := {
        @required
        @httpPayload
        body: Customer
    }
}

// Update existing customer
@idempotent
@http(method: "PATCH", uri: "/api/v1/customers/{customerId}", code: 200)
@mcpTool
@mcpName("update_customer")
@mcpDescription("Updates an existing customer")
@mcpCategories(["customers", "update"])
operation UpdateCustomer {
    input := with [CustomerIdMixin] {
        @required
        @httpPayload
        body: CustomerUpdateDto
    }
    output := {
        @required
        @httpPayload
        body: Customer
    }
}

// Delete customer
@idempotent
@http(method: "DELETE", uri: "/api/v1/customers/{customerId}", code: 204)
@mcpTool
@mcpName("delete_customer")
@mcpDescription("Deletes a customer by ID")
@mcpCategories(["customers", "delete"])
operation DeleteCustomer {
    input := with [CustomerIdMixin] {}
}

// Search customers with complex parameters
@readonly
@http(method: "GET", uri: "/api/v1/customers/search", code: 200)
@mcpTool
@mcpName("search_customers")
@mcpDescription("Advanced customer search with multiple filters")
@mcpCategories(["customers", "search", "advanced"])
operation SearchCustomers {
    input := with [PaginationParams] {
        @httpQuery("name")
        name: String
        
        @httpQuery("email")
        email: String
        
        @httpQuery("active")
        active: Boolean
        
        @httpQuery("tags")
        tags: TagList
    }
    output := {
        @required
        @httpPayload
        body: PaginatedCustomers
    }
}

// Data structures
structure Customer {
    @required
    id: String
    
    @required
    name: String
    
    @required
    email: String
    
    active: Boolean
    
    tags: TagList
    
    createdAt: Timestamp
    
    updatedAt: Timestamp
}

structure CustomerCreateDto {
    @required
    name: String
    
    @required
    email: String
    
    active: Boolean = true
    
    tags: TagList
}

structure CustomerUpdateDto {
    name: String
    
    email: String
    
    active: Boolean
    
    tags: TagList
}

structure PaginatedCustomers {
    @required
    customers: CustomerList
    
    @required
    total: Integer
    
    nextCursor: String
}

list CustomerList {
    member: Customer
}

list TagList {
    member: String
}