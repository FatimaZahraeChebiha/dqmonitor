import { Injectable, signal } from '@angular/core';

export type AppTheme = 'light' | 'dark';

const STORAGE_KEY = 'dqmonitor.theme';
const DARK_CLASS = 'dark-theme';

/**
 * Owns light/dark theme state for the whole app, mirroring LanguageService's
 * pattern (signal + localStorage + apply-to-<html>). The actual color values
 * for both themes live in material-theme.scss under the `.dark-theme` class -
 * this service only ever toggles that one class, it never touches colors
 * directly. index.html carries a tiny inline script that reads the same
 * localStorage key and applies the class before Angular even bootstraps, so
 * there's no flash of the wrong theme on page load.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly currentTheme = signal<AppTheme>(this.readStoredOrSystemTheme());

  constructor() {
    this.apply(this.currentTheme());
  }

  setTheme(theme: AppTheme): void {
    this.currentTheme.set(theme);
    localStorage.setItem(STORAGE_KEY, theme);
    this.apply(theme);
  }

  toggleTheme(): void {
    this.setTheme(this.currentTheme() === 'dark' ? 'light' : 'dark');
  }

  private apply(theme: AppTheme): void {
    document.documentElement.classList.toggle(DARK_CLASS, theme === 'dark');
  }

  private readStoredOrSystemTheme(): AppTheme {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  }
}
