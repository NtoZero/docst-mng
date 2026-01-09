---
name: branch-create
description: Create Git branches following project naming conventions. Use when user wants to create a new feature, fix, refactor, or other type of branch.
---

# Git Branch Creator

Create Git branches with consistent naming conventions.

## Usage

```
/branch-create <type> <description>
```

## Branch Types

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat/add-dark-mode` |
| `fix` | Bug fix | `fix/login-redirect` |
| `refactor` | Code refactoring | `refactor/auth-service` |
| `docs` | Documentation | `docs/api-guide` |
| `test` | Test additions | `test/search-service` |
| `chore` | Build/config changes | `chore/update-deps` |

## Workflow

When this skill is invoked:

1. **Parse arguments** - Extract type and description from user input
2. **Validate type** - Ensure it's one of the allowed types
3. **Format branch name** - Convert description to kebab-case
4. **Check current status** - Ensure working directory is clean
5. **Create and checkout branch** - Use git commands

### Branch Creation Commands

```bash
# 1. Check for uncommitted changes
git status --porcelain

# 2. If changes exist, warn user (optional: stash)
# git stash

# 3. Create and checkout new branch
git checkout -b <type>/<description>

# 4. Confirm branch creation
git branch --show-current
```

### Name Formatting Rules

- Convert spaces to hyphens: "add dark mode" -> "add-dark-mode"
- Convert to lowercase: "Add-Feature" -> "add-feature"
- Remove special characters
- Limit length to 50 characters

## Output Format

```markdown
## Branch Created

- Branch: `feat/add-dark-mode`
- Based on: `main`
- Status: Ready for development

Next steps:
1. Make your changes
2. Commit with: `git commit -m "feat: add dark mode support"`
3. Push with: `git push -u origin feat/add-dark-mode`
```

## Error Handling

### Uncommitted Changes
```markdown
## Warning: Uncommitted Changes

You have uncommitted changes in your working directory.

Options:
1. Commit your changes first
2. Stash changes: `git stash`
3. Discard changes: `git checkout -- .`
```

### Invalid Type
```markdown
## Error: Invalid Branch Type

`feature` is not a valid branch type.

Valid types: feat, fix, refactor, docs, test, chore
```

## Example Interactions

**User**: `/branch-create feat add-dark-mode`
**Action**: `git checkout -b feat/add-dark-mode`

**User**: `/branch-create fix login redirect issue`
**Action**: `git checkout -b fix/login-redirect-issue`

**User**: "Create a branch for the new search feature"
**Action**: Ask for branch type, then create `feat/new-search-feature`

**User**: "I need to fix the authentication bug"
**Action**: `git checkout -b fix/authentication-bug`

## Notes

- Always create branches from the latest `main` or `develop`
- Use descriptive names that explain the change
- Follow conventional commit conventions for commits
- Branch names should be URL-safe (no special characters)
