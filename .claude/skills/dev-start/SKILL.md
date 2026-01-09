---
name: dev-start
description: Start the Docst development environment. Use when user wants to start local development servers, Docker infrastructure, or the full development stack.
---

# Development Environment Starter

Start the Docst development environment with configurable components.

## Usage

```
/dev-start [mode]
```

## Modes

| Mode | Description |
|------|-------------|
| `--full` (default) | Start infrastructure + backend + frontend |
| `--backend-only` | Start backend server only (assumes infra is running) |
| `--frontend-only` | Start frontend dev server only |
| `--infra-only` | Start Docker infrastructure only (PostgreSQL, Neo4j, Ollama) |

## Workflow

When this skill is invoked:

1. **Parse the mode** from user input (default: `--full`)
2. **Execute the appropriate startup sequence** based on mode:

### Full Mode (`--full`)

Execute these commands in sequence:

```bash
# 1. Start Docker infrastructure
cd "$CLAUDE_PROJECT_DIR"
docker-compose up -d postgres neo4j ollama

# 2. Wait for database to be ready (10 seconds)
sleep 10

# 3. Start Backend (background)
cd "$CLAUDE_PROJECT_DIR/backend"
./gradlew bootRun &

# 4. Start Frontend (background)
cd "$CLAUDE_PROJECT_DIR/frontend"
npm run dev &

# 5. Health check (after 15 seconds)
sleep 15
curl -s http://localhost:8342/actuator/health || echo "Backend not ready yet"
curl -s http://localhost:3002 > /dev/null && echo "Frontend ready" || echo "Frontend not ready yet"
```

### Backend Only (`--backend-only`)

```bash
cd "$CLAUDE_PROJECT_DIR/backend"
./gradlew bootRun
```

### Frontend Only (`--frontend-only`)

```bash
cd "$CLAUDE_PROJECT_DIR/frontend"
npm run dev
```

### Infrastructure Only (`--infra-only`)

```bash
cd "$CLAUDE_PROJECT_DIR"
docker-compose up -d postgres neo4j ollama
docker-compose ps
```

## Output

After execution, report:
- Which services were started
- Access URLs:
  - Backend API: http://localhost:8342
  - Frontend: http://localhost:3002
  - PostgreSQL: localhost:5434
- Health check status (for `--full` mode)

## Example Interactions

**User**: `/dev-start`
**Action**: Run full startup sequence, report status

**User**: `/dev-start --backend-only`
**Action**: Start only the Spring Boot backend

**User**: "Start the dev environment"
**Action**: Interpret as `/dev-start --full`

## Notes

- Ensure Docker is running before using `--full` or `--infra-only`
- Backend requires ~30 seconds to fully initialize
- Frontend hot-reload is enabled in dev mode
- Use `docker-compose logs -f [service]` to view service logs
