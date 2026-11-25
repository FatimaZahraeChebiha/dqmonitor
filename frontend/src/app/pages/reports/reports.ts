import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { QualityReport } from '../../core/models/report.model';
import { ReportService } from '../../core/services/report.service';
import { LanguageService } from '../../core/services/language.service';
import { NotificationService } from '../../core/services/notification.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatMenuModule,
    MatPaginatorModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './reports.html',
  styleUrl: './reports.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Reports implements OnInit {
  reports = signal<QualityReport[]>([]);
  total = signal(0);
  page = signal(0);
  pageSize = signal(10);

  // Comparison only ever makes sense between exactly two analyses - a third
  // selection replaces the oldest one instead of piling up, so the "Comparer"
  // button's enabled state never has to explain a >2 case to the user.
  selectedIds = signal<number[]>([]);

  constructor(
    readonly language: LanguageService,
    private readonly reportService: ReportService,
    private readonly router: Router,
    private readonly notification: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly transloco: TranslocoService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(event?: PageEvent): void {
    const page = event ? event.pageIndex : this.page();
    const size = event ? event.pageSize : this.pageSize();
    this.reportService.getPage(page, size).subscribe((result) => {
      this.reports.set(result.content);
      this.total.set(result.totalElements);
      this.page.set(result.page);
      this.pageSize.set(result.size);
      // A selection made on a page the user has since paged away from can no
      // longer be acted on with both rows visible - drop it rather than let
      // "Comparer" navigate with a stale id.
      this.selectedIds.set([]);
    });
  }

  openDetail(report: QualityReport): void {
    this.router.navigate(['/reports', report.id]);
  }

  isSelected(report: QualityReport): boolean {
    return this.selectedIds().includes(report.id);
  }

  toggleSelection(report: QualityReport, event: Event): void {
    event.stopPropagation();
    const current = this.selectedIds();
    if (current.includes(report.id)) {
      this.selectedIds.set(current.filter((id) => id !== report.id));
    } else if (current.length < 2) {
      this.selectedIds.set([...current, report.id]);
    } else {
      this.selectedIds.set([current[1], report.id]);
    }
  }

  deleteReport(report: QualityReport, event: Event): void {
    event.stopPropagation();
    this.confirmDialog
      .open({
        title: this.transloco.translate('reports.deleteConfirmTitle'),
        message: this.transloco.translate('reports.deleteConfirmMessage', { name: report.dataset.fileName }),
        danger: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.reportService.delete(report.id).subscribe(() => {
          this.notification.success('reports.deleteSuccess');
          this.selectedIds.set(this.selectedIds().filter((id) => id !== report.id));
          this.load();
        });
      });
  }

  compareSelected(): void {
    const [a, b] = this.selectedIds();
    if (a === undefined || b === undefined) return;
    this.router.navigate(['/reports/compare'], { queryParams: { a, b } });
  }

  scoreClass(score: number): string {
    if (score >= 80) return 'success';
    if (score >= 50) return 'warning';
    return 'error';
  }

  scoreLabelKey(score: number): string {
    if (score >= 80) return 'reports.scoreLabel.success';
    if (score >= 50) return 'reports.scoreLabel.warning';
    return 'reports.scoreLabel.error';
  }
}
