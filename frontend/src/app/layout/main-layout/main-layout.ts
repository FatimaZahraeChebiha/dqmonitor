import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoModule } from '@jsverse/transloco';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { LanguageService } from '../../core/services/language.service';
import { NotificationCenterService } from '../../core/services/notification-center.service';
import { QualityReport } from '../../core/models/report.model';
import { LangSwitcher } from '../../shared/lang-switcher/lang-switcher';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatBadgeModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
    TranslocoModule,
    LangSwitcher,
    ThemeToggle,
    LocalizedDatePipe,
  ],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainLayout {
  isMobileSidebarOpen = false;

  // Declared before currentUrl (which depends on it) - field initializers run
  // in declaration order, so router is guaranteed to already be set.
  private readonly router = inject(Router);

  // The dashboard's welcome banner lives in the topbar now (the empty space
  // to the left of the icon cluster) instead of as its own block in the page
  // content - it only makes sense on the Dashboard itself, not as a generic
  // header repeated on every route, so the topbar shows it conditionally
  // based on the current route rather than every page opting in individually.
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  isDashboardRoute = computed(() => this.currentUrl() === '/dashboard');

  constructor(
    readonly auth: AuthService,
    readonly language: LanguageService,
    readonly notificationCenter: NotificationCenterService,
  ) {}

  scoreClass(score: number): string {
    if (score >= 80) return 'success';
    if (score >= 50) return 'warning';
    return 'error';
  }

  openReport(report: QualityReport): void {
    this.router.navigate(['/reports', report.id]);
  }

  toggleMobileSidebar(): void {
    this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
  }

  closeMobileSidebar(): void {
    this.isMobileSidebarOpen = false;
  }

  logout(): void {
    this.auth.logout();
  }

  goToSettings(): void {
    this.router.navigate(['/settings']);
  }
}
