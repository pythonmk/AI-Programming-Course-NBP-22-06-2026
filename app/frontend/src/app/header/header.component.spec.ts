import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    const component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('should render the NBP logo image', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const logo = compiled.querySelector('img.nbp-logo') as HTMLImageElement;
    expect(logo).withContext('logo img element should exist').toBeTruthy();
    expect(logo.src).withContext('logo src should point to logo.svg').toContain('logo.svg');
  });

  it('should render the Polish app title', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const titleEl = compiled.querySelector('.header-title');
    expect(titleEl).withContext('header title element should exist').toBeTruthy();
    expect(titleEl!.textContent).withContext('title should be in Polish').toContain('Asystent reklamacji');
  });

  it('should have a navy background via the header element', () => {
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('header');
    expect(header).withContext('header element should exist').toBeTruthy();
  });
});
