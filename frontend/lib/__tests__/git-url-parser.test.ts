import { parseGitUrl } from '../git-url-parser';

describe('parseGitUrl', () => {
  describe('GitHub HTTPS URLs', () => {
    test('parses GitHub HTTPS URL with .git', () => {
      const result = parseGitUrl('https://github.com/NtoZero/docst-mng.git');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('GITHUB');
      expect(result.owner).toBe('NtoZero');
      expect(result.name).toBe('docst-mng');
      expect(result.cloneUrl).toBe('https://github.com/NtoZero/docst-mng.git');
    });

    test('parses GitHub HTTPS URL without .git', () => {
      const result = parseGitUrl('https://github.com/facebook/react');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('GITHUB');
      expect(result.owner).toBe('facebook');
      expect(result.name).toBe('react');
      expect(result.cloneUrl).toBe('https://github.com/facebook/react.git');
    });

    test('parses GitHub HTTPS URL with http', () => {
      const result = parseGitUrl('http://github.com/vercel/next.js.git');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('GITHUB');
      expect(result.owner).toBe('vercel');
      expect(result.name).toBe('next');
    });
  });

  describe('GitHub SSH URLs', () => {
    test('parses GitHub SSH URL with .git', () => {
      const result = parseGitUrl('git@github.com:NtoZero/docst-mng.git');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('GITHUB');
      expect(result.owner).toBe('NtoZero');
      expect(result.name).toBe('docst-mng');
      expect(result.cloneUrl).toBe('https://github.com/NtoZero/docst-mng.git');
    });

    test('parses GitHub SSH URL without .git', () => {
      const result = parseGitUrl('git@github.com:facebook/react');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('GITHUB');
      expect(result.owner).toBe('facebook');
      expect(result.name).toBe('react');
    });
  });

  describe('Local paths', () => {
    test('parses Unix absolute path with file://', () => {
      const result = parseGitUrl('file:///home/user/repos/my-repo');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('LOCAL');
      expect(result.owner).toBe('repos');
      expect(result.name).toBe('my-repo');
      expect(result.cloneUrl).toBe('/home/user/repos/my-repo');
    });

    test('parses Unix absolute path without protocol', () => {
      const result = parseGitUrl('/home/user/repos/my-repo');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('LOCAL');
      expect(result.owner).toBe('repos');
      expect(result.name).toBe('my-repo');
      expect(result.cloneUrl).toBe('/home/user/repos/my-repo');
    });

    test('parses Windows absolute path', () => {
      const result = parseGitUrl('C:\\Users\\user\\repos\\my-repo');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('LOCAL');
      expect(result.owner).toBe('repos');
      expect(result.name).toBe('my-repo');
      expect(result.cloneUrl).toBe('C:\\Users\\user\\repos\\my-repo');
    });

    test('parses path with .git suffix', () => {
      const result = parseGitUrl('/home/user/repos/my-repo.git');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('LOCAL');
      expect(result.name).toBe('my-repo');
    });

    test('handles single-level path (defaults owner to "local")', () => {
      const result = parseGitUrl('/my-repo');
      expect(result.isValid).toBe(true);
      expect(result.provider).toBe('LOCAL');
      expect(result.owner).toBe('local');
      expect(result.name).toBe('my-repo');
    });
  });

  describe('Invalid URLs', () => {
    test('rejects empty URL', () => {
      const result = parseGitUrl('');
      expect(result.isValid).toBe(false);
      expect(result.error).toBe('URL is required');
    });

    test('rejects whitespace-only URL', () => {
      const result = parseGitUrl('   ');
      expect(result.isValid).toBe(false);
      expect(result.error).toBe('URL is required');
    });

    test('rejects unsupported URL format', () => {
      const result = parseGitUrl('not-a-valid-url');
      expect(result.isValid).toBe(false);
      expect(result.error).toContain('Unsupported Git URL format');
    });

    test('rejects incomplete GitHub URL', () => {
      const result = parseGitUrl('https://github.com/owner');
      expect(result.isValid).toBe(false);
    });
  });

  describe('Edge cases', () => {
    test('trims whitespace from URL', () => {
      const result = parseGitUrl('  https://github.com/owner/repo.git  ');
      expect(result.isValid).toBe(true);
      expect(result.owner).toBe('owner');
      expect(result.name).toBe('repo');
    });

    test('handles repository names with dots', () => {
      const result = parseGitUrl('https://github.com/vercel/next.js');
      expect(result.isValid).toBe(true);
      expect(result.name).toBe('next');
    });
  });
});
