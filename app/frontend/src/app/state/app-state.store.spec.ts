import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { AppStateStore } from './app-state.store';
import { SubmitResult, DecisionCategory, Message } from '../models/models';

describe('AppStateStore', () => {
  let store: AppStateStore;

  const mockCaseSummary = {
    requestType: 'COMPLAINT' as const,
    equipmentCategory: 'LAPTOP',
    equipmentName: 'ThinkPad X1',
    decisionCategory: 'APPROVE' as DecisionCategory,
  };

  const mockSubmitResult: SubmitResult = {
    sessionId: 'session-abc-123',
    decisionCategory: 'APPROVE',
    firstMessageMarkdown: '## Decyzja\n\nWstępnie **zatwierdzono** zgłoszenie.',
    caseSummary: mockCaseSummary,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    store = TestBed.inject(AppStateStore);
  });

  describe('initial state', () => {
    it('should start with null sessionId', () => {
      expect(store.sessionId()).toBeNull();
    });

    it('should start with null caseSummary', () => {
      expect(store.caseSummary()).toBeNull();
    });

    it('should start with null currentDecisionCategory', () => {
      expect(store.currentDecisionCategory()).toBeNull();
    });

    it('should start with empty messages array', () => {
      expect(store.messages()).toEqual([]);
    });

    it('should start with isStreaming false', () => {
      expect(store.isStreaming()).toBeFalse();
    });
  });

  describe('initFromSubmit', () => {
    beforeEach(() => {
      store.initFromSubmit(mockSubmitResult);
    });

    it('should set sessionId from result', () => {
      expect(store.sessionId()).toBe('session-abc-123');
    });

    it('should set caseSummary from result', () => {
      expect(store.caseSummary()).toEqual(mockCaseSummary);
    });

    it('should set currentDecisionCategory from result', () => {
      expect(store.currentDecisionCategory()).toBe('APPROVE');
    });

    it('should seed messages with one assistant message', () => {
      const msgs = store.messages();
      expect(msgs.length).toBe(1);
    });

    it('should seed the first message with role assistant', () => {
      expect(store.messages()[0].role).toBe('assistant');
    });

    it('should seed the first assistant message with firstMessageMarkdown content', () => {
      expect(store.messages()[0].content).toBe(mockSubmitResult.firstMessageMarkdown);
    });
  });

  describe('addUserMessage', () => {
    it('should append a user message to the messages array', () => {
      store.initFromSubmit(mockSubmitResult);
      store.addUserMessage('Mam pytanie dotyczące naprawy.');
      const msgs = store.messages();
      expect(msgs.length).toBe(2);
      expect(msgs[1].role).toBe('user');
      expect(msgs[1].content).toBe('Mam pytanie dotyczące naprawy.');
    });

    it('should work on an empty messages array', () => {
      store.addUserMessage('Witaj');
      const msgs = store.messages();
      expect(msgs.length).toBe(1);
      expect(msgs[0].role).toBe('user');
    });
  });

  describe('startAssistantMessage', () => {
    it('should push an empty assistant message', () => {
      store.startAssistantMessage();
      const msgs = store.messages();
      expect(msgs.length).toBe(1);
      expect(msgs[0].role).toBe('assistant');
      expect(msgs[0].content).toBe('');
    });

    it('should set isStreaming to true', () => {
      store.startAssistantMessage();
      expect(store.isStreaming()).toBeTrue();
    });
  });

  describe('appendToLastAssistant', () => {
    it('should append a token to the last assistant message content', () => {
      store.startAssistantMessage();
      store.appendToLastAssistant('Cześć');
      store.appendToLastAssistant(', jak mogę pomóc?');
      expect(store.messages()[0].content).toBe('Cześć, jak mogę pomóc?');
    });

    it('should produce a NEW array reference on each append (signal update)', () => {
      store.startAssistantMessage();
      const refBefore = store.messages();
      store.appendToLastAssistant('token');
      const refAfter = store.messages();
      expect(refAfter).not.toBe(refBefore);
    });

    it('should not mutate the previous message objects', () => {
      store.startAssistantMessage();
      const msgBefore = store.messages()[0];
      store.appendToLastAssistant('hello');
      // The object at index 0 in the old array snapshot should be unchanged
      expect(msgBefore.content).toBe('');
    });

    it('should only append to the last message when multiple messages exist', () => {
      store.addUserMessage('Pytanie');
      store.startAssistantMessage();
      store.appendToLastAssistant('Odpowiedź');
      const msgs = store.messages();
      expect(msgs[0].content).toBe('Pytanie');
      expect(msgs[1].content).toBe('Odpowiedź');
    });
  });

  describe('endStreaming', () => {
    it('should set isStreaming to false', () => {
      store.startAssistantMessage();
      expect(store.isStreaming()).toBeTrue();
      store.endStreaming();
      expect(store.isStreaming()).toBeFalse();
    });
  });

  describe('setDecisionCategory', () => {
    it('should update currentDecisionCategory', () => {
      store.initFromSubmit(mockSubmitResult);
      store.setDecisionCategory('ESCALATE');
      expect(store.currentDecisionCategory()).toBe('ESCALATE');
    });

    it('should keep caseSummary.decisionCategory in sync when caseSummary is set', () => {
      store.initFromSubmit(mockSubmitResult);
      store.setDecisionCategory('REJECT');
      expect(store.caseSummary()!.decisionCategory).toBe('REJECT');
    });

    it('should work when caseSummary is null', () => {
      // Should not throw when no caseSummary is present
      expect(() => store.setDecisionCategory('ESCALATE')).not.toThrow();
      expect(store.currentDecisionCategory()).toBe('ESCALATE');
    });
  });

  describe('reset', () => {
    it('should clear sessionId', () => {
      store.initFromSubmit(mockSubmitResult);
      store.reset();
      expect(store.sessionId()).toBeNull();
    });

    it('should clear caseSummary', () => {
      store.initFromSubmit(mockSubmitResult);
      store.reset();
      expect(store.caseSummary()).toBeNull();
    });

    it('should clear currentDecisionCategory', () => {
      store.initFromSubmit(mockSubmitResult);
      store.reset();
      expect(store.currentDecisionCategory()).toBeNull();
    });

    it('should clear messages', () => {
      store.initFromSubmit(mockSubmitResult);
      store.addUserMessage('cośtam');
      store.reset();
      expect(store.messages()).toEqual([]);
    });

    it('should set isStreaming to false', () => {
      store.startAssistantMessage();
      store.reset();
      expect(store.isStreaming()).toBeFalse();
    });
  });
});
