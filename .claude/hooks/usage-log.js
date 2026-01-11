#!/usr/bin/env node
/**
 * Unified usage logging hook for Claude Code
 * Logs Skill, Task (Agent), and Slash Command usage
 * Cross-platform: Works on Windows, macOS, and Linux
 */

const fs = require('fs');
const path = require('path');

// Use __dirname to get script location, then navigate to project root
// __dirname = /project/.claude/hooks → projectDir = /project
const projectDir = path.resolve(__dirname, '..', '..');
const logDir = path.join(projectDir, '.claude', 'logs');

// Ensure log directory exists
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

// Get today's log file
function getLogFile() {
  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  return path.join(logDir, `${today}-usage.jsonl`);
}

// Append log entry
function writeLog(entry) {
  const logFile = getLogFile();
  const line = JSON.stringify(entry) + '\n';
  fs.appendFileSync(logFile, line, 'utf-8');
}

// Read stdin JSON
function readInput() {
  try {
    return JSON.parse(fs.readFileSync(0, 'utf-8'));
  } catch (e) {
    return null;
  }
}

// Debug logging
function logError(err, context = '') {
  const errorLog = path.join(logDir, 'hook-errors.log');
  const entry = `[${new Date().toISOString()}] ${context}: ${err.message}\n${err.stack}\n\n`;
  fs.appendFileSync(errorLog, entry, 'utf-8');
}

// Main
function main() {
  try {
    const input = readInput();
    if (!input) {
      process.exit(0);
    }

  const timestamp = new Date().toISOString();
  const sessionId = input.session_id || 'unknown';
  const hookEvent = input.hook_event_name;

  // Handle PostToolUse (Skill, Task)
  if (hookEvent === 'PostToolUse') {
    const toolName = input.tool_name;
    const toolInput = input.tool_input || {};

    if (toolName === 'Skill') {
      writeLog({
        timestamp,
        session_id: sessionId,
        event: 'skill',
        name: toolInput.skill || '',
        args: toolInput.args || ''
      });
      console.log(`[LOG] Skill: ${toolInput.skill}`);
    } else if (toolName === 'Task') {
      writeLog({
        timestamp,
        session_id: sessionId,
        event: 'task',
        agent: toolInput.subagent_type || '',
        description: toolInput.description || ''
      });
      console.log(`[LOG] Task: ${toolInput.subagent_type}`);
    }
  }

  // Handle UserPromptSubmit (Slash Command)
  if (hookEvent === 'UserPromptSubmit') {
    const prompt = input.prompt || '';

    // Check if it's a slash command
    if (prompt.startsWith('/')) {
      const parts = prompt.split(/\s+/);
      const command = parts[0];
      const args = parts.slice(1).join(' ');

      writeLog({
        timestamp,
        session_id: sessionId,
        event: 'slash_command',
        command,
        args
      });
      console.log(`[LOG] Slash: ${command}`);
    }
  }

    process.exit(0);
  } catch (err) {
    logError(err, 'main()');
    console.error(`[Hook Error] ${err.message}`);
    process.exit(1);
  }
}

main();
