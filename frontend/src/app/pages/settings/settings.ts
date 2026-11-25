import { ChangeDetectionStrategy, ChangeDetectorRef, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { NotificationService } from '../../core/services/notification.service';
import { LanguageService, AppLanguage } from '../../core/services/language.service';
import { ThemeService, AppTheme } from '../../core/services/theme.service';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Settings {
  readonly languages: { code: AppLanguage; label: string }[] = [
    { code: 'fr', label: 'Français' },
    { code: 'en', label: 'English' },
    { code: 'ar', label: 'العربية' },
  ];

  readonly themes: { code: AppTheme; labelKey: string; icon: string }[] = [
    { code: 'light', labelKey: 'settings.themeLight', icon: 'light_mode' },
    { code: 'dark', labelKey: 'settings.themeDark', icon: 'dark_mode' },
  ];

  currentPassword = '';
  newPassword = '';
  confirmNewPassword = '';
  isSaving = signal(false);
  passwordErrorKey = signal<string | null>(null);

  constructor(
    readonly auth: AuthService,
    readonly language: LanguageService,
    readonly theme: ThemeService,
    private readonly userService: UserService,
    private readonly notification: NotificationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  setLanguage(lang: AppLanguage): void {
    this.language.setLanguage(lang);
  }

  setTheme(theme: AppTheme): void {
    this.theme.setTheme(theme);
  }

  changePassword(): void {
    this.passwordErrorKey.set(null);

    if (!this.currentPassword || !this.newPassword) {
      this.passwordErrorKey.set('common.requiredField');
      return;
    }
    if (this.newPassword !== this.confirmNewPassword) {
      this.passwordErrorKey.set('settings.passwordMismatch');
      return;
    }

    this.isSaving.set(true);
    this.userService
      .changeOwnPassword({ currentPassword: this.currentPassword, newPassword: this.newPassword })
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmNewPassword = '';
          // These three are plain ngModel-bound fields, not signals - without an
          // explicit markForCheck the cleared values would never repaint (this app
          // has no zone.js; only signal writes or an explicit call like this one
          // schedule a re-render).
          this.cdr.markForCheck();
          this.notification.success('settings.passwordSuccess');
        },
        error: () => this.isSaving.set(false),
      });
  }
}
