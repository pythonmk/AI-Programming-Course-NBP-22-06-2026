import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';

import { ChatStreamService } from './chat-stream.service';
import { AppStateStore } from '../state/app-state.store';
import { DecisionCategory } from '../models/models';

// ── Helper: build a Response whose body is a ReadableStream from text chunks ──

function makeStreamResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
  return new Response(stream, {
    status,
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

// ── Helper: wait one macrotask tick (needed because the service reads the
//    stream asynchronously; we must let the microtask/macrotask queue drain). ──

function waitForStreamEnd(): Promise<void> {
  return new Promise<void>(resolve => setTimeout(resolve, 50));
}

describe('ChatStreamService', () => {
  let service: ChatStreamService;
  let store: AppStateStore;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(ChatStreamService);
    store = TestBed.inject(AppStateStore);

    // Seed a session ID so sendMessage has a target
    store.initFromSubmit({
      sessionId: 'session-test-123',
      decisionCategory: 'APPROVE',
      firstMessageMarkdown: 'Witaj!',
      caseSummary: {
        requestType: 'COMPLAINT',
        equipmentCategory: 'LAPTOP',
        equipmentName: 'ThinkPad',
        decisionCategory: 'APPROVE',
      },
    });

    // Spy on the global fetch — replaced per test
    fetchSpy = spyOn(window, 'fetch');
  });

  afterEach(() => {
    // Always abort any in-flight stream to avoid async leaks between tests
    service.abort();
  });

  // ── TAC-202: Incremental render ──────────────────────────────────────────────

  describe('TAC-202 — incremental token rendering', () => {
    it('should call addUserMessage with the sent text before fetching', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );
      const addUserSpy = spyOn(store, 'addUserMessage').and.callThrough();

      const p = service.sendMessage('Hej!');
      // addUserMessage must be called synchronously before fetch resolves
      expect(addUserSpy).toHaveBeenCalledWith('Hej!');
      await p;
      await waitForStreamEnd();
    });

    it('should call startAssistantMessage before streaming begins', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );
      const startSpy = spyOn(store, 'startAssistantMessage').and.callThrough();

      await service.sendMessage('Test');
      await waitForStreamEnd();

      expect(startSpy).toHaveBeenCalledTimes(1);
    });

    it('should call appendToLastAssistant for each token in the stream', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"t":"Cześć"}\n\n',
            'data:{"t":" świecie"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );
      const appendSpy = spyOn(store, 'appendToLastAssistant').and.callThrough();

      await service.sendMessage('Hej');
      await waitForStreamEnd();

      expect(appendSpy).toHaveBeenCalledWith('Cześć');
      expect(appendSpy).toHaveBeenCalledWith(' świecie');
    });

    it('should assemble tokens into growing assistant message content', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"t":"Cześć"}\n\n',
            'data:{"t":" świecie"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );

      await service.sendMessage('Hej');
      await waitForStreamEnd();

      const msgs = store.messages();
      const lastAssistant = msgs[msgs.length - 1];
      expect(lastAssistant.role).toBe('assistant');
      expect(lastAssistant.content).toBe('Cześć świecie');
    });

    it('should set isStreaming to false after [DONE]', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"t":"token"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );

      await service.sendMessage('Pytanie');
      await waitForStreamEnd();

      expect(store.isStreaming()).toBeFalse();
    });

    it('should handle a frame split across two chunks (buffering)', async () => {
      // Frame "data:{"t":"Hello"}\n\n" is deliberately split across two chunks
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"t":"Hel',        // first half of a frame — no \n\n yet
            'lo"}\n\ndata:[DONE]\n\n', // second half completes it, then DONE
          ]),
        ),
      );
      const appendSpy = spyOn(store, 'appendToLastAssistant').and.callThrough();

      await service.sendMessage('Hej');
      await waitForStreamEnd();

      expect(appendSpy).toHaveBeenCalledWith('Hello');
    });

    it('should POST to the correct session URL', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );

      await service.sendMessage('Test');
      await waitForStreamEnd();

      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/sessions/session-test-123/messages',
        jasmine.objectContaining({ method: 'POST' }),
      );
    });

    it('should send the message text as JSON body', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );

      await service.sendMessage('Moja wiadomość');
      await waitForStreamEnd();

      const [, init] = fetchSpy.calls.mostRecent().args as [string, RequestInit];
      expect(init.body).toBe(JSON.stringify({ message: 'Moja wiadomość' }));
    });

    it('should set Content-Type application/json on the POST request', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );

      await service.sendMessage('Test');
      await waitForStreamEnd();

      const [, init] = fetchSpy.calls.mostRecent().args as [string, RequestInit];
      const headers = init.headers as Record<string, string>;
      expect(headers['Content-Type']).toBe('application/json');
    });

    it('should be a no-op when sessionId is null', async () => {
      store.reset();
      fetchSpy.and.returnValue(
        Promise.resolve(makeStreamResponse(['data:[DONE]\n\n'])),
      );

      await service.sendMessage('Test');

      expect(fetchSpy).not.toHaveBeenCalled();
    });
  });

  // ── TAC-203: Abort ───────────────────────────────────────────────────────────

  describe('TAC-203 — abort on component destroy', () => {
    it('should abort the AbortController signal when abort() is called', async () => {
      // We need to capture the signal passed to fetch
      let capturedSignal: AbortSignal | undefined;
      fetchSpy.and.callFake((_url: string, init: RequestInit) => {
        capturedSignal = init.signal as AbortSignal;
        // Return a promise that never resolves — simulates in-flight request
        return new Promise<Response>(() => { /* intentionally never resolves */ });
      });

      // Start the request but don't await it — it's intentionally hung
      void service.sendMessage('Test abort');
      // Yield so the fetch spy is actually called before we abort
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      service.abort();

      expect(capturedSignal).toBeDefined();
      expect(capturedSignal!.aborted).toBeTrue();
    });

    it('should call endStreaming after aborting', async () => {
      fetchSpy.and.callFake((_url: string, init: RequestInit) => {
        const signal = init.signal as AbortSignal;
        return new Promise<Response>((_resolve, reject) => {
          signal.addEventListener('abort', () => {
            const err = new DOMException('The operation was aborted.', 'AbortError');
            reject(err);
          });
        });
      });

      const endStreamingSpy = spyOn(store, 'endStreaming').and.callThrough();

      void service.sendMessage('Test abort endStreaming');
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      service.abort();
      await waitForStreamEnd();

      expect(endStreamingSpy).toHaveBeenCalled();
    });

    it('should not surface an error when aborted (AbortError is swallowed)', async () => {
      fetchSpy.and.callFake((_url: string, init: RequestInit) => {
        const signal = init.signal as AbortSignal;
        return new Promise<Response>((_resolve, reject) => {
          signal.addEventListener('abort', () => {
            reject(new DOMException('The operation was aborted.', 'AbortError'));
          });
        });
      });

      let caughtError: unknown = undefined;
      const sendPromise = service.sendMessage('Test no-error abort');
      await new Promise<void>(resolve => setTimeout(resolve, 0));
      service.abort();

      try {
        await sendPromise;
      } catch (e) {
        caughtError = e;
      }

      expect(caughtError).toBeUndefined();
    });
  });

  // ── TAC-204: Decision badge update ──────────────────────────────────────────

  describe('TAC-204 — decision badge update', () => {
    it('should call setDecisionCategory when the stream emits a category frame', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"decisionCategory":"ESCALATE"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );
      const setCatSpy = spyOn(store, 'setDecisionCategory').and.callThrough();

      await service.sendMessage('Pytanie o eskalację');
      await waitForStreamEnd();

      expect(setCatSpy).toHaveBeenCalledWith('ESCALATE' as DecisionCategory);
    });

    it('should update the store currentDecisionCategory to ESCALATE', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"decisionCategory":"ESCALATE"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );

      await service.sendMessage('Pytanie o eskalację');
      await waitForStreamEnd();

      expect(store.currentDecisionCategory()).toBe('ESCALATE');
    });

    it('should update the store currentDecisionCategory to REJECT', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"decisionCategory":"REJECT"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );

      await service.sendMessage('Pytanie o odrzucenie');
      await waitForStreamEnd();

      expect(store.currentDecisionCategory()).toBe('REJECT');
    });

    it('should handle both a token and a category frame in the same stream', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          makeStreamResponse([
            'data:{"t":"Analiza zakończona."}\n\n',
            'data:{"decisionCategory":"APPROVE"}\n\n',
            'data:[DONE]\n\n',
          ]),
        ),
      );
      const appendSpy = spyOn(store, 'appendToLastAssistant').and.callThrough();
      const setCatSpy = spyOn(store, 'setDecisionCategory').and.callThrough();

      await service.sendMessage('Pełny strumień');
      await waitForStreamEnd();

      expect(appendSpy).toHaveBeenCalledWith('Analiza zakończona.');
      expect(setCatSpy).toHaveBeenCalledWith('APPROVE' as DecisionCategory);
    });
  });

  // ── Error handling ───────────────────────────────────────────────────────────

  describe('error handling', () => {
    it('should call endStreaming when fetch rejects with a non-abort error', async () => {
      fetchSpy.and.returnValue(Promise.reject(new TypeError('Failed to fetch')));
      const endStreamingSpy = spyOn(store, 'endStreaming').and.callThrough();

      try {
        await service.sendMessage('Błąd sieci');
      } catch {
        // expected rejection
      }
      await waitForStreamEnd();

      expect(endStreamingSpy).toHaveBeenCalled();
    });

    it('should reject the returned promise when fetch rejects with a network error', async () => {
      fetchSpy.and.returnValue(Promise.reject(new TypeError('Failed to fetch')));

      let caught: unknown = undefined;
      try {
        await service.sendMessage('Błąd sieci');
      } catch (e) {
        caught = e;
      }

      expect(caught).toBeDefined();
    });

    it('should call endStreaming when the response is not ok (e.g. 503)', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          new Response(null, { status: 503, statusText: 'Service Unavailable' }),
        ),
      );
      const endStreamingSpy = spyOn(store, 'endStreaming').and.callThrough();

      try {
        await service.sendMessage('Błąd serwera');
      } catch {
        // expected rejection
      }
      await waitForStreamEnd();

      expect(endStreamingSpy).toHaveBeenCalled();
    });

    it('should reject the returned promise when the response status is not ok', async () => {
      fetchSpy.and.returnValue(
        Promise.resolve(
          new Response(null, { status: 503, statusText: 'Service Unavailable' }),
        ),
      );

      let caught: unknown = undefined;
      try {
        await service.sendMessage('Błąd serwera');
      } catch (e) {
        caught = e;
      }

      expect(caught).toBeDefined();
    });

    it('should set isStreaming to false after a fetch error', async () => {
      fetchSpy.and.returnValue(Promise.reject(new TypeError('Failed to fetch')));

      try {
        await service.sendMessage('Błąd sieci');
      } catch {
        // expected
      }
      await waitForStreamEnd();

      expect(store.isStreaming()).toBeFalse();
    });
  });
});
