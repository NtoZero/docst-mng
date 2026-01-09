#!/bin/bash
# Pre-commit lint and format hook for Docst project
# Runs after Edit/Write operations to check code quality

set -e

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# Read JSON input from stdin
INPUT=$(cat)

# Extract file path from tool input
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE_PATH" ]; then
    exit 0
fi

# Get relative path from project root
REL_PATH="${FILE_PATH#$PROJECT_DIR/}"

# Check file extension and run appropriate linter
case "$FILE_PATH" in
    *.java)
        echo "Running Java checkstyle..."
        cd "$PROJECT_DIR/backend"
        if ./gradlew checkstyleMain --quiet 2>/dev/null; then
            echo "Java lint: PASSED"
        else
            echo "Java lint: Issues found (non-blocking)" >&2
        fi
        ;;
    *.ts|*.tsx)
        echo "Running TypeScript lint..."
        cd "$PROJECT_DIR/frontend"
        if npm run lint --silent 2>/dev/null; then
            echo "TypeScript lint: PASSED"
        else
            echo "TypeScript lint: Issues found (non-blocking)" >&2
        fi
        ;;
    *)
        # No lint check for other file types
        exit 0
        ;;
esac

exit 0
