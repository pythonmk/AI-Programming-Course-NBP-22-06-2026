import { TestBed } from '@angular/core/testing';
import { IntakeFormComponent } from './intake-form.component';

describe('IntakeFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IntakeFormComponent],
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(IntakeFormComponent);
    const component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('should render the intake form heading in Polish', () => {
    const fixture = TestBed.createComponent(IntakeFormComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Formularz zgłoszenia');
  });
});
