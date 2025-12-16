# MCP Server - Client Setup Instructions

## Server Information

**MCP Server URL**: `http://localhost:8080/mcp`
**Status**: ✅ Running on port 8080
**Transport**: SSE (Server-Sent Events)
**Tool**: `get_books` - Search for books using OpenLibrary API

## Client Configuration

### TypeScript Client Setup

Update your `.env` file:

```env
VITE_MCP_SERVER_URL=http://localhost:8080/mcp
```

### How It Works

The MCP server uses **dual-channel architecture**:

1. **GET /mcp** - SSE endpoint for server→client streaming
   - Client opens persistent SSE connection
   - Server sends `endpoint` event with session ID
   - All server responses come through this channel

2. **POST /mcp/messages?sessionId=<id>** - Client→server messages
   - Client sends JSON-RPC messages via POST
   - Server processes and responds via SSE channel

### Connection Flow

```
1. Client → GET /mcp (opens SSE connection)
2. Server → SSE event: {"endpoint":"/mcp/messages?sessionId=abc123"}
3. Client → POST /mcp/messages?sessionId=abc123 (initialize request)
4. Server → SSE response (initialize result)
5. Client → POST /mcp/messages?sessionId=abc123 (tools/list request)
6. Server → SSE response (tools list)
7. Client → POST /mcp/messages?sessionId=abc123 (tools/call request)
8. Server → SSE response (tool result)
```

## Available Tool

### `get_books`

**Description**: Search for books using title and optional author names

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "title": {
      "type": "string",
      "description": "Book title to search"
    },
    "author": {
      "type": "array",
      "description": "Author names",
      "items": {
        "type": "string"
      }
    }
  },
  "required": ["title"]
}
```

**Example Usage**:
```typescript
import { initMCPClient, callMCPTool } from './services/mcp';

// Initialize connection
await initMCPClient();

// Call the tool
const result = await callMCPTool('get_books', {
  title: 'Alice',
  author: ['Lewis Carroll']
});

console.log(result);
```

**Expected Response**:
```
Found 10 book(s):

1. Alice's Adventures in Wonderland
   Authors: Lewis Carroll

2. Through the Looking-Glass
   Authors: Lewis Carroll

...
```

## Testing

### Manual Test with curl

```bash
# 1. Open SSE connection (in one terminal)
curl -N http://localhost:8080/mcp

# 2. Wait for endpoint event, then send initialize (in another terminal)
curl -X POST http://localhost:8080/mcp/messages?sessionId=<SESSION_ID> \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}'

# 3. List tools
curl -X POST http://localhost:8080/mcp/messages?sessionId=<SESSION_ID> \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":2}'

# 4. Call get_books tool
curl -X POST http://localhost:8080/mcp/messages?sessionId=<SESSION_ID> \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_books","arguments":{"title":"Alice","author":[]}},"id":3}'
```

## TypeScript Client Code

Your existing client code should work directly:

```typescript
// Initialize (happens automatically in your service)
const client = await initMCPClient();

// Get available tools
const tools = await getMCPTools();
console.log(tools); // Will show get_books tool

// Call the tool
const result = await callMCPTool('get_books', {
  title: 'Alice',
  author: []
});
```

## Troubleshooting

### CORS Issues
If you encounter CORS errors, check the server's CORS configuration in:
`mcp-app/src/main/kotlin/HTTP.kt`

### Connection Issues
- Ensure server is running: `./gradlew :mcp-app:run`
- Check port 8080 is not blocked by firewall
- Verify URL in `.env` is correct

### Session Timeout
If sessions expire, the client will automatically reconnect and get a new session ID.

## Server Management

```bash
# Start server
cd /home/khan/Projects/ai-challenge-server
./gradlew :mcp-app:run

# Build server
./gradlew build

# Stop server
# Ctrl+C or kill the Java process
```

## Next Steps

1. ✅ MCP Server is running
2. ✅ HTTP/SSE transport configured
3. ✅ `get_books` tool registered
4. 📝 Update your client `.env` with: `VITE_MCP_SERVER_URL=http://localhost:8080/mcp`
5. 🚀 Start your client and test the connection
6. 🎉 Call `getMCPTools()` to see the available tool
7. 🔍 Call `callMCPTool('get_books', {title: 'Alice'})` to search for books