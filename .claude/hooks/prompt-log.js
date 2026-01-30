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
    const timestamp = new Date().toISOString();
    const sessionId = input.session_id || 'unknown';

    if (hookEvent === 'UserPromptSubmit') {
      const prompt = input.prompt || '';

      // Skip empty prompts
      if (!prompt.trim()) {
        process.exit(0);
      }

      writeLog({
        timestamp,
        session_id: sessionId,
        type: 'user_prompt',
        prompt,
        prompt_length: prompt.length,
        is_slash_command: prompt.startsWith('/')
      });

      console.log(`[PROMPT] ${truncate(prompt)}`);

    } else if (hookEvent === 'Stop') {
      const result = input.result || '';

      if (!result.trim()) {
        process.exit(0);
      }

      writeLog({
        timestamp,
        session_id: sessionId,
        type: 'llm_response',
        response: result,
        response_length: result.length
      });

      console.log(`[RESPONSE] ${truncate(result)}`);

    } else {
      process.exit(0);
    }

    process.exit(0);
  } catch (err) {
    logError(err, 'main()');
    console.error(`[Hook Error] ${err.message}`);
    process.exit(1);
  }
}

main();
