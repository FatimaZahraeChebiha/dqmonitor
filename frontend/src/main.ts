import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeAr from '@angular/common/locales/ar';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Needed for LocalizedDatePipe (formatDate throws for unregistered locales) -
// en-US is built in, fr/ar are not.
registerLocaleData(localeFr, 'fr');
registerLocaleData(localeAr, 'ar');

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
