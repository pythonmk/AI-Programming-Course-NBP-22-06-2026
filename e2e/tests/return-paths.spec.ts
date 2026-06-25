import { test, expect } from '@playwright/test';
import {
  mockFormOptions,
  mockCaseSubmit,
  fillIntakeForm,
  submitForm,
  dateMinusDays,
} from './helpers';

test.describe('Q2 — Return paths', () => {
  test('Scenario A — within 14-day window → APPROVE badge', async ({ page }) => {
    await mockFormOptions(page);
    await mockCaseSubmit(page, {
      decisionCategory: 'APPROVE',
      requestType: 'RETURN',
      firstMessageMarkdown:
        '## Dzień dobry!\n\nWstępna ocena: **Zatwierdzono wstępnie**\n\nZwrot w terminie.\n\n*Powyższa ocena ma charakter wstępny.*',
    });

    await page.goto('/');

    await fillIntakeForm(page, {
      requestType: 'RETURN',
      equipmentCategory: 'Laptop',
      equipmentName: 'ThinkPad X1',
      purchaseDate: dateMinusDays(5),
      // reason is optional for RETURN
      uploadImage: true,
    });

    await submitForm(page);

    await page.waitForURL('**/chat');
    await expect(page).toHaveURL(/\/chat/);

    const badge = page.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('Zatwierdzono wstępnie');
  });

  test('Scenario B — outside 14-day window → REJECT badge and message', async ({ page }) => {
    await mockFormOptions(page);
    await mockCaseSubmit(page, {
      decisionCategory: 'REJECT',
      requestType: 'RETURN',
      firstMessageMarkdown:
        '## Odrzucono\n\nWstępna ocena: **Odrzucono**\n\nTermin zwrotu 14 dni minął.\n\n*Powyższa ocena ma charakter wstępny.*',
    });

    await page.goto('/');

    await fillIntakeForm(page, {
      requestType: 'RETURN',
      equipmentCategory: 'Laptop',
      equipmentName: 'ThinkPad X1',
      purchaseDate: dateMinusDays(20),
      uploadImage: true,
    });

    await submitForm(page);

    await page.waitForURL('**/chat');
    await expect(page).toHaveURL(/\/chat/);

    const badge = page.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('Odrzucono');

    // First assistant message contains "Odrzucono"
    const firstMsg = page.getByTestId('msg-assistant').first();
    await expect(firstMsg).toContainText('Odrzucono');
  });
});
