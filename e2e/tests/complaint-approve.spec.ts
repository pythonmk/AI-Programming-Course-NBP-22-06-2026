import { test, expect } from '@playwright/test';
import {
  mockFormOptions,
  mockCaseSubmit,
  mockChatStream,
  fillIntakeForm,
  submitForm,
  dateMinusDays,
} from './helpers';

test.describe('Q1 — Complaint → Approve happy path', () => {
  test('complete flow: fill form → submit → chat → streamed reply', async ({ page }) => {
    // ── Setup: mock all API routes before navigation ───────────────────────
    await mockFormOptions(page);
    await mockCaseSubmit(page, {
      decisionCategory: 'APPROVE',
      requestType: 'COMPLAINT',
    });

    // ── Step 1: Navigate to intake form ───────────────────────────────────
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Zgłoszenie reklamacji lub zwrotu' })).toBeVisible();

    // ── Step 2: Fill form ──────────────────────────────────────────────────
    await fillIntakeForm(page, {
      requestType: 'COMPLAINT',
      equipmentCategory: 'Laptop',
      equipmentName: 'ThinkPad X1',
      purchaseDate: dateMinusDays(10),
      reason: 'Ekran przestał działać',
      uploadImage: true,
    });

    // ── Step 3: Submit ─────────────────────────────────────────────────────
    await submitForm(page);

    // ── Step 4: Assert navigation to /chat ────────────────────────────────
    await page.waitForURL('**/chat');
    await expect(page).toHaveURL(/\/chat/);

    // ── Step 5: Assert decision badge shows "Zatwierdzono wstępnie" ───────
    const badge = page.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('Zatwierdzono wstępnie');

    // ── Step 6: Assert first assistant message is rendered ─────────────────
    const assistantMessages = page.getByTestId('msg-assistant');
    await expect(assistantMessages.first()).toBeVisible();
    // The markdown renders the firstMessageMarkdown content
    await expect(assistantMessages.first()).toContainText('Zatwierdzono wstępnie');

    // ── Step 7: Set up chat stream mock ───────────────────────────────────
    await mockChatStream(page, ['Dziękuję ', 'za wiadomość.']);

    // ── Step 8: Type and send a chat message ──────────────────────────────
    const textarea = page.getByRole('textbox', { name: 'Wiadomość' });
    await textarea.fill('Czy mogę wysłać urządzenie pocztą?');
    await page.getByTestId('send-btn').click();

    // ── Step 9: Assert user bubble appears ────────────────────────────────
    const userBubble = page.getByTestId('msg-user').last();
    await expect(userBubble).toContainText('Czy mogę wysłać urządzenie pocztą?');

    // ── Step 10: Assert assistant reply assembled from stream tokens ───────
    const lastAssistantMsg = page.getByTestId('msg-assistant').last();
    await expect(lastAssistantMsg).toContainText('Dziękuję za wiadomość.');

    // ── Step 11: Assert streaming indicator disappears after stream ends ───
    await expect(page.getByTestId('streaming-indicator')).not.toBeVisible();
  });
});
