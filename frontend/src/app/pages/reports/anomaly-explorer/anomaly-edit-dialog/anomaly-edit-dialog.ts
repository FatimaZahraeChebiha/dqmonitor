import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoModule } from '@jsverse/transloco';
import { Anomaly } from '../../../../core/models/anomaly.model';

export interface AnomalyEditDialogData {
  anomaly: Anomaly;
  /** A value already staged for this same cell in the current batch, if any - lets re-opening the editor show the pending edit instead of the original. */
  pendingValue?: string;
}

@Component({
  selector: 'app-anomaly-edit-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslocoModule],
  templateUrl: './anomaly-edit-dialog.html',
  styleUrl: './anomaly-edit-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnomalyEditDialog {
  readonly anomaly: Anomaly;
  newValue: string;

  constructor(
    private readonly dialogRef: MatDialogRef<AnomalyEditDialog, string>,
    @Inject(MAT_DIALOG_DATA) data: AnomalyEditDialogData,
  ) {
    this.anomaly = data.anomaly;
    this.newValue = data.pendingValue ?? data.anomaly.value ?? '';
  }

  save(): void {
    this.dialogRef.close(this.newValue);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
