import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { QualityReport, ReportCheckResult, parseReportDetails } from '../../../core/models/report.model';
import { ReportService } from '../../../core/services/report.service';
import { LanguageService } from '../../../core/services/language.service';
import { NotificationService } from '../../../core/services/notification.service';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-report-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './report-detail.html',
  styleUrl: './report-detail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportDetail implements OnInit {
  report = signal<QualityReport | null>(null);
  checks = signal<ReportCheckResult[]>([]);
  isExporting = signal(false);

  constructor(
    readonly language: LanguageService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reportService: ReportService,
    private readonly notification: NotificationService,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.reportService.getById(id).subscribe((report) => {
      this.report.set(report);
      this.checks.set(parseReportDetails(report));
    });
  }

  back(): void {
    this.router.navigate(['/reports']);
  }

  exportPdf(id: number): void {
    this.isExporting.set(true);
    this.reportService.downloadPdf(id).subscribe({
      next: (blob) => {
        this.isExporting.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `report-${id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.isExporting.set(false);
        this.notification.error('reports.detail.exportPdfError');
      },
    });
  }

  scoreClass(score: number): string {
    if (score >= 80) return 'success';
    if (score >= 50) return 'warning';
    return 'error';
  }
}
