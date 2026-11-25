import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/services/auth.service';
import { ValidationRule } from '../../core/models/validation-rule.model';
import { ValidationRuleService } from '../../core/services/validation-rule.service';
import { NotificationService } from '../../core/services/notification.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { RuleFormDialog, RuleFormDialogData } from './rule-form-dialog/rule-form-dialog';

@Component({
  selector: 'app-validation-rules',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, TranslocoModule],
  templateUrl: './validation-rules.html',
  styleUrl: './validation-rules.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ValidationRules implements OnInit {
  rules = signal<ValidationRule[]>([]);

  constructor(
    readonly auth: AuthService,
    private readonly ruleService: ValidationRuleService,
    private readonly notification: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly dialog: MatDialog,
    private readonly transloco: TranslocoService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.ruleService.getAll().subscribe((rules) => this.rules.set(rules));
  }

  openCreate(): void {
    this.openForm({});
  }

  openEdit(rule: ValidationRule): void {
    this.openForm({ rule });
  }

  private openForm(data: RuleFormDialogData): void {
    this.dialog
      .open(RuleFormDialog, { data, width: '460px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        const request$ = data.rule
          ? this.ruleService.update(data.rule.id, result)
          : this.ruleService.create(result);
        request$.subscribe(() => {
          this.notification.success('rules.saveSuccess');
          this.load();
        });
      });
  }

  delete(rule: ValidationRule): void {
    this.confirmDialog
      .open({
        title: this.transloco.translate('rules.deleteConfirmTitle'),
        message: this.transloco.translate('rules.deleteConfirmMessage', { name: rule.ruleName }),
        danger: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.ruleService.delete(rule.id).subscribe(() => {
          this.rules.update((list) => list.filter((r) => r.id !== rule.id));
          this.notification.success('rules.deleteSuccess');
        });
      });
  }
}
