---
name: test-run
description: Run tests for the Docst project. Use when user wants to execute unit tests, integration tests, or lint checks for backend or frontend.
---

# Test Runner

Execute tests with configurable scope, target, and coverage options.

## Usage

```
/test-run [options]
```

## Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--scope` | `unit`, `integration`, `all` | `unit` | Test scope to run |
| `--target` | `backend`, `frontend`, `both` | `both` | Target project |
| `--coverage` | flag | `false` | Generate coverage report |

## Workflow

When this skill is invoked:

1. **Parse options** from user input
2. **Execute appropriate test commands** based on options
3. **Report results** with pass/fail status

### Backend Tests

#### Unit Tests
```bash
cd "$CLAUDE_PROJECT_DIR/backend"
./gradlew test --tests "*Test" -x integrationTest
```

#### Integration Tests
```bash
cd "$CLAUDE_PROJECT_DIR/backend"
./gradlew integrationTest
```

#### All Tests with Coverage
```bash
cd "$CLAUDE_PROJECT_DIR/backend"
./gradlew test jacocoTestReport
# Coverage report: build/reports/jacoco/test/html/index.html
```

### Frontend Tests

#### Lint Check
```bash
cd "$CLAUDE_PROJECT_DIR/frontend"
npm run lint
```

#### Type Check
```bash
cd "$CLAUDE_PROJECT_DIR/frontend"
npx tsc --noEmit
```

#### Unit Tests (if configured)
```bash
cd "$CLAUDE_PROJECT_DIR/frontend"
npm test
```

## Command Matrix

| Scope | Target | Commands |
|-------|--------|----------|
| unit | backend | `./gradlew test` |
| unit | frontend | `npm run lint && npx tsc --noEmit` |
| unit | both | Both of above |
| integration | backend | `./gradlew integrationTest` |
| integration | frontend | N/A (lint only) |
| all | backend | `./gradlew test integrationTest` |
| all | frontend | `npm run lint && npx tsc --noEmit && npm test` |
| all + coverage | backend | `./gradlew test jacocoTestReport` |

## Output Format

Report test results in this format:

```markdown
## Test Results

### Backend
- Unit Tests: PASSED (42 tests, 0 failures)
- Integration Tests: SKIPPED
- Coverage: 78.5%

### Frontend
- Lint: PASSED (0 errors, 2 warnings)
- Type Check: PASSED
- Unit Tests: SKIPPED
```

## Example Interactions

**User**: `/test-run`
**Action**: Run unit tests for both backend and frontend

**User**: `/test-run --scope unit --target backend`
**Action**: Run backend unit tests only

**User**: `/test-run --scope all --coverage`
**Action**: Run all tests with coverage report

**User**: "Run the integration tests"
**Action**: Run `./gradlew integrationTest`

**User**: "Check if the frontend builds"
**Action**: Run `npm run lint && npx tsc --noEmit`

## Notes

- Backend tests require PostgreSQL to be running for integration tests
- Use `--coverage` to generate JaCoCo reports for backend
- Frontend lint includes ESLint with TypeScript rules
- Type check uses strict TypeScript compiler options
