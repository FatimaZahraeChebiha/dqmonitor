import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';

/**
 * Centralized, translated feedback - replaces the original app's native alert()
 * calls, which can't be styled or (crucially) translated per the i18n requirement.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly transloco: TranslocoService,
  ) {}

  success(i18nKey: string, params?: Record<string, unknown>): void {
    this.show(i18nKey, params, 'success-snackbar');
  }

  error(i18nKey: string, params?: Record<string, unknown>): void {
    this.show(i18nKey, params, 'error-snackbar');
  }

  info(i18nKey: string, params?: Record<string, unknown>): void {
    this.show(i18nKey, params, 'info-snackbar');
  }

  private show(i18nKey: string, params: Record<string, unknown> | undefined, panelClass: string): void {
    const message = this.transloco.translate(i18nKey, params);
    const dismiss = this.transloco.translate('common.dismiss');
    this.snackBar.open(message, dismiss, {
      duration: 5000,
      panelClass,
      horizontalPosition: 'end',
      verticalPosition: 'top',
    });
  }
}
