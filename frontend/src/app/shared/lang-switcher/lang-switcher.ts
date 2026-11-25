import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LanguageService, AppLanguage } from '../../core/services/language.service';

@Component({
  selector: 'app-lang-switcher',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lang-switcher.html',
  styleUrl: './lang-switcher.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LangSwitcher {
  readonly languages: { code: AppLanguage; label: string }[] = [
    { code: 'fr', label: 'FR' },
    { code: 'en', label: 'EN' },
    { code: 'ar', label: 'AR' },
  ];

  constructor(readonly language: LanguageService) {}

  setLanguage(lang: AppLanguage): void {
    this.language.setLanguage(lang);
  }
}
