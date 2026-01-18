// Simple test script for Git URL parser
// Run with: node test-parser.js

const { parseGitUrl } = require('../git-url-parser.ts');

const testCases = [
  // GitHub HTTPS
  { url: 'https://github.com/NtoZero/docst-mng.git', expected: { provider: 'GITHUB', owner: 'NtoZero', name: 'docst-mng' } },
  { url: 'https://github.com/facebook/react', expected: { provider: 'GITHUB', owner: 'facebook', name: 'react' } },

  // GitHub SSH
  { url: 'git@github.com:NtoZero/docst-mng.git', expected: { provider: 'GITHUB', owner: 'NtoZero', name: 'docst-mng' } },

  // Local paths
  { url: 'file:///home/user/repos/my-repo', expected: { provider: 'LOCAL', owner: 'repos', name: 'my-repo' } },
  { url: '/home/user/repos/my-repo', expected: { provider: 'LOCAL', owner: 'repos', name: 'my-repo' } },
  { url: 'C:\\Users\\user\\repos\\my-repo', expected: { provider: 'LOCAL', owner: 'repos', name: 'my-repo' } },

  // Invalid
  { url: '', expected: { isValid: false } },
  { url: 'invalid-url', expected: { isValid: false } },
];

console.log('Testing Git URL Parser...\n');

testCases.forEach((testCase, index) => {
  const result = parseGitUrl(testCase.url);
  const passed = testCase.expected.isValid === false
    ? !result.isValid
    : result.provider === testCase.expected.provider &&
      result.owner === testCase.expected.owner &&
      result.name === testCase.expected.name;

  console.log(`Test ${index + 1}: ${passed ? '✓' : '✗'}`);
  console.log(`  URL: ${testCase.url}`);
  console.log(`  Result:`, result);
  console.log('');
});
