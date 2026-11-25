import { Pipe, PipeTransform, inject } from '@angular/core';
import { formatDate } from '@angular/common';
import { AppLanguage } from '../../core/services/language.service';

const ANGULAR_LOCALE: Record<AppLanguage, string> = {
  fr: 'fr',
  en: 'en-US',
  ar: 'ar',
};

/**
 * Angular's built-in DatePipe reads the app-wide LOCALE_ID token, which is fixed
 * at bootstrap - it doesn't react to the in-app language switcher, so dates kept
 * rendering in en-US format even when the rest of the UI was in French/Arabic.
 * This pipe takes the active language explicitly so it re-formats whenever the
 * template's language signal changes.
 */
@Pipe({
  name: 'localizedDate',
  standalone: true,
})
export class LocalizedDatePipe implements PipeTransform {
  transform(value: string | Date | null | undefined, format: string, lang: AppLanguage): string {
    if (!value) {
      return '';
    }
    return formatDate(value, format, ANGULAR_LOCALE[lang]);
  }
}
