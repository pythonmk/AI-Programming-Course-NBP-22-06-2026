import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideMarkdown } from 'ngx-markdown';
import { provideHttpClient, withFetch } from '@angular/common/http';

import { ChatComponent } from './chat.component';
import { AppStateStore } from '../state/app-state.store';
import { ChatStreamService } from '../services/chat-stream.service';
import { DecisionCategory, Message } from '../models/models';

// ── Helpers ───────────────────────────────────────────────────────────────────

function seedStore(
  store: AppStateStore,
  overrides: {
    sessionId?: string | null;
    decisionCategory?: DecisionCategory | null;
    messages?: Message[];
    isStreaming?: boolean;
  } = {},
): void {
  const sessionId =
    overrides.sessionId !== undefined ? overrides.sessionId : 'sess-001';

  if (sessionId !== null) {
    store.initFromSubmit({
      sessionId,
      decisionCategory: overrides.decisionCategory ?? 'APPROVE',
      firstMessageMarkdown: '**Cześć!**',
      caseSummary: {
        requestType: 'COMPLAINT',
        equipmentCategory: 'LAPTOP',
        equipmentName: 'ThinkPad X1',
        decisionCategory: overrides.decisionCategory ?? 'APPROVE',
      },
    });
  }

  if (overrides.messages !== undefined) {
    // We cannot call a private setter, but we CAN repopulate via
    // addUserMessage / startAssistantMessage / appendToLastAssistant.
    // For test simplicity we rebuild via reset + initFromSubmit + helpers.
    // Already done above; additional messages added below.
    for (const msg of overrides.messages) {
      if (msg.role === 'user') {
        store.addUserMessage(msg.content);
      }
      // assistant messages are added via startAssistantMessage + append
    }
  }

  if (overrides.isStreaming) {
    store.startAssistantMessage();
  }
}

// ── Test suite ────────────────────────────────────────────────────────────────

describe('ChatComponent', () => {
  let fixture: ComponentFixture<ChatComponent>;
  let component: ChatComponent;
  let store: AppStateStore;
  let router: Router;
  let mockChatStream: jasmine.SpyObj<ChatStreamService>;

  beforeEach(async () => {
    mockChatStream = jasmine.createSpyObj<ChatStreamService>(
      'ChatStreamService',
      ['sendMessage', 'abort'],
    );
    mockChatStream.sendMessage.and.resolveTo(undefined);

    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideAnimationsAsync(),
        provideMarkdown(),
        provideHttpClient(withFetch()),
        provideRouter([
          { path: '', component: ChatComponent },
          { path: 'chat', component: ChatComponent },
        ]),
        { provide: ChatStreamService, useValue: mockChatStream },
      ],
    }).compileComponents();

    store = TestBed.inject(AppStateStore);
    router = TestBed.inject(Router);
    await router.initialNavigation();
  });

  // ── Guard: redirect when no session ────────────────────────────────────────

  describe('session guard', () => {
    it('redirects to "" when sessionId is null', async () => {
      // store is fresh → sessionId is null
      spyOn(router, 'navigate').and.resolveTo(true);

      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      expect(router.navigate).toHaveBeenCalledWith(['']);
    });

    it('does NOT redirect when sessionId is set', async () => {
      seedStore(store);
      spyOn(router, 'navigate').and.resolveTo(true);

      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      expect(router.navigate).not.toHaveBeenCalledWith(['']);
    });
  });

  // ── Decision badge ─────────────────────────────────────────────────────────

  describe('decision badge', () => {
    beforeEach(async () => {
      seedStore(store, { decisionCategory: 'APPROVE' });
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('shows "Zatwierdzono wstępnie" for APPROVE', () => {
      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.textContent?.trim()).toContain('Zatwierdzono wstępnie');
    });

    it('has approve CSS class for APPROVE', () => {
      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.classList).toContain('badge--approve');
    });

    it('shows "Odrzucono" for REJECT', async () => {
      store.setDecisionCategory('REJECT');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.textContent?.trim()).toContain('Odrzucono');
    });

    it('has reject CSS class for REJECT', async () => {
      store.setDecisionCategory('REJECT');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.classList).toContain('badge--reject');
    });

    it('shows "Przekazanie do konsultanta" for ESCALATE', async () => {
      store.setDecisionCategory('ESCALATE');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.textContent?.trim()).toContain('Przekazanie do konsultanta');
    });

    it('has escalate CSS class for ESCALATE', async () => {
      store.setDecisionCategory('ESCALATE');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('[data-testid="decision-badge"]') as HTMLElement;
      expect(badge?.classList).toContain('badge--escalate');
    });
  });

  // ── "Nowa sprawa" button ───────────────────────────────────────────────────

  describe('"Nowa sprawa" button', () => {
    beforeEach(async () => {
      seedStore(store);
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('calls store.reset() and navigates to "" when clicked', async () => {
      spyOn(store, 'reset').and.callThrough();
      spyOn(router, 'navigate').and.resolveTo(true);

      const btn = fixture.nativeElement.querySelector(
        '[data-testid="new-case-btn"]',
      ) as HTMLButtonElement;
      btn.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(store.reset).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['']);
    });
  });

  // ── Send button disabled states ────────────────────────────────────────────

  describe('send button disabled states', () => {
    beforeEach(async () => {
      seedStore(store);
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('is disabled when input is empty', () => {
      const btn = fixture.nativeElement.querySelector(
        '[data-testid="send-btn"]',
      ) as HTMLButtonElement;
      expect(btn.disabled).toBeTrue();
    });

    it('is disabled when isStreaming is true', async () => {
      store.startAssistantMessage(); // sets isStreaming = true
      component.inputText.set('Cześć');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector(
        '[data-testid="send-btn"]',
      ) as HTMLButtonElement;
      expect(btn.disabled).toBeTrue();
    });

    it('is enabled when input has text and NOT streaming', async () => {
      component.inputText.set('Pytanie testowe');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector(
        '[data-testid="send-btn"]',
      ) as HTMLButtonElement;
      expect(btn.disabled).toBeFalse();
    });
  });

  // ── Send action ────────────────────────────────────────────────────────────

  describe('send action', () => {
    beforeEach(async () => {
      seedStore(store);
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('calls chatStreamService.sendMessage with the input text on send', async () => {
      component.inputText.set('Jakie są warunki gwarancji?');
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector(
        '[data-testid="send-btn"]',
      ) as HTMLButtonElement;
      btn.click();

      await fixture.whenStable();
      fixture.detectChanges();

      expect(mockChatStream.sendMessage).toHaveBeenCalledWith(
        'Jakie są warunki gwarancji?',
      );
    });

    it('clears the input after send', async () => {
      component.inputText.set('Pytanie');
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector(
        '[data-testid="send-btn"]',
      ) as HTMLButtonElement;
      btn.click();

      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.inputText()).toBe('');
    });
  });

  // ── Message list ───────────────────────────────────────────────────────────

  describe('message list', () => {
    beforeEach(async () => {
      seedStore(store);
      store.addUserMessage('Moje pytanie');
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('renders user messages in the list', () => {
      const userBubbles = fixture.nativeElement.querySelectorAll(
        '[data-testid="msg-user"]',
      ) as NodeListOf<HTMLElement>;
      expect(userBubbles.length).toBeGreaterThan(0);
    });

    it('renders assistant messages in the list', () => {
      const assistantBubbles = fixture.nativeElement.querySelectorAll(
        '[data-testid="msg-assistant"]',
      ) as NodeListOf<HTMLElement>;
      expect(assistantBubbles.length).toBeGreaterThan(0);
    });
  });

  // ── Streaming indicator ────────────────────────────────────────────────────

  describe('streaming indicator', () => {
    it('shows streaming indicator when isStreaming is true and last msg is assistant', async () => {
      seedStore(store, { isStreaming: true });
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const indicator = fixture.nativeElement.querySelector(
        '[data-testid="streaming-indicator"]',
      ) as HTMLElement;
      expect(indicator).toBeTruthy();
    });

    it('does NOT show streaming indicator when isStreaming is false', async () => {
      seedStore(store, { isStreaming: false });
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const indicator = fixture.nativeElement.querySelector(
        '[data-testid="streaming-indicator"]',
      ) as HTMLElement;
      expect(indicator).toBeNull();
    });
  });

  // ── ngOnDestroy ────────────────────────────────────────────────────────────

  describe('ngOnDestroy', () => {
    it('calls chatStreamService.abort() on destroy', async () => {
      seedStore(store);
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      fixture.destroy();

      expect(mockChatStream.abort).toHaveBeenCalled();
    });
  });
});
