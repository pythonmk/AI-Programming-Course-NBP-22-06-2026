import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import {
  provideHttpClient,
  withFetch,
} from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';

import { CaseApiService, CaseSubmitError } from './case-api.service';
import { FormOptions, SubmitResult } from '../models/models';

describe('CaseApiService', () => {
  let service: CaseApiService;
  let httpTesting: HttpTestingController;

  const mockFormOptions: FormOptions = {
    requestTypes: [
      { value: 'COMPLAINT', labelPl: 'Reklamacja' },
      { value: 'RETURN', labelPl: 'Zwrot' },
    ],
    equipmentCategories: [
      { value: 'LAPTOP', labelPl: 'Laptop' },
      { value: 'PHONE', labelPl: 'Telefon' },
    ],
  };

  const mockSubmitResult: SubmitResult = {
    sessionId: 'session-abc-123',
    decisionCategory: 'APPROVE',
    firstMessageMarkdown: '## Decyzja\n\nZgłoszenie **zatwierdzone**.',
    caseSummary: {
      requestType: 'COMPLAINT',
      equipmentCategory: 'LAPTOP',
      equipmentName: 'ThinkPad X1',
      decisionCategory: 'APPROVE',
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(CaseApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  // ── getFormOptions ──────────────────────────────────────────────────────────

  describe('getFormOptions', () => {
    it('should issue a GET request to /api/meta/form-options', async () => {
      const resultPromise = service.getFormOptions();

      const req = httpTesting.expectOne('/api/meta/form-options');
      expect(req.request.method).toBe('GET');
      req.flush(mockFormOptions);

      await resultPromise;
    });

    it('should return the parsed FormOptions on success', async () => {
      const resultPromise = service.getFormOptions();

      const req = httpTesting.expectOne('/api/meta/form-options');
      req.flush(mockFormOptions);

      const result = await resultPromise;
      expect(result).toEqual(mockFormOptions);
    });

    it('should return requestTypes array from the response', async () => {
      const resultPromise = service.getFormOptions();

      const req = httpTesting.expectOne('/api/meta/form-options');
      req.flush(mockFormOptions);

      const result = await resultPromise;
      expect(result.requestTypes.length).toBe(2);
      expect(result.requestTypes[0].value).toBe('COMPLAINT');
    });

    it('should return equipmentCategories array from the response', async () => {
      const resultPromise = service.getFormOptions();

      const req = httpTesting.expectOne('/api/meta/form-options');
      req.flush(mockFormOptions);

      const result = await resultPromise;
      expect(result.equipmentCategories.length).toBe(2);
      expect(result.equipmentCategories[0].value).toBe('LAPTOP');
    });
  });

  // ── submitCase ──────────────────────────────────────────────────────────────

  describe('submitCase', () => {
    const mockImageFile = new File(['fake-image-bytes'], 'photo.jpg', {
      type: 'image/jpeg',
    });

    const validInput = {
      requestType: 'COMPLAINT' as const,
      equipmentCategory: 'LAPTOP',
      equipmentName: 'ThinkPad X1',
      purchaseDate: '2024-01-15',
      reason: 'Ekran przestał działać po 3 miesiącach.',
      image: mockImageFile,
    };

    it('should issue a POST request to /api/cases', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      expect(req.request.method).toBe('POST');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should send a FormData body', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      expect(req.request.body).toBeInstanceOf(FormData);
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include requestType field in the FormData', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('requestType')).toBe('COMPLAINT');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include equipmentCategory field in the FormData', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('equipmentCategory')).toBe('LAPTOP');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include equipmentName field in the FormData', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('equipmentName')).toBe('ThinkPad X1');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include purchaseDate field in the FormData', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('purchaseDate')).toBe('2024-01-15');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include reason field in the FormData when provided', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('reason')).toBe('Ekran przestał działać po 3 miesiącach.');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should include the image File in the FormData', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('image')).toBe(mockImageFile);
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should not include reason field when reason is undefined', async () => {
      const inputWithoutReason = { ...validInput, reason: undefined };
      const resultPromise = service.submitCase(inputWithoutReason);

      const req = httpTesting.expectOne('/api/cases');
      const formData = req.request.body as FormData;
      expect(formData.get('reason')).toBeNull();
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      await resultPromise;
    });

    it('should return the parsed SubmitResult on 201', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      const result = await resultPromise;
      expect(result).toEqual(mockSubmitResult);
    });

    it('should return the sessionId from the SubmitResult', async () => {
      const resultPromise = service.submitCase(validInput);

      const req = httpTesting.expectOne('/api/cases');
      req.flush(mockSubmitResult, { status: 201, statusText: 'Created' });

      const result = await resultPromise;
      expect(result.sessionId).toBe('session-abc-123');
    });
  });

  // ── submitCase error mapping ─────────────────────────────────────────────────

  describe('submitCase — error mapping', () => {
    const mockImageFile = new File(['fake'], 'photo.jpg', { type: 'image/jpeg' });

    const validInput = {
      requestType: 'COMPLAINT' as const,
      equipmentCategory: 'LAPTOP',
      equipmentName: 'ThinkPad X1',
      purchaseDate: '2024-01-15',
      image: mockImageFile,
    };

    it('should reject with CaseSubmitError on 400', async () => {
      const errorBody = {
        error: {
          code: 'VALIDATION_ERROR',
          messagePl: 'Dane formularza są nieprawidłowe.',
          fieldErrors: [
            { field: 'equipmentName', messagePl: 'Pole wymagane.' },
          ],
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught).toBeDefined();
    });

    it('should set status 400 on a 400 response', async () => {
      const errorBody = {
        error: {
          code: 'VALIDATION_ERROR',
          messagePl: 'Dane formularza są nieprawidłowe.',
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.status).toBe(400);
    });

    it('should set retryable=false on 400 response', async () => {
      const errorBody = {
        error: {
          code: 'VALIDATION_ERROR',
          messagePl: 'Dane formularza są nieprawidłowe.',
          fieldErrors: [{ field: 'equipmentName', messagePl: 'Pole wymagane.' }],
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.retryable).toBeFalse();
    });

    it('should populate fieldErrors from 400 response body', async () => {
      const errorBody = {
        error: {
          code: 'VALIDATION_ERROR',
          messagePl: 'Dane formularza są nieprawidłowe.',
          fieldErrors: [
            { field: 'equipmentName', messagePl: 'Pole wymagane.' },
            { field: 'purchaseDate', messagePl: 'Data jest wymagana.' },
          ],
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.fieldErrors).toBeDefined();
      expect(caught!.fieldErrors!.length).toBe(2);
      expect(caught!.fieldErrors![0].field).toBe('equipmentName');
    });

    it('should populate code and messagePl from 400 response body', async () => {
      const errorBody = {
        error: {
          code: 'VALIDATION_ERROR',
          messagePl: 'Dane formularza są nieprawidłowe.',
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.code).toBe('VALIDATION_ERROR');
      expect(caught!.messagePl).toBe('Dane formularza są nieprawidłowe.');
    });

    it('should set retryable=true on 503 response', async () => {
      const errorBody = {
        error: {
          code: 'LLM_UNAVAILABLE',
          messagePl: 'Serwis AI jest chwilowo niedostępny.',
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 503, statusText: 'Service Unavailable' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.retryable).toBeTrue();
    });

    it('should set status 503 on a 503 response', async () => {
      const errorBody = {
        error: {
          code: 'LLM_UNAVAILABLE',
          messagePl: 'Serwis AI jest chwilowo niedostępny.',
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 503, statusText: 'Service Unavailable' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.status).toBe(503);
    });

    it('should use server messagePl on 503 when available', async () => {
      const errorBody = {
        error: {
          code: 'LLM_UNAVAILABLE',
          messagePl: 'Serwis AI jest chwilowo niedostępny.',
        },
      };

      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(errorBody, { status: 503, statusText: 'Service Unavailable' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.messagePl).toBe('Serwis AI jest chwilowo niedostępny.');
    });

    it('should use Polish fallback messagePl on 503 when server provides no body', async () => {
      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(null, { status: 503, statusText: 'Service Unavailable' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.messagePl).toBe(
        'Nie udało się przygotować oceny. Spróbuj ponownie.',
      );
    });

    it('should set retryable=true on 502 response', async () => {
      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.flush(null, { status: 502, statusText: 'Bad Gateway' });
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.retryable).toBeTrue();
    });

    it('should set retryable=true on network error (status 0)', async () => {
      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.error(new ProgressEvent('error'));
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught).toBeDefined();
      expect(caught!.retryable).toBeTrue();
    });

    it('should use Polish fallback messagePl on network error', async () => {
      let caught: CaseSubmitError | undefined;
      try {
        const resultPromise = service.submitCase(validInput);
        const req = httpTesting.expectOne('/api/cases');
        req.error(new ProgressEvent('error'));
        await resultPromise;
      } catch (e) {
        caught = e as CaseSubmitError;
      }

      expect(caught!.messagePl).toBe(
        'Nie udało się przygotować oceny. Spróbuj ponownie.',
      );
    });
  });
});
