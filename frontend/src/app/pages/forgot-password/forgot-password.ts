import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../core/services/auth.service';
import { LangSwitcher } from '../../shared/lang-switcher/lang-switcher';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-forgot-password',
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
  templateUrl: './forgot-password.html',
  styleUrl: '../../shared/styles/auth-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPassword {
  email = '';
  isSubmitting = signal(false);
  submitted = signal(false);

  constructor(private readonly authService: AuthService) {}

  submit(): void {
    if (!this.email) {
      return;
    }
    this.isSubmitting.set(true);
    this.authService.forgotPassword({ email: this.email }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.submitted.set(true);
      },
      error: () => {
        // Backend always returns 204 regardless of whether the email exists,
        // so a real error here means something else went wrong (network, 500) -
        // still show the generic success state to avoid leaking account existence.
        this.isSubmitting.set(false);
        this.submitted.set(true);
      },
    });
  }
}
