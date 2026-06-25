import { Page } from '@playwright/test';

// ── Form-options mock response ─────────────────────────────────────────────

export const FORM_OPTIONS_RESPONSE = {
  requestTypes: [
    { value: 'COMPLAINT', labelPl: 'Reklamacja' },
    { value: 'RETURN', labelPl: 'Zwrot' },
  ],
  equipmentCategories: [
    { value: 'LAPTOP', labelPl: 'Laptop' },
    { value: 'DESKTOP', labelPl: 'Komputer stacjonarny' },
    { value: 'MONITOR', labelPl: 'Monitor' },
    { value: 'PERIPHERALS', labelPl: 'Peryferia' },
    { value: 'PC_COMPONENTS', labelPl: 'Komponenty PC' },
    { value: 'NETWORKING', labelPl: 'Sieciowe' },
    { value: 'ACCESSORIES', labelPl: 'Akcesoria' },
    { value: 'OTHER', labelPl: 'Inne' },
  ],
};

// ── Date helpers ───────────────────────────────────────────────────────────

/**
 * Returns a date string in the format accepted by Angular Material datepicker.
 * Angular Material with NativeDateAdapter uses M/D/YYYY format.
 * e.g. today minus daysAgo days.
 */
export function dateMinusDays(daysAgo: number): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  // Angular Material NativeDateAdapter accepts M/D/YYYY
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const year = d.getFullYear();
  return `${month}/${day}/${year}`;
}

/** Returns a future date string (tomorrow) in M/D/YYYY format. */
export function datePlusDays(daysAhead: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const year = d.getFullYear();
  return `${month}/${day}/${year}`;
}

// ── Minimal test JPEG bytes ────────────────────────────────────────────────

export const MINIMAL_JPEG = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
]);

// ── Mock helpers ───────────────────────────────────────────────────────────

/** Sets up route mock for GET /api/meta/form-options */
export async function mockFormOptions(page: Page): Promise<void> {
  await page.route('**/api/meta/form-options', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(FORM_OPTIONS_RESPONSE),
    });
  });
}

export interface CaseMockOptions {
  decisionCategory: 'APPROVE' | 'REJECT' | 'ESCALATE';
  requestType?: string;
  firstMessageMarkdown?: string;
  sessionId?: string;
}

/** Sets up route mock for POST /api/cases → 201 */
export async function mockCaseSubmit(
  page: Page,
  options: CaseMockOptions,
): Promise<void> {
  const {
    decisionCategory,
    requestType = 'COMPLAINT',
    firstMessageMarkdown,
    sessionId = 'session-e2e-001',
  } = options;

  const defaultMessage =
    decisionCategory === 'APPROVE'
      ? '## Dzień dobry!\n\nWstępna ocena: **Zatwierdzono wstępnie**\n\nUrządzenie kwalifikuje się do reklamacji.\n\n*Powyższa ocena ma charakter wstępny.*'
      : decisionCategory === 'REJECT'
        ? '## Odrzucono\n\nWstępna ocena: **Odrzucono**\n\nZgłoszenie nie spełnia warunków.\n\n*Powyższa ocena ma charakter wstępny.*'
        : '## Przekazanie do konsultanta\n\nSprawę przekazujemy do konsultanta.\n\n*Powyższa ocena ma charakter wstępny.*';

  await page.route('**/api/cases', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        sessionId,
        decisionCategory,
        firstMessageMarkdown: firstMessageMarkdown ?? defaultMessage,
        caseSummary: {
          requestType,
          equipmentCategory: 'LAPTOP',
          equipmentName: 'ThinkPad X1',
          decisionCategory,
        },
      }),
    });
  });
}

/**
 * Sets up route mock for POST /api/sessions/{id}/messages → SSE stream.
 *
 * Tokens are emitted as {"t":"<token>"} frames.
 * Optional newDecisionCategory emits a {"decisionCategory":"..."} frame.
 * Stream terminates with [DONE].
 */
export async function mockChatStream(
  page: Page,
  tokens: string[],
  newDecisionCategory?: string,
): Promise<void> {
  await page.route('**/api/sessions/*/messages', async (route) => {
    const tokenFrames = tokens
      .map((t) => `data:${JSON.stringify({ t })}\n\n`)
      .join('');
    const stateFrame = newDecisionCategory
      ? `data:${JSON.stringify({ decisionCategory: newDecisionCategory })}\n\n`
      : '';
    const body = tokenFrames + stateFrame + 'data:[DONE]\n\n';

    await route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
      body,
    });
  });
}

// ── Form interaction helpers ───────────────────────────────────────────────

export interface FillFormOptions {
  requestType?: 'COMPLAINT' | 'RETURN';
  equipmentCategory?: string;
  equipmentName?: string;
  purchaseDate?: string; // M/D/YYYY format
  reason?: string;
  uploadImage?: boolean;
}

/**
 * Fills the intake form fields.
 * Assumes form-options mock is already set up and page is at '/'.
 */
export async function fillIntakeForm(
  page: Page,
  options: FillFormOptions,
): Promise<void> {
  const {
    requestType,
    equipmentCategory,
    equipmentName,
    purchaseDate,
    reason,
    uploadImage = false,
  } = options;

  // Request type — mat-select (combobox)
  if (requestType !== undefined) {
    await page.getByRole('combobox', { name: 'Rodzaj zgłoszenia' }).click();
    const label = requestType === 'COMPLAINT' ? 'Reklamacja' : 'Zwrot';
    await page.getByRole('option', { name: label }).click();
  }

  // Equipment category — mat-select (combobox)
  if (equipmentCategory !== undefined) {
    await page.getByRole('combobox', { name: 'Kategoria sprzętu' }).click();
    await page.getByRole('option', { name: equipmentCategory }).click();
  }

  // Equipment name — text input
  if (equipmentName !== undefined) {
    await page.getByLabel('Nazwa / model sprzętu').fill(equipmentName);
  }

  // Purchase date — Angular Material datepicker input
  if (purchaseDate !== undefined) {
    const dateInput = page.locator('input[formcontrolname="purchaseDate"]');
    await dateInput.click();
    await dateInput.fill(purchaseDate);
    await dateInput.press('Tab'); // confirm date selection
  }

  // Reason textarea
  if (reason !== undefined) {
    const reasonTextarea = page.locator('textarea[formcontrolname="reason"]');
    await reasonTextarea.fill(reason);
  }

  // Image upload
  if (uploadImage) {
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: MINIMAL_JPEG,
    });
  }
}

/** Clicks the submit button. */
export async function submitForm(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Wyślij zgłoszenie' }).click();
}
