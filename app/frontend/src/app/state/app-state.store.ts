import { Injectable, signal, Signal } from '@angular/core';
import {
  CaseSummary,
  DecisionCategory,
  Message,
  SubmitResult,
} from '../models/models';

/**
 * Central state store for the Hardware Service Decision Copilot.
 *
 * Holds session state shared by IntakeFormComponent and ChatComponent:
 * - Session identity (sessionId)
 * - Case metadata (caseSummary, currentDecisionCategory)
 * - Chat message history (messages)
 * - SSE streaming flag (isStreaming)
 *
 * All mutable state is held in private WritableSignals; public API exposes
 * readonly Signal<T> via .asReadonly() so consumers cannot mutate state
 * directly.
 */
@Injectable({ providedIn: 'root' })
export class AppStateStore {
  // ── Private writable signals ──────────────────────────────────────────────

  private readonly _sessionId = signal<string | null>(null);
  private readonly _caseSummary = signal<CaseSummary | null>(null);
  private readonly _currentDecisionCategory = signal<DecisionCategory | null>(null);
  private readonly _messages = signal<Message[]>([]);
  private readonly _isStreaming = signal<boolean>(false);

  // ── Public readonly signals ───────────────────────────────────────────────

  /** Current session ID, or null before first submission. */
  readonly sessionId: Signal<string | null> = this._sessionId.asReadonly();

  /** Case summary from the last submission, or null. */
  readonly caseSummary: Signal<CaseSummary | null> = this._caseSummary.asReadonly();

  /**
   * Current decision category shown in the badge.
   * May be updated mid-conversation via a stream state frame.
   */
  readonly currentDecisionCategory: Signal<DecisionCategory | null> =
    this._currentDecisionCategory.asReadonly();

  /** Full ordered chat message history. */
  readonly messages: Signal<Message[]> = this._messages.asReadonly();

  /** True while an SSE stream is in progress. */
  readonly isStreaming: Signal<boolean> = this._isStreaming.asReadonly();

  // ── Mutation methods ──────────────────────────────────────────────────────

  /**
   * Seeds the store after a successful case submission.
   * Sets session identity, case summary, initial decision category,
   * and places the first assistant message bubble from the backend.
   */
  initFromSubmit(result: SubmitResult): void {
    this._sessionId.set(result.sessionId);
    this._caseSummary.set(result.caseSummary);
    this._currentDecisionCategory.set(result.decisionCategory);
    this._messages.set([
      { role: 'assistant', content: result.firstMessageMarkdown },
    ]);
    this._isStreaming.set(false);
  }

  /** Appends a user message to the chat history. */
  addUserMessage(content: string): void {
    this._messages.update(msgs => [...msgs, { role: 'user', content }]);
  }

  /**
   * Pushes an empty assistant message placeholder and marks the stream
   * as active. Tokens are then appended via appendToLastAssistant().
   */
  startAssistantMessage(): void {
    this._messages.update(msgs => [...msgs, { role: 'assistant', content: '' }]);
    this._isStreaming.set(true);
  }

  /**
   * Appends a text token to the last assistant message.
   * Produces a new array reference (and a new message object) so the signal
   * change is detectable by Angular's reactive graph.
   */
  appendToLastAssistant(token: string): void {
    this._messages.update(msgs => {
      const lastIndex = msgs.length - 1;
      if (lastIndex < 0) {
        return msgs;
      }
      const last = msgs[lastIndex];
      // Build a new array with a new message object at the tail — do NOT
      // mutate existing objects so previous snapshot references stay stable.
      return [
        ...msgs.slice(0, lastIndex),
        { ...last, content: last.content + token },
      ];
    });
  }

  /** Marks the current SSE stream as finished. */
  endStreaming(): void {
    this._isStreaming.set(false);
  }

  /**
   * Updates the decision badge category.
   * Also keeps caseSummary.decisionCategory in sync if a summary is present,
   * so both the header badge and the case detail always agree.
   */
  setDecisionCategory(category: DecisionCategory): void {
    this._currentDecisionCategory.set(category);
    const summary = this._caseSummary();
    if (summary !== null) {
      this._caseSummary.set({ ...summary, decisionCategory: category });
    }
  }

  /** Resets all state to the initial (pre-submission) values. */
  reset(): void {
    this._sessionId.set(null);
    this._caseSummary.set(null);
    this._currentDecisionCategory.set(null);
    this._messages.set([]);
    this._isStreaming.set(false);
  }
}
