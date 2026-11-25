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
import { errorCodeToI18nKey } from '../../core/models/api-error.model';
import { LangSwitcher } from '../../shared/lang-switcher/lang-switcher';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-login',
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
  templateUrl: './login.html',
  styleUrl: '../../shared/styles/auth-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  email = '';
  password = '';
  isSubmitting = signal(false);
  errorKey = signal<string | null>(null);
  readonly currentYear = new Date().getFullYear();

  constructor(
    private readonly router: Router,
    private readonly authService: AuthService,
  ) {}

  login(): void {
    this.errorKey.set(null);

    if (!this.email || !this.password) {
      this.errorKey.set('auth.fillAllFields');
      return;
    }

    this.isSubmitting.set(true);
    this.authService
      .login({ email: this.email, password: this.password })
      .subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.router.navigate(['/dashboard']);
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
