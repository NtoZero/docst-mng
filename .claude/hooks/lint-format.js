#!/usr/bin/env node
/**
 * Post-commit lint and format hook for Docst project
 * Runs after Edit/Write operations to check code quality
 * Cross-platform: Works on Windows, macOS, and Linux
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Read JSON input from stdin
let input;
try {
  input = JSON.parse(fs.readFileSync(0, 'utf-8'));
} catch (e) {
  process.exit(0);
}

const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const filePath = input?.tool_input?.file_path;

if (!filePath) {
  process.exit(0);
}

// Get file extension
const ext = path.extname(filePath).toLowerCase();

// Helper to run command silently
function runCommand(cmd, cwd) {
  try {
    execSync(cmd, {
      cwd,
      stdio: ['pipe', 'pipe', 'pipe'],
      encoding: 'utf-8',
      shell: true
    });
    return { success: true };
  } catch (error) {
    return { success: false, error: error.stderr || error.message };
  }
}

// Run appropriate linter based on file extension
switch (ext) {
  case '.java': {
    console.log('Running Java checkstyle...');
    const backendDir = path.join(projectDir, 'backend');

    // Check if gradlew exists
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
    const gradlewPath = path.join(backendDir, gradlew);

    if (!fs.existsSync(gradlewPath)) {
      console.log('Gradlew not found, skipping Java lint');
      break;
    }

    const result = runCommand(`${gradlew} checkstyleMain --quiet`, backendDir);
    if (result.success) {
      console.log('Java lint: PASSED');
    } else {
      console.error('Java lint: Issues found (non-blocking)');
    }
    break;
  }

  case '.ts':
  case '.tsx': {
    console.log('Running TypeScript lint...');
    const frontendDir = path.join(projectDir, 'frontend');

    // Check if package.json exists
    const packageJsonPath = path.join(frontendDir, 'package.json');
    if (!fs.existsSync(packageJsonPath)) {
      console.log('package.json not found, skipping TypeScript lint');
      break;
    }

    const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    const result = runCommand(`${npm} run lint --silent`, frontendDir);
    if (result.success) {
      console.log('TypeScript lint: PASSED');
    } else {
      console.error('TypeScript lint: Issues found (non-blocking)');
    }
    break;
  }

  default:
    // No lint check for other file types
    break;
}

process.exit(0);