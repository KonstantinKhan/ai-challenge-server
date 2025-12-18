# React Application Integration Prompt

## Server Overview

You are building a React application that integrates with an MCP (Model Context Protocol) server running at `http://localhost:8080`. The server provides task management functionality with AI-enhanced MCP tools and real-time summary streaming via SSE.

## Server Architecture

### Base URLs
- **MCP Endpoint**: `http://localhost:8080/mcp` (SSE connection)
- **MCP Messages**: `http://localhost:8080/mcp/messages` (POST for sending messages)
- **SSE Summaries**: `http://localhost:8080/api/summaries/stream` (Server-Sent Events)

### CORS Configuration
The server allows requests from `http://localhost:5173` (Vite default port) with credentials enabled.

## Available MCP Tools

The server provides **6 task management tools** designed to work with AI assistants:

### 1. **add_task**
- **Purpose**: Add a new task to the task list
- **Input**:
  ```json
  {
    "title": "string (required)",
    "description": "string (optional)"
  }
  ```
- **Output**: `{"id": number}`
- **AI Behavior**: When used by an LLM, it will automatically generate appropriate task titles based on conversation context

### 2. **get_pending_tasks**
- **Purpose**: Retrieve all pending (incomplete) tasks
- **Input**: `{}` (no parameters required)
- **Output**:
  ```json
  [
    {
      "id": number,
      "title": "string",
      "description": "string?",
      "status": "pending",
      "createdAt": "string (ISO timestamp)"
    }
  ]
  ```
- **AI Behavior**: LLMs are instructed to call this BEFORE attempting to complete tasks

### 3. **complete_task**
- **Purpose**: Mark a specific task as completed
- **Input**:
  ```json
  {
    "id": number (required)
  }
  ```
- **Output**: `{"success": true}`
- **AI Behavior**: LLMs will first query pending tasks, analyze context, then intelligently select which task to complete

### 4. **add_task_summary**
- **Purpose**: Create a summary of recent task activities
- **Input**:
  ```json
  {
    "summary_text": "string (required)"
  }
  ```
- **Output**: `{"id": number}`
- **AI Behavior**: LLMs generate meaningful summaries based on conversation context (2-3 sentences)

### 5. **get_undelivered_summaries**
- **Purpose**: Retrieve all task summaries that haven't been delivered via SSE
- **Input**: `{}` (no parameters required)
- **Output**:
  ```json
  [
    {
      "id": number,
      "summaryText": "string",
      "generatedAt": "string (ISO timestamp)"
    }
  ]
  ```

### 6. **mark_summary_delivered**
- **Purpose**: Mark a specific summary as delivered
- **Input**:
  ```json
  {
    "id": number (required)
  }
  ```
- **Output**: `{"success": true}`
- **AI Behavior**: LLMs will query undelivered summaries first, then intelligently select which to mark

## SSE Streaming Endpoint

### GET `/api/summaries/stream`
- **Type**: Server-Sent Events (text/event-stream)
- **Behavior**:
  1. On connection, sends all undelivered summaries
  2. Marks each summary as delivered after sending
  3. Sends completion event when done
  4. Sends heartbeat every 30 seconds to keep connection alive

**Event Types**:
- `summary` - Contains summary data as JSON
- `complete` - Indicates all summaries have been delivered
- `ping` - Heartbeat to keep connection alive
- `error` - Error information if something fails

**Example Event**:
```
event: summary
id: 1
data: {"id": 1, "summaryText": "Completed 3 tasks today...", "generatedAt": "2025-12-17T22:00:00Z"}

event: complete
data: {"delivered": 3}

event: ping
data: heartbeat
```

## React Application Requirements

### Core Features to Implement

#### 1. Task Management UI
- Display list of pending tasks (call `get_pending_tasks`)
- Add new tasks manually or via AI assistant
- Mark tasks as complete (call `complete_task`)
- Show task details (title, description, created timestamp)
- Real-time updates when tasks are added/completed

#### 2. AI Assistant Integration
- Connect to MCP server via SSE
- Allow users to interact with AI via natural language
- AI automatically:
  - Creates tasks from conversation context
  - Completes tasks based on discussion
  - Generates task summaries
- Display AI tool usage transparently to user

#### 3. Summary Streaming
- Connect to SSE endpoint (`/api/summaries/stream`)
- Display incoming summaries in real-time
- Show notification when new summaries arrive
- Keep connection alive and handle reconnection
- Display heartbeat status (optional)

#### 4. User Experience
- Show loading states during API calls
- Error handling with user-friendly messages
- Toast notifications for task actions
- Responsive design (mobile-friendly)
- Dark mode support (optional)

### Technical Stack Recommendations

#### State Management
- **React Context** or **Zustand** for global state
- **TanStack Query** (React Query) for server state management
- **EventSource API** for SSE connections

#### UI Components
- **Shadcn/ui** or **Chakra UI** for component library
- **Framer Motion** for animations (optional)
- **Lucide React** for icons

#### MCP Integration
- Use `@modelcontextprotocol/sdk` for MCP client
- Handle SSE connections with proper reconnection logic
- Implement proper error boundaries

### Example Architecture

```
src/
├── components/
│   ├── TaskList.tsx           # Display pending tasks
│   ├── TaskItem.tsx           # Individual task component
│   ├── AddTaskForm.tsx        # Manual task creation
│   ├── AIAssistant.tsx        # Chat interface with AI
│   ├── SummaryStream.tsx      # SSE summary display
│   └── TaskSummaries.tsx      # List of summaries
├── hooks/
│   ├── useTasks.ts            # Task management logic
│   ├── useMCP.ts              # MCP client connection
│   ├── useSSE.ts              # SSE connection management
│   └── useSummaries.ts        # Summary operations
├── services/
│   ├── mcpClient.ts           # MCP protocol client
│   ├── sseClient.ts           # SSE connection handler
│   └── api.ts                 # HTTP API calls
├── types/
│   ├── task.ts                # Task type definitions
│   └── summary.ts             # Summary type definitions
└── utils/
    ├── formatDate.ts          # Date formatting utilities
    └── errorHandler.ts        # Error handling utilities
```

### API Integration Examples

#### Fetching Pending Tasks
```typescript
async function getPendingTasks(): Promise<Task[]> {
  const response = await fetch('http://localhost:8080/mcp/messages', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      jsonrpc: '2.0',
      method: 'tools/call',
      params: {
        name: 'get_pending_tasks',
        arguments: {}
      },
      id: 1
    })
  });

  const result = await response.json();
  return JSON.parse(result.result.content[0].text);
}
```

#### SSE Connection
```typescript
function connectToSummaryStream(onSummary: (summary: Summary) => void) {
  const eventSource = new EventSource('http://localhost:8080/api/summaries/stream');

  eventSource.addEventListener('summary', (event) => {
    const summary = JSON.parse(event.data);
    onSummary(summary);
  });

  eventSource.addEventListener('complete', (event) => {
    console.log('All summaries delivered:', event.data);
  });

  eventSource.addEventListener('ping', () => {
    console.log('Heartbeat received');
  });

  eventSource.onerror = (error) => {
    console.error('SSE error:', error);
    eventSource.close();
    // Implement reconnection logic
  };

  return () => eventSource.close();
}
```

## Key Implementation Considerations

### 1. MCP Protocol
- The server uses MCP protocol over SSE for AI tool integration
- Messages follow JSON-RPC 2.0 format
- Tools are designed to be LLM-friendly with contextual descriptions
- Handle both AI-initiated and user-initiated tool calls

### 2. Real-time Updates
- Implement optimistic updates for better UX
- Use SSE for streaming summaries (automatic push)
- Poll or use websockets for task list updates (if needed)
- Handle connection drops gracefully

### 3. Error Handling
- Network errors (connection failures)
- Tool call errors (invalid parameters, not found)
- SSE disconnections (implement auto-reconnect)
- Display user-friendly error messages

### 4. Performance
- Debounce user input for task creation
- Virtual scrolling for large task lists
- Lazy load old tasks/summaries
- Cache frequently accessed data

### 5. Accessibility
- Keyboard navigation for task list
- Screen reader support for notifications
- Focus management for modals/dialogs
- ARIA labels for interactive elements

## Database Schema (For Reference)

### Tasks Table
```sql
CREATE TABLE tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title VARCHAR(500) NOT NULL,
  description TEXT,
  status VARCHAR(20) DEFAULT 'pending', -- 'pending' or 'completed'
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### Task Summaries Table
```sql
CREATE TABLE task_summaries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  summary_text TEXT NOT NULL,
  generated_at TIMESTAMP NOT NULL,
  delivered BOOLEAN DEFAULT false,
  delivered_at TIMESTAMP
);
```

## Testing Requirements

### Unit Tests
- Test task management hooks
- Test MCP client message formatting
- Test SSE connection handling
- Test date formatting utilities

### Integration Tests
- Test task CRUD operations end-to-end
- Test SSE connection and reconnection
- Test AI assistant tool calls
- Test error handling flows

### E2E Tests
- Complete user journey: add task → complete task → view summary
- AI assistant conversation flow
- SSE summary streaming
- Error recovery scenarios

## Development Workflow

1. **Setup**
   - Start MCP server: `cd ai-challenge-server && ./gradlew :mcp-app:run`
   - Start React app: `cd react-app && npm run dev`
   - Verify CORS allows localhost:5173

2. **Development**
   - Implement task list view first (get_pending_tasks)
   - Add task creation (add_task)
   - Add task completion (complete_task)
   - Integrate AI assistant with MCP
   - Add summary streaming (SSE)

3. **Testing**
   - Test manually with UI
   - Test with AI assistant (Claude Code)
   - Verify SSE connection stability
   - Check all error cases

4. **Deployment**
   - Build React app for production
   - Configure production CORS on server
   - Set up proper environment variables
   - Deploy both server and client

## Example User Flows

### Flow 1: Manual Task Management
1. User opens app
2. Sees list of pending tasks (initially empty)
3. Clicks "Add Task" button
4. Enters task details manually
5. Task appears in the list
6. Clicks "Complete" on a task
7. Task is marked as complete and removed from pending list

### Flow 2: AI-Assisted Task Management
1. User opens AI assistant chat
2. Types: "I just finished implementing the login feature"
3. AI automatically:
   - Creates task: "Implement login feature"
   - Completes the task immediately
   - Generates summary: "Completed login feature implementation..."
4. User sees task in completed list
5. Summary appears in summary stream

### Flow 3: Summary Streaming
1. User opens "Summaries" tab
2. Connects to SSE endpoint automatically
3. Receives 3 undelivered summaries immediately
4. Sees "3 summaries delivered" notification
5. Connection stays alive with heartbeat pings
6. New summaries appear in real-time as AI generates them

## Notes for AI Development

When an LLM (like Claude) is building this React app, it should:
- Use modern React patterns (hooks, functional components)
- Implement proper TypeScript types for all data
- Handle loading and error states comprehensively
- Make the UI intuitive and responsive
- Add proper animations for better UX
- Implement proper SSE reconnection logic
- Test thoroughly with the actual MCP server
- Follow accessibility best practices
- Use React Query for server state management
- Implement optimistic updates where appropriate

## Getting Started

1. Clone/create React project with Vite + TypeScript
2. Install dependencies: MCP SDK, React Query, date-fns, etc.
3. Set up API client for MCP and SSE
4. Create type definitions from server schema
5. Build UI components incrementally
6. Test with running MCP server
7. Add AI assistant integration
8. Implement SSE streaming
9. Polish UI/UX
10. Add comprehensive error handling

## Support & Documentation

- MCP Protocol: https://modelcontextprotocol.io/
- Server Code: `/home/khan/Projects/ai-challenge-server/`
- Database: SQLite at `./tasks.db`
- Tool Descriptions: See MCPRouting.kt for LLM-guiding prompts

---

**Important**: The MCP tools are designed to be LLM-friendly. When an AI assistant uses these tools, it will intelligently generate task titles, select tasks to complete, and create meaningful summaries based on conversation context. Your React UI should reflect this autonomous behavior while also allowing manual interaction.