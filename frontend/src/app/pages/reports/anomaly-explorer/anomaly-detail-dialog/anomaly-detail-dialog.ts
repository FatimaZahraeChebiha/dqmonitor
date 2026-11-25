import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoModule } from '@jsverse/transloco';
import { Anomaly } from '../../../../core/models/anomaly.model';

export interface AnomalyDetailDialogData {
  anomaly: Anomaly;
}

@Component({
  selector: 'app-anomaly-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, TranslocoModule],
  templateUrl: './anomaly-detail-dialog.html',
  styleUrl: './anomaly-detail-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnomalyDetailDialog {
  readonly anomaly: Anomaly;

  constructor(@Inject(MAT_DIALOG_DATA) data: AnomalyDetailDialogData) {
    this.anomaly = data.anomaly;
  }
}
