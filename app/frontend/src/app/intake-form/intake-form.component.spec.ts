import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';

import { IntakeFormComponent } from './intake-form.component';
import { CaseApiService, CaseSubmitError } from '../services/case-api.service';
import { AppStateStore } from '../state/app-state.store';
import { FormOptions, SubmitResult } from '../models/models';

const MOCK_FORM_OPTIONS: FormOptions = {
  requestTypes: [
    { value: 'COMPLAINT', labelPl: 'Reklamacja' },
    { value: 'RETURN', labelPl: 'Zwrot' },
  ],
  equipmentCategories: [
    { value: 'LAPTOP', labelPl: 'Laptopy' },
    { value: 'MONITOR', labelPl: 'Monitory' },
  ],
};

const MOCK_SUBMIT_RESULT: SubmitResult = {
  sessionId: 'session-001',
  decisionCategory: 'APPROVE',
  firstMessageMarkdown: '## Decyzja\n\nZatwierdzone.',
  caseSummary: {
    requestType: 'COMPLAINT',
    equipmentCategory: 'LAPTOP',
    equipmentName: 'ThinkPad X1',
    decisionCategory: 'APPROVE',
  },
};

/** Build a File with a given MIME type and size (bytes). */
function makeFile(name: string, type: string, sizeBytes: number): File {
  const content = new Uint8Array(sizeBytes);
  return new File([content], name, { type });
}

describe('IntakeFormComponent', () => {
  let fixture: ComponentFixture<IntakeFormComponent>;
  let component: IntakeFormComponent;
  let mockCaseApi: jasmine.SpyObj<CaseApiService>;
  let store: AppStateStore;
  let router: Router;

  beforeEach(async () => {
    mockCaseApi = jasmine.createSpyObj<CaseApiService>('CaseApiService', [
      'getFormOptions',
      'submitCase',
    ]);
    mockCaseApi.getFormOptions.and.resolveTo(MOCK_FORM_OPTIONS);

    await TestBed.configureTestingModule({
      imports: [IntakeFormComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideAnimationsAsync(),
        provideNativeDateAdapter(),
        provideRouter([
          { path: '', component: IntakeFormComponent },
          { path: 'chat', component: IntakeFormComponent },
        ]),
        { provide: CaseApiService, useValue: mockCaseApi },
      ],
    }).compileComponents();

    store = TestBed.inject(AppStateStore);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(IntakeFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    // Wait for getFormOptions to resolve
    await fixture.whenStable();
    fixture.detectChanges();
  });

  // ─── TAC-201 scenario 1: reason required toggles ──────────────────────────

  describe('reason required toggles (TAC-201)', () => {
    it('reason is required when request type is COMPLAINT', () => {
      component.form.get('requestType')!.setValue('COMPLAINT');
      fixture.detectChanges();
      const reasonCtrl = component.form.get('reason')!;
      reasonCtrl.setValue('');
      reasonCtrl.markAsTouched();
      expect(reasonCtrl.hasError('required')).toBeTrue();
    });

    it('reason is NOT required when request type is RETURN', () => {
      component.form.get('requestType')!.setValue('RETURN');
      fixture.detectChanges();
      const reasonCtrl = component.form.get('reason')!;
      reasonCtrl.setValue('');
      reasonCtrl.markAsTouched();
      expect(reasonCtrl.hasError('required')).toBeFalse();
    });

    it('switches from COMPLAINT to RETURN — reason becomes optional', () => {
      component.form.get('requestType')!.setValue('COMPLAINT');
      fixture.detectChanges();
      component.form.get('requestType')!.setValue('RETURN');
      fixture.detectChanges();
      const reasonCtrl = component.form.get('reason')!;
      reasonCtrl.setValue('');
      reasonCtrl.markAsTouched();
      expect(reasonCtrl.hasError('required')).toBeFalse();
    });

    it('switches from RETURN to COMPLAINT — reason becomes required', () => {
      component.form.get('requestType')!.setValue('RETURN');
      fixture.detectChanges();
      component.form.get('requestType')!.setValue('COMPLAINT');
      fixture.detectChanges();
      const reasonCtrl = component.form.get('reason')!;
      reasonCtrl.setValue('');
      reasonCtrl.markAsTouched();
      expect(reasonCtrl.hasError('required')).toBeTrue();
    });
  });

  // ─── TAC-201 scenario 2: image validation ─────────────────────────────────

  describe('image validation (TAC-201)', () => {
    it('rejects a GIF with "Dozwolone formaty: JPG, PNG"', () => {
      const gifFile = makeFile('photo.gif', 'image/gif', 1024);
      component.onFileSelected({ target: { files: [gifFile] } } as unknown as Event);
      fixture.detectChanges();
      expect(component.imageError()).toBe('Dozwolone formaty: JPG, PNG');
    });

    it('rejects a file larger than 5 MB with "Maksymalny rozmiar: 5 MB"', () => {
      const bigFile = makeFile('photo.jpg', 'image/jpeg', 5 * 1024 * 1024 + 1);
      component.onFileSelected({ target: { files: [bigFile] } } as unknown as Event);
      fixture.detectChanges();
      expect(component.imageError()).toBe('Maksymalny rozmiar: 5 MB');
    });

    it('accepts a valid PNG under 5 MB — clears error and sets selected file', async () => {
      const validFile = makeFile('photo.png', 'image/png', 100 * 1024);
      component.onFileSelected({ target: { files: [validFile] } } as unknown as Event);
      // FileReader is async — wait for the microtask/promise queue
      await new Promise<void>(resolve => setTimeout(resolve, 50));
      fixture.detectChanges();
      expect(component.imageError()).toBeNull();
      expect(component.selectedFile()).toBe(validFile);
    });

    it('accepts a valid PNG under 5 MB — sets a previewUrl', async () => {
      const validFile = makeFile('photo.png', 'image/png', 100 * 1024);
      component.onFileSelected({ target: { files: [validFile] } } as unknown as Event);
      await new Promise<void>(resolve => setTimeout(resolve, 50));
      fixture.detectChanges();
      expect(component.previewUrl()).toBeTruthy();
    });
  });

  // ─── TAC-201 scenario 3: future date blocked ──────────────────────────────

  describe('future date validation (TAC-201)', () => {
    it('marks purchaseDate control as invalid for a future date', () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      component.form.get('purchaseDate')!.setValue(tomorrow);
      component.form.get('purchaseDate')!.markAsTouched();
      fixture.detectChanges();
      expect(component.form.get('purchaseDate')!.invalid).toBeTrue();
    });

    it('form is invalid when purchaseDate is in the future', () => {
      fillRequiredFieldsExcept(component, 'purchaseDate');
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      component.form.get('purchaseDate')!.setValue(tomorrow);
      fixture.detectChanges();
      expect(component.form.valid).toBeFalse();
    });

    it('marks purchaseDate control as valid for today', () => {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      component.form.get('purchaseDate')!.setValue(today);
      component.form.get('purchaseDate')!.markAsTouched();
      fixture.detectChanges();
      expect(component.form.get('purchaseDate')!.invalid).toBeFalse();
    });
  });

  // ─── TAC-201 scenario 4: invalid form blocks submit ───────────────────────

  describe('invalid form blocks submit (TAC-201)', () => {
    it('submitCase is NOT called when form is invalid', async () => {
      // Leave form with missing image and invalid fields
      await component.onSubmit();
      expect(mockCaseApi.submitCase).not.toHaveBeenCalled();
    });
  });

  // ─── TAC-201 scenario 5: valid form → navigate to /chat ──────────────────

  describe('valid form + submitCase resolves → navigate', () => {
    it('calls store.initFromSubmit and router.navigate(["/chat"])', async () => {
      mockCaseApi.submitCase.and.resolveTo(MOCK_SUBMIT_RESULT);
      spyOn(store, 'initFromSubmit').and.callThrough();
      spyOn(router, 'navigate').and.resolveTo(true);

      fillValidForm(component);
      fixture.detectChanges();

      await component.onSubmit();

      expect(mockCaseApi.submitCase).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ requestType: 'COMPLAINT' }),
      );
      expect(store.initFromSubmit).toHaveBeenCalledWith(MOCK_SUBMIT_RESULT);
      expect(router.navigate).toHaveBeenCalledWith(['/chat']);
    });
  });

  // ─── TAC-201 scenario 6: error handling ──────────────────────────────────

  describe('submitCase rejects with retryable error', () => {
    it('shows retry error message and re-enables form on retryable error', async () => {
      const retryableError: CaseSubmitError = {
        status: 503,
        messagePl: 'Usługa niedostępna',
        retryable: true,
      };
      mockCaseApi.submitCase.and.rejectWith(retryableError);

      fillValidForm(component);
      fixture.detectChanges();

      await component.onSubmit();
      fixture.detectChanges();

      expect(component.retryError()).toBeTruthy();
      expect(component.form.disabled).toBeFalse();
      expect(component.isLoading()).toBeFalse();
    });
  });

  describe('submitCase rejects with fieldErrors', () => {
    it('maps backend fieldErrors onto form controls', async () => {
      const fieldErrorResponse: CaseSubmitError = {
        status: 400,
        fieldErrors: [
          { field: 'equipmentName', messagePl: 'Nazwa jest wymagana' },
        ],
        retryable: false,
      };
      mockCaseApi.submitCase.and.rejectWith(fieldErrorResponse);

      fillValidForm(component);
      fixture.detectChanges();

      await component.onSubmit();
      fixture.detectChanges();

      const ctrl = component.form.get('equipmentName')!;
      expect(ctrl.hasError('serverError')).toBeTrue();
      expect(ctrl.getError('serverError')).toBe('Nazwa jest wymagana');
    });
  });
});

// ─── Helpers ─────────────────────────────────────────────────────────────────

function fillValidForm(component: IntakeFormComponent): void {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  component.form.setValue({
    requestType: 'COMPLAINT',
    equipmentCategory: 'LAPTOP',
    equipmentName: 'ThinkPad X1',
    purchaseDate: today,
    reason: 'Klawiatura nie działa',
  });

  const validFile = makeFile('photo.png', 'image/png', 100 * 1024);
  // Directly set the internal writable signals (accessible via bracket notation)
  component._selectedFile.set(validFile);
  component._previewUrl.set('data:image/png;base64,abc');
  component._imageError.set(null);
}

function fillRequiredFieldsExcept(
  component: IntakeFormComponent,
  skip: string,
): void {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const values: Record<string, unknown> = {
    requestType: 'COMPLAINT',
    equipmentCategory: 'LAPTOP',
    equipmentName: 'ThinkPad X1',
    purchaseDate: today,
    reason: 'Klawiatura nie działa',
  };

  if (skip in values) {
    delete values[skip];
    component.form.patchValue(values);
  } else {
    component.form.setValue({
      requestType: 'COMPLAINT',
      equipmentCategory: 'LAPTOP',
      equipmentName: 'ThinkPad X1',
      purchaseDate: today,
      reason: 'Klawiatura nie działa',
    });
  }

  const validFile = makeFile('photo.png', 'image/png', 100 * 1024);
  component._selectedFile.set(validFile);
  component._previewUrl.set('data:image/png;base64,abc');
  component._imageError.set(null);
}
