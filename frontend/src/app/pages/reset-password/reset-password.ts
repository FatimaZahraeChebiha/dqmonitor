import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
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
  selector: 'app-reset-password',
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
  templateUrl: './reset-password.html',
  styleUrl: '../../shared/styles/auth-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPassword implements OnInit {
  token: string | null = null;
  newPassword = '';
  confirmNewPassword = '';
  isSubmitting = signal(false);
  errorKey = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthService,
    private readonly notification: NotificationService,
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.errorKey.set('auth.resetPasswordInvalidToken');
    }
  }

  submit(): void {
    if (!this.token) {
      return;
    }
    this.errorKey.set(null);

    if (!this.newPassword || this.newPassword !== this.confirmNewPassword) {
      this.errorKey.set('settings.passwordMismatch');
      return;
    }

    this.isSubmitting.set(true);
    this.authService.resetPassword({ token: this.token, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.notification.success('auth.resetPasswordSuccess');
        this.router.navigate(['/login']);
      },
      error: (err: unknown) => {
        this.isSubmitting.set(false);
        if (err instanceof HttpErrorResponse && err.status === 401) {
          this.errorKey.set('auth.resetPasswordInvalidToken');
        } else if (err instanceof HttpErrorResponse) {
          this.errorKey.set(errorCodeToI18nKey(err.error?.code));
        } else {
          this.errorKey.set('errors.INTERNAL_ERROR');
        }
      },
    });
  }
}
