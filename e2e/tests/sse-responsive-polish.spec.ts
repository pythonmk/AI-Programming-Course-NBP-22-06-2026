import { test, expect } from '@playwright/test';
import {
  mockFormOptions,
  mockCaseSubmit,
  mockChatStream,
  fillIntakeForm,
  submitForm,
  dateMinusDays,
} from './helpers';

// Shared helper: navigate to chat and arrive at the chat page
async function navigateToChat(
  page: Parameters<typeof mockFormOptions>[0],
  decisionCategory: 'APPROVE' | 'REJECT' | 'ESCALATE' = 'APPROVE',
): Promise<void> {
  await mockFormOptions(page);
  await mockCaseSubmit(page, { decisionCategory });
  await page.goto('/');
  await fillIntakeForm(page, {
    requestType: 'COMPLAINT',
    equipmentCategory: 'Laptop',
    equipmentName: 'ThinkPad X1',
    purchaseDate: dateMinusDays(10),
    reason: 'Ekran nie działa',
    uploadImage: true,
  });
  await submitForm(page);
  await page.waitForURL('**/chat');
}

test.describe('Q5 — SSE rendering, responsive 360px, Polish copy', () => {
  test.describe('SSE rendering', () => {
    test('multi-token stream assembles correctly; [DONE] terminates spinner', async ({ page }) => {
      await navigateToChat(page, 'APPROVE');

      await mockChatStream(page, [
        'To jest ',
        'odpowiedź ',
        'asystenta.',
      ]);

      const textarea = page.getByRole('textbox', { name: 'Wiadomość' });
      await textarea.fill('Mam pytanie');
      await page.getByTestId('send-btn').click();

      // The assembled text should appear
      const lastAssistant = page.getByTestId('msg-assistant').last();
      await expect(lastAssistant).toContainText('To jest odpowiedź asystenta.');

      // Streaming indicator must not be visible after stream ends
      await expect(page.getByTestId('streaming-indicator')).not.toBeVisible();
    });
  });

  test.describe('Responsive 360px viewport', () => {
    test('no horizontal overflow on intake form at 360px', async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 800 });
      await mockFormOptions(page);
      await page.goto('/');

      // Form heading is visible at narrow width
      await expect(
        page.getByRole('heading', { name: 'Zgłoszenie reklamacji lub zwrotu' }),
      ).toBeVisible();

      // No horizontal scroll — body scroll width should not exceed viewport width
      const scrollWidth = await page.evaluate(() => document.body.scrollWidth);
      expect(scrollWidth).toBeLessThanOrEqual(360);
    });

    test('chat interface is visible at 360px', async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 800 });
      await navigateToChat(page, 'APPROVE');

      // Decision badge is visible
      const badge = page.getByTestId('decision-badge');
      await expect(badge).toBeVisible();

      // Text input area is visible
      await expect(page.getByRole('textbox', { name: 'Wiadomość' })).toBeVisible();

      // No horizontal scroll on chat page either
      const scrollWidth = await page.evaluate(() => document.body.scrollWidth);
      expect(scrollWidth).toBeLessThanOrEqual(360);
    });
  });

  test.describe('Polish copy', () => {
    test('intake form shows Polish labels for request types', async ({ page }) => {
      await mockFormOptions(page);
      await page.goto('/');

      // Open the request type dropdown
      await page.getByRole('combobox', { name: 'Rodzaj zgłoszenia' }).click();

      // Polish labels visible in the options
      await expect(page.getByRole('option', { name: 'Reklamacja' })).toBeVisible();
      await expect(page.getByRole('option', { name: 'Zwrot' })).toBeVisible();

      // Close dropdown
      await page.keyboard.press('Escape');
    });

    test('REJECT decision badge shows "Odrzucono" (not enum name)', async ({ page }) => {
      await navigateToChat(page, 'REJECT');

      const badge = page.getByTestId('decision-badge');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('Odrzucono');
      // Must NOT show raw enum name
      await expect(badge).not.toHaveText('REJECT');
    });

    test('APPROVE decision badge shows "Zatwierdzono wstępnie" (not enum name)', async ({ page }) => {
      await navigateToChat(page, 'APPROVE');

      const badge = page.getByTestId('decision-badge');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('Zatwierdzono wstępnie');
      await expect(badge).not.toHaveText('APPROVE');
    });
  });
});
