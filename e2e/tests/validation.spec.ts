import { test, expect } from '@playwright/test';
import {
  mockFormOptions,
  fillIntakeForm,
  submitForm,
  dateMinusDays,
  datePlusDays,
  MINIMAL_JPEG,
} from './helpers';

test.describe('Q4 — Validation: required fields, no LLM on invalid', () => {
  test('submitting blank form shows required-field errors; /api/cases is NOT called', async ({ page }) => {
    await mockFormOptions(page);

    // Set up a route that FAILS the test if /api/cases is called
    let casesCalled = false;
    await page.route('**/api/cases', async (route) => {
      casesCalled = true;
      await route.abort();
    });

    await page.goto('/');

    // Reset requestType to empty so we can test that error too.
    // The form pre-fills COMPLAINT, so we test other missing fields:
    // equipmentCategory, equipmentName, purchaseDate, image.
    // We do NOT fill anything — just submit.
    await submitForm(page);

    // equipmentCategory is required
    await expect(page.getByText('Kategoria sprzętu jest wymagana')).toBeVisible();
    // equipmentName is required
    await expect(page.getByText('Nazwa/model jest wymagana')).toBeVisible();
    // purchaseDate is required
    await expect(page.getByText('Data zakupu jest wymagana')).toBeVisible();
    // reason is required for COMPLAINT
    await expect(page.getByText('Opis jest wymagany dla reklamacji')).toBeVisible();
    // image is required
    await expect(page.getByText('Zdjęcie jest wymagane')).toBeVisible();

    // /api/cases must NOT have been called
    expect(casesCalled).toBe(false);
  });

  test('COMPLAINT with blank reason shows reason error', async ({ page }) => {
    await mockFormOptions(page);

    let casesCalled = false;
    await page.route('**/api/cases', async (route) => {
      casesCalled = true;
      await route.abort();
    });

    await page.goto('/');

    // Fill everything except reason
    await fillIntakeForm(page, {
      requestType: 'COMPLAINT',
      equipmentCategory: 'Laptop',
      equipmentName: 'ThinkPad X1',
      purchaseDate: dateMinusDays(10),
      // reason intentionally omitted
      uploadImage: true,
    });

    await submitForm(page);

    await expect(page.getByText('Opis jest wymagany dla reklamacji')).toBeVisible();
    expect(casesCalled).toBe(false);
  });

  test('future purchase date shows date error', async ({ page }) => {
    await mockFormOptions(page);

    let casesCalled = false;
    await page.route('**/api/cases', async (route) => {
      casesCalled = true;
      await route.abort();
    });

    await page.goto('/');

    await fillIntakeForm(page, {
      requestType: 'COMPLAINT',
      equipmentCategory: 'Laptop',
      equipmentName: 'ThinkPad X1',
      purchaseDate: datePlusDays(1),
      reason: 'Urządzenie się nie włącza',
      uploadImage: true,
    });

    await submitForm(page);

    // Either "matDatepickerMax" or "futureDate" error should appear.
    // Use .first() because Angular Material may render the same error text
    // in multiple mat-error elements simultaneously.
    const dateError = page.locator('mat-error').filter({
      hasText: /nie może być w przyszłości/,
    }).first();
    await expect(dateError).toBeVisible();

    expect(casesCalled).toBe(false);
  });

  test('server 400 VALIDATION_ERROR shows field error on equipmentName', async ({ page }) => {
    await mockFormOptions(page);

    // Override the route to return a 400 with validation error
    await page.route('**/api/cases', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          error: {
            code: 'VALIDATION_ERROR',
            messagePl: 'Błąd walidacji',
            fieldErrors: [
              {
                field: 'equipmentName',
                messagePl: 'Nazwa jest wymagana',
              },
            ],
          },
        }),
      });
    });

    await page.goto('/');

    // Fill a form that passes client-side validation but fails server-side
    await fillIntakeForm(page, {
      requestType: 'COMPLAINT',
      equipmentCategory: 'Laptop',
      equipmentName: 'X', // short name that passes client but fails server
      purchaseDate: dateMinusDays(10),
      reason: 'Ekran przestał działać',
      uploadImage: true,
    });

    await submitForm(page);

    // The server field error should appear for equipmentName
    await expect(page.getByText('Nazwa jest wymagana')).toBeVisible();
  });
});
