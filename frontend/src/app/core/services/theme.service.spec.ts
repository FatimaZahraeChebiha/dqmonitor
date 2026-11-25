import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark-theme');
    TestBed.configureTestingModule({});
  });

  it('defaults to light when nothing is stored and the system has no preference', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('light');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(false);
  });

  it('restores a previously stored preference on construction', () => {
    localStorage.setItem('dqmonitor.theme', 'dark');

    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('dark');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(true);
  });

  it('setTheme updates the signal, persists to localStorage, and toggles the html class', () => {
    const service = TestBed.inject(ThemeService);

    service.setTheme('dark');

    expect(service.currentTheme()).toBe('dark');
    expect(localStorage.getItem('dqmonitor.theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(true);

    service.setTheme('light');

    expect(service.currentTheme()).toBe('light');
    expect(localStorage.getItem('dqmonitor.theme')).toBe('light');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(false);
  });

  it('toggleTheme flips between light and dark', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('light');
    service.toggleTheme();
    expect(service.currentTheme()).toBe('dark');
    service.toggleTheme();
    expect(service.currentTheme()).toBe('light');
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark-theme');
  });
});
