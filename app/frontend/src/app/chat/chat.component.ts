import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MarkdownComponent } from 'ngx-markdown';

import { AppStateStore } from '../state/app-state.store';
import { ChatStreamService } from '../services/chat-stream.service';
import {
  DECISION_LABELS_PL,
  DecisionCategory,
  Message,
} from '../models/models';

/** Map decision category to a CSS modifier class for the badge. */
const DECISION_BADGE_CLASS: Record<DecisionCategory, string> = {
  APPROVE: 'badge--approve',
  REJECT: 'badge--reject',
  ESCALATE: 'badge--escalate',
};

@Component({
  selector: 'app-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MarkdownComponent,
  ],
  template: `
    <!-- ── Header ──────────────────────────────────────────────────────── -->
    <header class="chat-header">
      <div class="chat-header__case-info">
        @if (store.caseSummary(); as summary) {
          <span class="chat-header__equipment">{{ summary.equipmentName }}</span>
          <span class="chat-header__category">{{ summary.equipmentCategory }}</span>
        }
      </div>

      @if (store.currentDecisionCategory(); as category) {
        <span
          class="decision-badge {{ decisionBadgeClass() }}"
          data-testid="decision-badge"
        >
          {{ decisionLabel() }}
        </span>
      }

      <button
        mat-stroked-button
        class="chat-header__new-case-btn"
        data-testid="new-case-btn"
        (click)="onNewCase()"
      >
        Nowa sprawa
      </button>
    </header>

    <!-- ── Message list ─────────────────────────────────────────────────── -->
    <div class="message-list" #messageList>
      @for (msg of store.messages(); track $index) {
        @if (msg.role === 'user') {
          <div class="message-bubble message-bubble--user" data-testid="msg-user">
            <p class="message-bubble__text">{{ msg.content }}</p>
          </div>
        } @else if (msg.role === 'assistant') {
          <div class="message-bubble message-bubble--assistant" data-testid="msg-assistant">
            @if (isLastAssistantStreaming($index)) {
              <span class="streaming-indicator" data-testid="streaming-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </span>
            } @else {
              <markdown [data]="msg.content" />
            }
          </div>
        }
      }
    </div>

    <!-- ── Error banner ─────────────────────────────────────────────────── -->
    @if (connectionError()) {
      <div class="error-banner" role="alert">
        Błąd połączenia. Spróbuj ponownie.
      </div>
    }

    <!-- ── Input area ───────────────────────────────────────────────────── -->
    <footer class="chat-input-area">
      <mat-form-field appearance="outline" class="chat-input-area__field">
        <textarea
          matInput
          rows="2"
          placeholder="Wpisz wiadomość..."
          [value]="inputText()"
          (input)="onInputChange($event)"
          (keydown)="onKeyDown($event)"
          [disabled]="store.isStreaming()"
          class="chat-input-area__textarea"
          aria-label="Wiadomość"
        ></textarea>
      </mat-form-field>

      <button
        mat-icon-button
        color="primary"
        data-testid="send-btn"
        [disabled]="isSendDisabled()"
        (click)="onSend()"
        aria-label="Wyślij wiadomość"
      >
        <mat-icon>send</mat-icon>
      </button>
    </footer>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      overflow: hidden;
      font-family: var(--font-body);
    }

    /* ── Header ──────────────────────────────────────────────────────────── */
    .chat-header {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      background: var(--color-bg-dark);
      color: var(--color-text-on-dark);
      flex-shrink: 0;
      flex-wrap: wrap;
    }

    .chat-header__case-info {
      display: flex;
      flex-direction: column;
      flex: 1 1 auto;
      min-width: 0;
    }

    .chat-header__equipment {
      font-weight: 600;
      font-size: 15px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .chat-header__category {
      font-size: 12px;
      opacity: 0.75;
    }

    .chat-header__new-case-btn {
      flex-shrink: 0;
      color: var(--color-text-on-dark) !important;
      border-color: rgba(255, 255, 255, 0.5) !important;
    }

    /* ── Decision badge ───────────────────────────────────────────────────── */
    .decision-badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: var(--radius-full);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      flex-shrink: 0;
    }

    .badge--approve {
      background-color: #388e3c;
      color: #fff;
    }

    .badge--reject {
      background-color: #c62828;
      color: #fff;
    }

    .badge--escalate {
      background-color: #f57c00;
      color: #fff;
    }

    /* ── Message list ─────────────────────────────────────────────────────── */
    .message-list {
      flex: 1 1 auto;
      overflow-y: auto;
      padding: var(--space-4);
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }

    .message-bubble {
      max-width: 80%;
      padding: var(--space-3) var(--space-4);
      border-radius: 12px;
      word-break: break-word;
    }

    .message-bubble--user {
      align-self: flex-end;
      background-color: #152E52;
      color: #fff;
    }

    .message-bubble--user .message-bubble__text {
      margin: 0;
    }

    .message-bubble--assistant {
      align-self: flex-start;
      background-color: #f5f5f5;
      color: #212121;
    }

    /* Strip default markdown margins inside the bubble */
    .message-bubble--assistant ::ng-deep p:last-child {
      margin-bottom: 0;
    }
    .message-bubble--assistant ::ng-deep p:first-child {
      margin-top: 0;
    }

    /* ── Streaming indicator ──────────────────────────────────────────────── */
    .streaming-indicator {
      display: inline-flex;
      gap: 4px;
      align-items: center;
      padding: 4px 0;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: #888;
      animation: bounce 1.2s infinite ease-in-out;
    }

    .dot:nth-child(2) { animation-delay: 0.2s; }
    .dot:nth-child(3) { animation-delay: 0.4s; }

    @keyframes bounce {
      0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
      40% { transform: scale(1.2); opacity: 1; }
    }

    /* ── Error banner ─────────────────────────────────────────────────────── */
    .error-banner {
      background-color: #ffebee;
      color: #c62828;
      padding: var(--space-2) var(--space-4);
      font-size: 14px;
      text-align: center;
      flex-shrink: 0;
    }

    /* ── Input area ───────────────────────────────────────────────────────── */
    .chat-input-area {
      display: flex;
      align-items: flex-end;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-4) var(--space-3);
      background: var(--color-bg-default);
      border-top: 1px solid var(--color-border-default);
      flex-shrink: 0;
    }

    .chat-input-area__field {
      flex: 1 1 auto;
    }

    /* Narrow-viewport tweak */
    @media (max-width: 400px) {
      .message-bubble {
        max-width: 92%;
      }
    }
  `],
})
export class ChatComponent implements OnInit, OnDestroy {
  protected readonly store = inject(AppStateStore);
  private readonly chatStreamService = inject(ChatStreamService);
  private readonly router = inject(Router);

  @ViewChild('messageList') private readonly messageListRef?: ElementRef<HTMLElement>;

  /** Writable signal for the textarea value — exposed for tests. */
  readonly inputText = signal<string>('');

  /** True if a connection error has occurred. */
  readonly connectionError = signal<boolean>(false);

  /** Derived: current decision label in Polish. */
  readonly decisionLabel = computed<string>(() => {
    const cat = this.store.currentDecisionCategory();
    return cat ? DECISION_LABELS_PL[cat] : '';
  });

  /** Derived: CSS modifier class for the badge. */
  readonly decisionBadgeClass = computed<string>(() => {
    const cat = this.store.currentDecisionCategory();
    return cat ? DECISION_BADGE_CLASS[cat] : '';
  });

  /** Derived: true when send button must be disabled. */
  readonly isSendDisabled = computed<boolean>(
    () => this.store.isStreaming() || this.inputText().trim() === '',
  );

  constructor() {
    // Auto-scroll to bottom whenever messages array changes.
    effect(() => {
      // Read messages signal to establish dependency
      this.store.messages();
      // Scroll after the current render cycle finishes
      queueMicrotask(() => this.scrollToBottom());
    });
  }

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  ngOnInit(): void {
    if (this.store.sessionId() === null) {
      this.router.navigate(['']);
    }
  }

  ngOnDestroy(): void {
    this.chatStreamService.abort();
  }

  // ── Template helpers ───────────────────────────────────────────────────────

  /**
   * Returns true when the message at the given index is the last one,
   * it is an assistant message, and the stream is still active.
   * This drives the streaming-indicator vs. rendered markdown toggle.
   */
  isLastAssistantStreaming(index: number): boolean {
    const messages: Message[] = this.store.messages();
    return (
      this.store.isStreaming() &&
      index === messages.length - 1 &&
      messages[index]?.role === 'assistant'
    );
  }

  // ── Event handlers ─────────────────────────────────────────────────────────

  onInputChange(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.inputText.set(target.value);
  }

  onKeyDown(event: KeyboardEvent): void {
    const isCtrlOrCmd = event.ctrlKey || event.metaKey;
    if (isCtrlOrCmd && event.key === 'Enter' && !this.isSendDisabled()) {
      event.preventDefault();
      this.onSend();
    }
  }

  async onSend(): Promise<void> {
    const text = this.inputText().trim();
    if (!text || this.store.isStreaming()) {
      return;
    }

    this.inputText.set('');
    this.connectionError.set(false);

    try {
      await this.chatStreamService.sendMessage(text);
    } catch {
      this.connectionError.set(true);
    }
  }

  onNewCase(): void {
    this.store.reset();
    this.router.navigate(['']);
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private scrollToBottom(): void {
    const el = this.messageListRef?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }
}
