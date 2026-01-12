#!/usr/bin/env node
/**
 * Prompt logging hook for Claude Code
 * Logs all user prompts to .claude/logs/prompts/
 * Cross-platform: Works on Windows, macOS, and Linux
 */

const fs = require('fs');
const path = require('path');

// Use __dirname to get script location, then navigate to project root
const projectDir = path.resolve(__dirname, '..', '..');
const logDir = path.join(projectDir, '.claude', 'logs', 'prompts');

// Ensure log directory exists
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

// Get today's log file
function getLogFile() {
  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  return path.join(logDir, `${today}-prompts.jsonl`);
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

// Truncate long text for console output
function truncate(str, maxLen = 50) {
  if (!str || str.length <= maxLen) return str;
  return str.substring(0, maxLen) + '...';
}

// Main
function main() {
  try {
    const input = readInput();
    if (!input) {
      process.exit(0);
    }

    const hookEvent = input.hook_event_name;

    // Only handle UserPromptSubmit
    if (hookEvent !== 'UserPromptSubmit') {
      process.exit(0);
    }

    const timestamp = new Date().toISOString();
    const sessionId = input.session_id || 'unknown';
    const prompt = input.prompt || '';

    // Skip empty prompts
    if (!prompt.trim()) {
      process.exit(0);
    }

    // Log all prompts
    writeLog({
      timestamp,
      session_id: sessionId,
      prompt,
      prompt_length: prompt.length,
      is_slash_command: prompt.startsWith('/')
    });

    console.log(`[PROMPT] ${truncate(prompt)}`);

    process.exit(0);
  } catch (err) {
    logError(err, 'main()');
    console.error(`[Hook Error] ${err.message}`);
    process.exit(1);
  }
}

main();
