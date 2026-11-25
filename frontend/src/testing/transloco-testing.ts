import { TranslocoTestingModule } from '@jsverse/transloco';
import en from '../../public/i18n/en.json';

/**
 * Real `en` translations (not stubs) so components that read specific keys in
 * ngOnInit/templates render the same content in tests as in the app, instead
 * of silently falling back to raw translation keys.
 */
export function getTranslocoTestingModule() {
  return TranslocoTestingModule.forRoot({
    langs: { en },
    translocoConfig: {
      availableLangs: ['en'],
      defaultLang: 'en',
    },
    preloadLangs: true,
  });
}
