import { Injectable } from '@angular/core';

import { AppStateStore } from '../state/app-state.store';
import { DecisionCategory } from '../models/models';

/**
 * Handles SSE-over-fetch streaming for the chat screen.
 *
 * Uses the native `fetch` API with `ReadableStream` (NOT `EventSource`,
 * which is GET-only) to POST the user message and incrementally read the
 * server-sent token stream.
 *
 * Wire format (per ADR-002 §5 and main ADR §6):
 *   POST /api/sessions/{id}/messages
 *   Content-Type: application/json
 *   Body: { "message": "<text>" }
 *   Response: text/event-stream
 *
 * Each SSE frame is delimited by a blank line (\n\n).
 * Payload semantics (the concatenation of data: line contents):
 *   - "[DONE]"                       → terminal sentinel; stop reading.
 *   - {"t":"<delta>"}                → token; append delta to last assistant msg.
 *   - {"decisionCategory":"<cat>"}   → state change; update decision badge.
 */
@Injectable({ providedIn: 'root' })
export class ChatStreamService {
  /** Active AbortController for the in-flight fetch; null when idle. */
  private controller: AbortController | null = null;

  constructor(private readonly store: AppStateStore) {}

  /**
   * Sends a user message and begins streaming the assistant reply.
   *
   * Steps:
   * 1. Reads sessionId from the store (no-op if null).
   * 2. Pushes a user bubble and an empty assistant placeholder, sets
   *    isStreaming = true.
   * 3. Fetches POST /api/sessions/{id}/messages with the text payload.
   * 4. Reads the response body as a ReadableStream, buffering partial frames
   *    across chunk boundaries, and updates the store per token/state events.
   * 5. Calls endStreaming() on completion, abort, or error.
   *
   * @throws {Error} when fetch rejects with a non-AbortError, or when the
   *   response status is not ok — so the caller (ChatComponent) can show a
   *   retry banner. AbortError is swallowed (treated as intentional cancel).
   */
  async sendMessage(text: string): Promise<void> {
    const sessionId = this.store.sessionId();
    if (sessionId === null) {
      return;
    }

    this.store.addUserMessage(text);
    this.store.startAssistantMessage();

    this.controller = new AbortController();
    const { signal } = this.controller;

    try {
      const response = await fetch(
        `/api/sessions/${sessionId}/messages`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: text }),
          signal,
        },
      );

      if (!response.ok) {
        throw new Error(
          `Błąd serwera: ${response.status} ${response.statusText}`,
        );
      }

      await this.readStream(response);
    } catch (err) {
      if (this.isAbortError(err)) {
        // Intentional cancel — swallow, just clean up
        this.store.endStreaming();
        return;
      }
      this.store.endStreaming();
      throw err;
    }
  }

  /**
   * Aborts the current in-flight fetch stream.
   * Intended to be called from `ngOnDestroy` to prevent dangling reads
   * (see ADR-002 §6, TAC-203).
   */
  abort(): void {
    this.controller?.abort();
    this.controller = null;
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  /**
   * Reads the response ReadableStream, buffers across chunk boundaries,
   * splits on SSE frame delimiters (\n\n), and dispatches each complete frame.
   */
  private async readStream(response: Response): Promise<void> {
    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // Split on blank-line frame boundaries; keep trailing partial frame.
        const frames = buffer.split('\n\n');
        // The last element is an incomplete (or empty) frame — keep it buffered
        buffer = frames.pop() ?? '';

        for (const frame of frames) {
          const trimmed = frame.trim();
          if (trimmed === '') {
            continue;
          }
          const stop = this.processFrame(trimmed);
          if (stop) {
            return;
          }
        }
      }

      // Flush any remaining content in the decoder
      const remaining = decoder.decode();
      if (remaining) {
        buffer += remaining;
      }

      // Process any buffered frame left after stream end
      if (buffer.trim() !== '') {
        this.processFrame(buffer.trim());
      }
    } finally {
      this.store.endStreaming();
    }
  }

  /**
   * Parses a single SSE frame (the lines between blank-line delimiters).
   *
   * Extracts the payload by stripping the `data:` prefix from each data line
   * (joining multi-line data values with `\n`), then dispatches based on
   * payload content.
   *
   * @returns true if the stream should stop ([DONE] sentinel received).
   */
  private processFrame(frame: string): boolean {
    const lines = frame.split('\n');
    const dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith('data:')) {
        // Strip the "data:" prefix (and a single optional leading space)
        dataLines.push(line.slice(5).replace(/^ /, ''));
      }
    }

    if (dataLines.length === 0) {
      return false;
    }

    const payload = dataLines.join('\n');

    if (payload === '[DONE]') {
      return true;
    }

    try {
      const parsed = JSON.parse(payload) as Record<string, unknown>;

      if (typeof parsed['t'] === 'string') {
        // Token frame
        this.store.appendToLastAssistant(parsed['t']);
      } else if (typeof parsed['decisionCategory'] === 'string') {
        // State frame
        this.store.setDecisionCategory(
          parsed['decisionCategory'] as DecisionCategory,
        );
      }
    } catch {
      // Malformed JSON — ignore and continue streaming
    }

    return false;
  }

  /** Returns true if the error is a DOM AbortError (from AbortController). */
  private isAbortError(err: unknown): boolean {
    return (
      err instanceof DOMException && err.name === 'AbortError'
    );
  }
}
