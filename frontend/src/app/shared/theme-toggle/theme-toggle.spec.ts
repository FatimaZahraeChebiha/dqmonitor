import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ThemeToggle } from './theme-toggle';
import { ThemeService } from '../../core/services/theme.service';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';

describe('ThemeToggle', () => {
  let component: ThemeToggle;
  let fixture: ComponentFixture<ThemeToggle>;
  let theme: ThemeService;

  beforeEach(async () => {
    localStorage.clear();
    document.documentElement.classList.remove('dark-theme');

    await TestBed.configureTestingModule({
      imports: [ThemeToggle, getTranslocoTestingModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(ThemeToggle);
    component = fixture.componentInstance;
    theme = TestBed.inject(ThemeService);
    await fixture.whenStable();
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark-theme');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('toggles the theme when clicked', () => {
    expect(theme.currentTheme()).toBe('light');

    component.toggle();

    expect(theme.currentTheme()).toBe('dark');
  });

  it('shows the moon icon in light mode and the sun icon in dark mode', () => {
    fixture.detectChanges();
    const iconBefore = fixture.nativeElement.querySelector('mat-icon').textContent.trim();
    expect(iconBefore).toBe('dark_mode');

    component.toggle();
    fixture.detectChanges();

    const iconAfter = fixture.nativeElement.querySelector('mat-icon').textContent.trim();
    expect(iconAfter).toBe('light_mode');
  });
});
