/**
 * Domain models for the Hardware Service Decision Copilot frontend.
 * All user-facing labels are in Polish.
 */

/** Type of the customer request. */
export type RequestType = 'COMPLAINT' | 'RETURN';

/** AI decision category for the submitted case. */
export type DecisionCategory = 'APPROVE' | 'REJECT' | 'ESCALATE';

/**
 * Equipment category value as returned from the backend at runtime.
 * Kept as a loose string because categories are driven by backend metadata.
 */
export type EquipmentCategoryValue = string;

/** A single select-option with a Polish display label. */
export interface Option {
  value: string;
  labelPl: string;
}

/** Form options fetched from GET /api/meta/form-options. */
export interface FormOptions {
  requestTypes: Option[];
  equipmentCategories: Option[];
}

/** Summary of the submitted case, held in AppStateStore. */
export interface CaseSummary {
  requestType: RequestType;
  equipmentCategory: string;
  equipmentName: string;
  decisionCategory: DecisionCategory;
}

/**
 * Response from POST /api/cases.
 * Seeds the AppStateStore after a successful form submission.
 */
export interface SubmitResult {
  sessionId: string;
  decisionCategory: DecisionCategory;
  firstMessageMarkdown: string;
  caseSummary: CaseSummary;
}

/** A single message in the chat history. Assistant content is markdown. */
export interface Message {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

/**
 * Polish display labels for the decision badge.
 * Keyed by DecisionCategory.
 */
export const DECISION_LABELS_PL: Record<DecisionCategory, string> = {
  APPROVE: 'Zatwierdzono wstępnie',
  REJECT: 'Odrzucono',
  ESCALATE: 'Przekazanie do konsultanta',
};
