import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideTransloco } from '@jsverse/transloco';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { HttpTranslocoLoader } from './core/services/transloco-loader';
import { TranslocoPaginatorIntl } from './core/services/transloco-paginator-intl.service';
import { environment } from '../environments/environment';

// Chart.js (via ng2-charts) is registered inside Dashboard's own component
// providers, not here - it's only used on one lazy-loaded route, so keeping it
// out of the root config keeps it out of the eagerly-loaded main bundle too.
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    { provide: MatPaginatorIntl, useClass: TranslocoPaginatorIntl },
    provideTransloco({
      config: {
        availableLangs: ['fr', 'en', 'ar'],
        defaultLang: 'fr',
        fallbackLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: environment.production,
      },
      loader: HttpTranslocoLoader,
    }),
  ],
};
