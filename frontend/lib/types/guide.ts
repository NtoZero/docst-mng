/**
 * Guide System Types (Phase 15)
 *
 * Types for the user guide and onboarding system.
 */

// Guide key - maps to credential types and standalone guides
export type GuideKey =
  | 'github_pat'
  | 'openai_api_key'
  | 'anthropic_api_key'
  | 'neo4j_auth'
  | 'pgvector_auth'
  | 'quick_start';

// Popover content (displayed in HelpPopover)
export interface GuidePopoverContent {
  title: string;
  summary: string;
  quickSteps: string[];
  detailButtonText: string;
}

// Individual guide step
export interface GuideStep {
  id: string;
  title: string;
  description: string;
  imageUrl?: string;
  imageAlt?: string;
  permissions?: Array<{ scope: string; description: string }>;
  tip?: string;
  warning?: string;
  code?: string;
}

// Full detailed guide (loaded from JSON)
export interface DetailedGuide {
  id: string;
  title: string;
  description: string;
  steps: GuideStep[];
  externalLinks?: Array<{ text: string; url: string }>;
}

// Onboarding step configuration
export interface OnboardingStep {
  title: string;
  description: string;
  icon?: string;
}
