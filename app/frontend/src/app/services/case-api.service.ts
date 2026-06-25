import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { FormOptions, SubmitResult, RequestType } from '../models/models';

/**
 * Structured error thrown by CaseApiService methods.
 *
 * - `status`      — HTTP status code (0 for network errors).
 * - `code`        — machine-readable error code from the backend `error.code` field.
 * - `messagePl`   — Polish-language message suitable for display.
 * - `fieldErrors` — per-field validation errors (populated for 400 responses).
 * - `retryable`   — true for 5xx / network errors; false for 400 validation errors.
 */
export interface CaseSubmitError {
  status: number;
  code?: string;
  messagePl?: string;
  fieldErrors?: { field: string; messagePl: string }[];
  retryable: boolean;
}

/** Input shape for {@link CaseApiService.submitCase}. */
export interface CaseSubmitInput {
  requestType: RequestType;
  equipmentCategory: string;
  equipmentName: string;
  /** ISO date string: yyyy-MM-dd */
  purchaseDate: string;
  /** Required for complaint requests; optional for returns. */
  reason?: string;
  /** JPEG or PNG, ≤ 5 MB. */
  image: File;
}

/** Polish fallback message used when no server body is available for 5xx / network errors. */
const FALLBACK_ERROR_PL =
  'Nie udało się przygotować oceny. Spróbuj ponownie.';

/**
 * Handles all HTTP interactions with the `/api/meta` and `/api/cases` endpoints.
 *
 * Both methods return `Promise<T>` (via `firstValueFrom`) to keep the
 * zoneless signal-based components free of Observable subscriptions.
 */
@Injectable({ providedIn: 'root' })
export class CaseApiService {
  private static readonly FORM_OPTIONS_URL = '/api/meta/form-options';
  private static readonly CASES_URL = '/api/cases';

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetches the select-option metadata for the intake form.
   * Calls `GET /api/meta/form-options`.
   */
  getFormOptions(): Promise<FormOptions> {
    return firstValueFrom(
      this.http.get<FormOptions>(CaseApiService.FORM_OPTIONS_URL),
    );
  }

  /**
   * Submits a new case via `POST /api/cases` (multipart/form-data).
   *
   * On success (201) resolves with a {@link SubmitResult}.
   * On failure rejects with a {@link CaseSubmitError}:
   *   - 400 → `retryable: false`, `fieldErrors` populated.
   *   - 502/503/5xx/network → `retryable: true`, Polish message.
   */
  async submitCase(input: CaseSubmitInput): Promise<SubmitResult> {
    const body = this.buildFormData(input);

    try {
      return await firstValueFrom(
        this.http.post<SubmitResult>(CaseApiService.CASES_URL, body),
      );
    } catch (raw) {
      throw this.mapError(raw as HttpErrorResponse);
    }
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  private buildFormData(input: CaseSubmitInput): FormData {
    const fd = new FormData();
    fd.append('requestType', input.requestType);
    fd.append('equipmentCategory', input.equipmentCategory);
    fd.append('equipmentName', input.equipmentName);
    fd.append('purchaseDate', input.purchaseDate);
    if (input.reason !== undefined && input.reason !== null) {
      fd.append('reason', input.reason);
    }
    fd.append('image', input.image);
    return fd;
  }

  private mapError(err: HttpErrorResponse): CaseSubmitError {
    const status = err.status ?? 0;
    // Try to extract the structured error envelope { error: { code, messagePl, fieldErrors? } }
    const envelope = err.error as
      | { error?: { code?: string; messagePl?: string; fieldErrors?: { field: string; messagePl: string }[] } }
      | null
      | undefined;
    const serverError = envelope?.error;

    if (status === 400) {
      return {
        status,
        code: serverError?.code,
        messagePl: serverError?.messagePl,
        fieldErrors: serverError?.fieldErrors,
        retryable: false,
      };
    }

    // 5xx, network (0), or any other non-400 failure → retryable
    return {
      status,
      code: serverError?.code,
      messagePl: serverError?.messagePl ?? FALLBACK_ERROR_PL,
      retryable: true,
    };
  }
}
