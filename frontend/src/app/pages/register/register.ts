import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { errorCodeToI18nKey } from '../../core/models/api-error.model';
import { LangSwitcher } from '../../shared/lang-switcher/lang-switcher';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslocoModule,
    LangSwitcher,
    ThemeToggle,
  ],
  templateUrl: './register.html',
  styleUrls: ['../../shared/styles/auth-page.css', './register.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  username = '';
  email = '';
  password = '';
  confirmPassword = '';
  isSubmitting = signal(false);
  errorKey = signal<string | null>(null);
  readonly currentYear = new Date().getFullYear();

  constructor(
    private readonly router: Router,
    private readonly authService: AuthService,
    private readonly notification: NotificationService,
  ) {}

  register(): void {
    this.errorKey.set(null);

    if (!this.username || !this.email || !this.password) {
      this.errorKey.set('auth.fillAllFields');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorKey.set('auth.passwordMismatch');
      return;
    }

    this.isSubmitting.set(true);
    this.authService
      .register({ username: this.username, email: this.email, password: this.password })
      .subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.notification.success('auth.registerSuccess');
          this.router.navigate(['/login']);
        },
        error: (err: unknown) => {
          this.isSubmitting.set(false);
          if (err instanceof HttpErrorResponse) {
            this.errorKey.set(errorCodeToI18nKey(err.error?.code));
          } else {
            this.errorKey.set('errors.INTERNAL_ERROR');
          }
        },
      });
  }
}
