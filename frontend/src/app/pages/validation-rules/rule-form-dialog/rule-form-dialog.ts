import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoModule } from '@jsverse/transloco';
import { RuleType, ValidationRule, ValidationRuleRequest } from '../../../core/models/validation-rule.model';

export interface RuleFormDialogData {
  rule?: ValidationRule;
}

const RULE_TYPES: RuleType[] = ['NOT_NULL', 'UNIQUE', 'REGEX', 'RANGE', 'ENUM', 'REQUIRED_COLUMN'];

@Component({
  selector: 'app-rule-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    TranslocoModule,
  ],
  templateUrl: './rule-form-dialog.html',
  styleUrl: './rule-form-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RuleFormDialog {
  readonly ruleTypes = RULE_TYPES;
  readonly isEditMode: boolean;

  form: ValidationRuleRequest;

  constructor(
    private readonly dialogRef: MatDialogRef<RuleFormDialog, ValidationRuleRequest>,
    @Inject(MAT_DIALOG_DATA) data: RuleFormDialogData,
  ) {
    this.isEditMode = !!data.rule;
    this.form = data.rule
      ? {
          ruleName: data.rule.ruleName,
          columnName: data.rule.columnName,
          ruleType: data.rule.ruleType,
          ruleValue: data.rule.ruleValue,
          isActive: data.rule.isActive,
          description: data.rule.description,
        }
      : {
          ruleName: '',
          columnName: '',
          ruleType: 'NOT_NULL',
          ruleValue: '',
          isActive: true,
          description: '',
        };
  }

  get isValid(): boolean {
    return !!this.form.ruleName && !!this.form.columnName && !!this.form.ruleType;
  }

  save(): void {
    if (!this.isValid) return;
    this.dialogRef.close(this.form);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
