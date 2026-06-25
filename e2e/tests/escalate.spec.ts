import { test, expect } from '@playwright/test';
import {
  mockFormOptions,
  mockCaseSubmit,
  mockChatStream,
  fillIntakeForm,
  submitForm,
  dateMinusDays,
} from './helpers';

// Shared helper: navigate to chat with a given decision via the intake form
async function goToChat(
  page: Parameters<typeof mockFormOptions>[0],
  decisionCategory: 'APPROVE' | 'REJECT' | 'ESCALATE',
): Promise<void> {
  await mockFormOptions(page);
  await mockCaseSubmit(page, { decisionCategory });
  await page.goto('/');
  await fillIntakeForm(page, {
    requestType: 'COMPLAINT',
    equipmentCategory: 'Laptop',
    equipmentName: 'ThinkPad X1',
    purchaseDate: dateMinusDays(30),
    reason: 'Urządzenie nie uruchamia się',
    uploadImage: true,
  });
  await submitForm(page);
  await page.waitForURL('**/chat');
}

test.describe('Q3 — Escalate path + decision transitions', () => {
  test('Scenario A — initial ESCALATE decision shows correct badge', async ({ page }) => {
    await goToChat(page, 'ESCALATE');

    const badge = page.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('Przekazanie do konsultanta');
  });

  test('Scenario B — REJECT → ESCALATE transition via chat', async ({ page }) => {
    await goToChat(page, 'REJECT');

    const badge = page.getByTestId('decision-badge');
    await expect(badge).toHaveText('Odrzucono');

    // Mock chat stream that includes a decisionCategory state change to ESCALATE
    await mockChatStream(
      page,
      ['Po analizie ', 'sprawa wymaga eskalacji.'],
      'ESCALATE',
    );

    // Send a message
    const textarea = page.getByRole('textbox', { name: 'Wiadomość' });
    await textarea.fill('Proszę o ponowne rozpatrzenie');
    await page.getByTestId('send-btn').click();

    // Badge updates to ESCALATE
    await expect(badge).toHaveText('Przekazanie do konsultanta');

    // Reply contains the streamed tokens
    const lastAssistantMsg = page.getByTestId('msg-assistant').last();
    await expect(lastAssistantMsg).toContainText('Po analizie sprawa wymaga eskalacji.');
  });

  test('Scenario C — APPROVE badge does not change via chat (no decisionCategory in stream)', async ({ page }) => {
    await goToChat(page, 'APPROVE');

    const badge = page.getByTestId('decision-badge');
    await expect(badge).toHaveText('Zatwierdzono wstępnie');

    // Stream has no decisionCategory frame — badge must stay the same
    await mockChatStream(page, ['Rozumiem ', 'pytanie.']);

    const textarea = page.getByRole('textbox', { name: 'Wiadomość' });
    await textarea.fill('Kiedy mogę dostarczyć urządzenie?');
    await page.getByTestId('send-btn').click();

    // Wait for stream to finish
    const lastAssistantMsg = page.getByTestId('msg-assistant').last();
    await expect(lastAssistantMsg).toContainText('Rozumiem pytanie.');

    // Badge must still show APPROVE
    await expect(badge).toHaveText('Zatwierdzono wstępnie');
  });
});
