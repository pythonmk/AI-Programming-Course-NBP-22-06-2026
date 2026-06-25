import {
  Component,
  OnInit,
  signal,
  computed,
  inject,
  WritableSignal,
} from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  AbstractControl,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';

import { CaseApiService, CaseSubmitError } from '../services/case-api.service';
import { AppStateStore } from '../state/app-state.store';
import { FormOptions, Option, RequestType } from '../models/models';

/** Validator: rejects dates in the future (strictly after today). */
function noFutureDateValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: Date | null = control.value;
    if (!value) {
      return null; // let required handle missing
    }
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    return value > today
      ? { futureDate: 'Data zakupu nie może być w przyszłości' }
      : null;
  };
}

/** Accepted MIME types for image upload. */
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png'];
/** Maximum allowed file size in bytes (5 MB). */
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-intake-form',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatProgressBarModule,
    MatCardModule,
  ],
  templateUrl: './intake-form.component.html',
  styleUrl: './intake-form.component.scss',
})
export class IntakeFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly caseApi = inject(CaseApiService);
  private readonly store = inject(AppStateStore);
  private readonly router = inject(Router);

  // ── Form ──────────────────────────────────────────────────────────────────

  readonly form: FormGroup = this.fb.group({
    requestType: ['COMPLAINT', Validators.required],
    equipmentCategory: ['', Validators.required],
    equipmentName: ['', [Validators.required, Validators.minLength(1)]],
    purchaseDate: [null, [Validators.required, noFutureDateValidator()]],
    reason: [''],
  });

  // ── Form-options state ────────────────────────────────────────────────────

  readonly requestTypes = signal<Option[]>([]);
  readonly equipmentCategories = signal<Option[]>([]);

  // ── Image state ───────────────────────────────────────────────────────────

  /** WritableSignal kept internal; exposed via readonly accessors. */
  readonly _selectedFile: WritableSignal<File | null> = signal<File | null>(null);
  readonly _previewUrl: WritableSignal<string | null> = signal<string | null>(null);
  readonly _imageError: WritableSignal<string | null> = signal<string | null>(null);

  readonly selectedFile = this._selectedFile.asReadonly();
  readonly previewUrl = this._previewUrl.asReadonly();
  readonly imageError = this._imageError.asReadonly();

  // ── Submit state ──────────────────────────────────────────────────────────

  readonly isLoading = signal<boolean>(false);
  readonly retryError = signal<string | null>(null);
  readonly submitAttempted = signal<boolean>(false);

  // ── Derived state ─────────────────────────────────────────────────────────

  readonly maxDate = new Date();

  /** Label for the reason field depending on current request type. */
  readonly reasonLabel = computed<string>(() => {
    const rt = this.form.get('requestType')?.value as RequestType | null;
    return rt === 'COMPLAINT'
      ? 'Opis problemu (wymagany)'
      : 'Opis (opcjonalny)';
  });

  readonly isComplaint = computed<boolean>(
    () => (this.form.get('requestType')?.value as RequestType) === 'COMPLAINT',
  );

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadFormOptions();
    this.subscribeToRequestTypeChanges();
  }

  // ── Public API (called from template) ────────────────────────────────────

  /** Triggered by the hidden file input. */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] ?? null;
    if (!file) {
      return;
    }
    this.validateAndSetFile(file);
  }

  /** Remove the selected image. */
  removeImage(): void {
    this._selectedFile.set(null);
    this._previewUrl.set(null);
    this._imageError.set(null);
  }

  /** Form submit handler. */
  async onSubmit(): Promise<void> {
    this.submitAttempted.set(true);
    this.form.markAllAsTouched();

    const file = this._selectedFile();
    if (!file) {
      this._imageError.set('Zdjęcie jest wymagane');
    }

    if (this.form.invalid || !file) {
      return;
    }

    this.retryError.set(null);
    this.isLoading.set(true);
    this.form.disable();

    const raw = this.form.getRawValue();
    const purchaseDate = raw.purchaseDate as Date;
    const yyyy = purchaseDate.getFullYear();
    const mm = String(purchaseDate.getMonth() + 1).padStart(2, '0');
    const dd = String(purchaseDate.getDate()).padStart(2, '0');

    try {
      const result = await this.caseApi.submitCase({
        requestType: raw.requestType as RequestType,
        equipmentCategory: raw.equipmentCategory as string,
        equipmentName: (raw.equipmentName as string).trim(),
        purchaseDate: `${yyyy}-${mm}-${dd}`,
        reason: raw.reason ? (raw.reason as string).trim() : undefined,
        image: file,
      });

      this.store.initFromSubmit(result);
      await this.router.navigate(['/chat']);
    } catch (err: unknown) {
      const submitErr = err as CaseSubmitError;
      this.isLoading.set(false);
      this.form.enable();

      if (submitErr.fieldErrors && submitErr.fieldErrors.length > 0) {
        for (const fe of submitErr.fieldErrors) {
          const ctrl = this.form.get(fe.field);
          if (ctrl) {
            ctrl.setErrors({ serverError: fe.messagePl });
          }
        }
      } else {
        this.retryError.set(
          submitErr.messagePl ?? 'Nie udało się przygotować oceny. Spróbuj ponownie.',
        );
      }
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private async loadFormOptions(): Promise<void> {
    try {
      const opts: FormOptions = await this.caseApi.getFormOptions();
      this.requestTypes.set(opts.requestTypes);
      this.equipmentCategories.set(opts.equipmentCategories);
    } catch {
      // Non-critical: selects will be empty; user can still interact
    }
  }

  private subscribeToRequestTypeChanges(): void {
    this.form.get('requestType')!.valueChanges.subscribe((value: RequestType) => {
      this.updateReasonValidators(value);
    });
    // Apply initial validators based on default value
    this.updateReasonValidators(
      this.form.get('requestType')!.value as RequestType,
    );
  }

  private updateReasonValidators(requestType: RequestType): void {
    const reasonCtrl = this.form.get('reason')!;
    if (requestType === 'COMPLAINT') {
      reasonCtrl.setValidators([Validators.required]);
    } else {
      reasonCtrl.clearValidators();
    }
    reasonCtrl.updateValueAndValidity();
  }

  private validateAndSetFile(file: File): void {
    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      this._imageError.set('Dozwolone formaty: JPG, PNG');
      this._selectedFile.set(null);
      this._previewUrl.set(null);
      return;
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      this._imageError.set('Maksymalny rozmiar: 5 MB');
      this._selectedFile.set(null);
      this._previewUrl.set(null);
      return;
    }

    this._imageError.set(null);
    this._selectedFile.set(file);

    const reader = new FileReader();
    reader.onload = (e: ProgressEvent<FileReader>) => {
      this._previewUrl.set(e.target?.result as string ?? null);
    };
    reader.readAsDataURL(file);
  }
}
