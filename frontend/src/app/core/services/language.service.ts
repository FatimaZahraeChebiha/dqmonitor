import { Injectable, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

export type AppLanguage = 'fr' | 'en' | 'ar';

const STORAGE_KEY = 'dqmonitor.lang';
const RTL_LANGUAGES: AppLanguage[] = ['ar'];

/**
 * Owns language + text direction for the whole app - not just the login form.
 * The original app only applied dir="rtl" inside the login card; here it's set
 * on <html> so the sidebar, tables and dialogs mirror correctly too.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly currentLang = signal<AppLanguage>(this.readStoredLang());

  constructor(private readonly transloco: TranslocoService) {
    this.apply(this.currentLang());
  }

  setLanguage(lang: AppLanguage): void {
    this.currentLang.set(lang);
    localStorage.setItem(STORAGE_KEY, lang);
    this.apply(lang);
  }

  private apply(lang: AppLanguage): void {
    this.transloco.setActiveLang(lang);
    const dir = RTL_LANGUAGES.includes(lang) ? 'rtl' : 'ltr';
    document.documentElement.setAttribute('lang', lang);
    document.documentElement.setAttribute('dir', dir);
  }

  private readStoredLang(): AppLanguage {
    const stored = localStorage.getItem(STORAGE_KEY) as AppLanguage | null;
    return stored ?? 'fr';
  }
}
