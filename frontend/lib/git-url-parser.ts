/**
 * Git URL Parser
 *
 * Parses various Git URL formats and extracts owner, repository name, and provider information.
 *
 * Supported formats:
 * - GitHub HTTPS: https://github.com/{owner}/{repo}.git
 * - GitHub HTTPS (no .git): https://github.com/{owner}/{repo}
 * - GitHub SSH: git@github.com:{owner}/{repo}.git
 * - Local Path: file:///path/to/repo, /absolute/path, C:\absolute\path
 */

export type GitProvider = 'GITHUB' | 'LOCAL';

export interface ParsedGitUrl {
  provider?: GitProvider;
  owner?: string;
  name?: string;
  cloneUrl?: string;
  isValid: boolean;
  error?: string;
}

/**
 * Parse a Git URL and extract repository information
 *
 * @param url - Git URL or local path
 * @returns Parsed repository information
 */
export function parseGitUrl(url: string): ParsedGitUrl {
  const trimmedUrl = url.trim();

  if (!trimmedUrl) {
    return {
      isValid: false,
      error: 'URL is required'
    };
  }

  // GitHub HTTPS pattern: https://github.com/{owner}/{repo}.git or https://github.com/{owner}/{repo}
  const githubHttpsMatch = trimmedUrl.match(
    /^https?:\/\/github\.com\/([^\/]+)\/([^\/\.]+)(\.git)?$/
  );
  if (githubHttpsMatch) {
    const owner = githubHttpsMatch[1];
    const name = githubHttpsMatch[2];
    return {
      provider: 'GITHUB',
      owner,
      name,
      cloneUrl: `https://github.com/${owner}/${name}.git`,
      isValid: true
    };
  }

  // GitHub SSH pattern: git@github.com:{owner}/{repo}.git
  const githubSshMatch = trimmedUrl.match(
    /^git@github\.com:([^\/]+)\/([^\/\.]+)(\.git)?$/
  );
  if (githubSshMatch) {
    const owner = githubSshMatch[1];
    const name = githubSshMatch[2];
    return {
      provider: 'GITHUB',
      owner,
      name,
      cloneUrl: `https://github.com/${owner}/${name}.git`,
      isValid: true
    };
  }

  // Local path patterns: file:///path, /absolute/path, C:\absolute\path
  if (
    trimmedUrl.startsWith('file://') ||
    trimmedUrl.startsWith('/') ||
    /^[A-Za-z]:[\\/]/.test(trimmedUrl)
  ) {
    // Remove file:// protocol if present
    const path = trimmedUrl.replace(/^file:\/\//, '');

    // Split path and extract segments
    const segments = path.split(/[\/\\]/).filter(Boolean);

    if (segments.length === 0) {
      return {
        isValid: false,
        error: 'Invalid local path'
      };
    }

    // Extract repository name (last segment, remove .git if present)
    const repoName = segments[segments.length - 1].replace(/\.git$/, '');

    // Extract owner (second-to-last segment, or 'local' as default)
    const owner = segments.length > 1 ? segments[segments.length - 2] : 'local';

    return {
      provider: 'LOCAL',
      owner,
      name: repoName,
      cloneUrl: path,
      isValid: true
    };
  }

  // Unsupported format
  return {
    isValid: false,
    error: 'Unsupported Git URL format. Please use GitHub HTTPS/SSH URL or local path.'
  };
}
