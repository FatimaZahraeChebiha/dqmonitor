import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoService } from '@jsverse/transloco';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './theme-toggle.html',
  styleUrl: './theme-toggle.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ThemeToggle {
  constructor(
    readonly theme: ThemeService,
    private readonly transloco: TranslocoService,
  ) {}

  toggle(): void {
    this.theme.toggleTheme();
  }

  label(): string {
    const key = this.theme.currentTheme() === 'dark' ? 'common.switchToLightMode' : 'common.switchToDarkMode';
    return this.transloco.translate(key);
  }
}
