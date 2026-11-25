import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoModule } from '@jsverse/transloco';
import { Anomaly, AnomalyFilters, AnomalyGroupCount, AnomalyStats } from '../../../core/models/anomaly.model';
import { CorrectionRecord, CorrectionRequest } from '../../../core/models/correction.model';
import { Dataset } from '../../../core/models/dataset.model';
import { QualityReport } from '../../../core/models/report.model';
import { AnomalyService } from '../../../core/services/anomaly.service';
import { ReportService } from '../../../core/services/report.service';
import { DatasetService } from '../../../core/services/dataset.service';
import { CorrectionService } from '../../../core/services/correction.service';
import { NotificationService } from '../../../core/services/notification.service';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { LanguageService } from '../../../core/services/language.service';
import { AnomalyDetailDialog } from './anomaly-detail-dialog/anomaly-detail-dialog';
import { AnomalyEditDialog } from './anomaly-edit-dialog/anomaly-edit-dialog';

type GroupDimension = 'column' | 'ruleName' | 'checkType';

interface PendingCorrection {
  key: string;
  rowIndex: number;
  columnName: string;
  oldValue: string | null;
  newValue: string;
}

const POLL_DELAYS_MS = [2000, 3000, 5000, 8000, 10000];
const MAX_POLL_ATTEMPTS = 24;

@Component({
  selector: 'app-anomaly-explorer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatTooltipModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './anomaly-explorer.html',
  styleUrl: './anomaly-explorer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnomalyExplorer implements OnInit, OnDestroy {
  reportId = 0;
  report = signal<QualityReport | null>(null);
  dataset = signal<Dataset | null>(null);
  stats = signal<AnomalyStats | null>(null);
  anomalies = signal<Anomaly[]>([]);
  total = signal(0);
  page = signal(0);
  pageSize = signal(25);
  isLoading = signal(true);
  isExporting = signal(false);
  isSaving = signal(false);
  isReanalyzing = signal(false);
  isDownloading = signal(false);
  private destroyed = false;

  selectedColumn = signal<string | null>(null);
  selectedRule = signal<string | null>(null);
  selectedType = signal<string | null>(null);
  searchText = '';
  private activeSearch = '';

  activeGroupTab = signal<GroupDimension>('column');

  pendingCorrections = signal<Map<string, PendingCorrection>>(new Map());
  pendingList = computed(() => Array.from(this.pendingCorrections().values()));

  showHistory = signal(false);
  history = signal<CorrectionRecord[]>([]);

  // Matches FileCorrectionService's supported formats on the backend.
  canCorrect = computed(() => {
    const fileName = this.report()?.dataset.fileName;
    if (!fileName) return false;
    const ext = fileName.split('.').pop()?.toLowerCase();
    return ext === 'csv' || ext === 'json' || ext === 'xlsx';
  });

  constructor(
    readonly language: LanguageService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly anomalyService: AnomalyService,
    private readonly reportService: ReportService,
    private readonly datasetService: DatasetService,
    private readonly correctionService: CorrectionService,
    private readonly notification: NotificationService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.reportId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadReportAndDataset();
    this.loadStats();
    this.loadAnomalies();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
  }

  loadReportAndDataset(): void {
    this.reportService.getById(this.reportId).subscribe((report) => {
      this.report.set(report);
      this.datasetService.getById(report.dataset.id).subscribe((dataset) => this.dataset.set(dataset));
    });
  }

  loadStats(): void {
    this.anomalyService.getStats(this.reportId).subscribe((stats) => this.stats.set(stats));
  }

  loadAnomalies(event?: PageEvent): void {
    const page = event ? event.pageIndex : this.page();
    const size = event ? event.pageSize : this.pageSize();
    this.isLoading.set(true);
    this.anomalyService.search(this.reportId, page, size, this.currentFilters()).subscribe({
      next: (result) => {
        this.anomalies.set(result.content);
        this.total.set(result.totalElements);
        this.page.set(result.page);
        this.pageSize.set(result.size);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false),
    });
  }

  currentFilters(): AnomalyFilters {
    return {
      column: this.selectedColumn() ?? undefined,
      ruleName: this.selectedRule() ?? undefined,
      checkType: this.selectedType() ?? undefined,
      search: this.activeSearch || undefined,
    };
  }

  setGroupTab(dimension: GroupDimension): void {
    this.activeGroupTab.set(dimension);
  }

  toggleColumnFilter(group: AnomalyGroupCount): void {
    this.selectedColumn.set(this.selectedColumn() === group.key ? null : group.key);
    this.page.set(0);
    this.loadAnomalies();
  }

  toggleRuleFilter(group: AnomalyGroupCount): void {
    this.selectedRule.set(this.selectedRule() === group.key ? null : group.key);
    this.page.set(0);
    this.loadAnomalies();
  }

  toggleTypeFilter(group: AnomalyGroupCount): void {
    this.selectedType.set(this.selectedType() === group.key ? null : group.key);
    this.page.set(0);
    this.loadAnomalies();
  }

  onColumnSelect(value: string | null): void {
    this.selectedColumn.set(value);
    this.page.set(0);
    this.loadAnomalies();
  }

  onRuleSelect(value: string | null): void {
    this.selectedRule.set(value);
    this.page.set(0);
    this.loadAnomalies();
  }

  onTypeSelect(value: string | null): void {
    this.selectedType.set(value);
    this.page.set(0);
    this.loadAnomalies();
  }

  runSearch(): void {
    this.activeSearch = this.searchText.trim();
    this.page.set(0);
    this.loadAnomalies();
  }

  clearFilters(): void {
    this.selectedColumn.set(null);
    this.selectedRule.set(null);
    this.selectedType.set(null);
    this.searchText = '';
    this.activeSearch = '';
    this.page.set(0);
    this.loadAnomalies();
  }

  hasActiveFilters(): boolean {
    return !!(this.selectedColumn() || this.selectedRule() || this.selectedType() || this.activeSearch);
  }

  openDetail(anomaly: Anomaly): void {
    this.dialog.open(AnomalyDetailDialog, { data: { anomaly }, width: '420px' });
  }

  private pendingKey(rowIndex: number | null, columnName: string | null): string {
    return `${rowIndex}::${columnName}`;
  }

  pendingFor(anomaly: Anomaly): PendingCorrection | undefined {
    return this.pendingCorrections().get(this.pendingKey(anomaly.rowIndex, anomaly.columnName));
  }

  openEdit(anomaly: Anomaly, event: Event): void {
    event.stopPropagation();
    const existing = this.pendingFor(anomaly);
    this.dialog
      .open(AnomalyEditDialog, { data: { anomaly, pendingValue: existing?.newValue }, width: '420px' })
      .afterClosed()
      .subscribe((newValue: string | undefined) => {
        if (newValue === undefined || anomaly.rowIndex === null || anomaly.columnName === null) return;
        const key = this.pendingKey(anomaly.rowIndex, anomaly.columnName);
        const map = new Map(this.pendingCorrections());
        map.set(key, {
          key,
          rowIndex: anomaly.rowIndex,
          columnName: anomaly.columnName,
          oldValue: anomaly.value,
          newValue,
        });
        this.pendingCorrections.set(map);
      });
  }

  removePending(key: string): void {
    const map = new Map(this.pendingCorrections());
    map.delete(key);
    this.pendingCorrections.set(map);
  }

  clearPending(): void {
    this.pendingCorrections.set(new Map());
  }

  saveCorrections(): void {
    const requests: CorrectionRequest[] = this.pendingList().map((p) => ({
      rowIndex: p.rowIndex,
      columnName: p.columnName,
      newValue: p.newValue,
    }));
    if (requests.length === 0) return;

    this.isSaving.set(true);
    this.correctionService.apply(this.reportId, requests).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.clearPending();
        this.notification.success('anomalyExplorer.correction.saveSuccess');
        this.loadReportAndDataset();
        if (this.showHistory()) {
          this.loadHistory();
        }
      },
      error: () => {
        this.isSaving.set(false);
        this.notification.error('anomalyExplorer.correction.saveError');
      },
    });
  }

  toggleHistory(): void {
    this.showHistory.set(!this.showHistory());
    if (this.showHistory() && this.history().length === 0) {
      this.loadHistory();
    }
  }

  loadHistory(): void {
    this.correctionService.getHistory(this.reportId).subscribe((records) => this.history.set(records));
  }

  reanalyze(): void {
    const datasetId = this.dataset()?.id;
    if (!datasetId) return;
    this.isReanalyzing.set(true);
    this.datasetService.analyze(datasetId).subscribe({
      next: () => {
        this.notification.success('anomalyExplorer.correction.reanalyzeStarted');
        this.pollDatasetStatus(datasetId);
      },
      error: () => this.isReanalyzing.set(false),
    });
  }

  private pollDatasetStatus(datasetId: number, attempt = 0): void {
    if (this.destroyed) return;
    if (attempt >= MAX_POLL_ATTEMPTS) {
      this.isReanalyzing.set(false);
      return;
    }
    const delay = POLL_DELAYS_MS[Math.min(attempt, POLL_DELAYS_MS.length - 1)];
    setTimeout(() => {
      if (this.destroyed) return;
      this.datasetService.getById(datasetId).subscribe((updated) => {
        if (updated.status === 'PROCESSING' || updated.status === 'PENDING') {
          this.pollDatasetStatus(datasetId, attempt + 1);
          return;
        }
        this.isReanalyzing.set(false);
        if (updated.status === 'COMPLETED') {
          this.goToLatestReport(datasetId);
        } else {
          this.notification.error('anomalyExplorer.correction.reanalyzeError');
        }
      });
    }, delay);
  }

  private goToLatestReport(datasetId: number): void {
    this.reportService.getByDataset(datasetId).subscribe((reports) => {
      if (reports.length === 0) return;
      const latest = reports.reduce((a, b) => (a.id > b.id ? a : b));
      this.notification.success('anomalyExplorer.correction.reanalyzeDone');
      this.router.navigate(['/reports', latest.id]);
    });
  }

  downloadCorrected(): void {
    const datasetId = this.dataset()?.id;
    if (!datasetId) return;
    this.isDownloading.set(true);
    this.datasetService.downloadCorrectedFile(datasetId).subscribe({
      next: (blob) => {
        this.isDownloading.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `corrige-${this.report()?.dataset.fileName ?? 'dataset'}`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.isDownloading.set(false),
    });
  }

  exportAnomalies(): void {
    this.isExporting.set(true);
    this.anomalyService.export(this.reportId, this.currentFilters()).subscribe({
      next: (blob) => {
        this.isExporting.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `anomalies-report-${this.reportId}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.isExporting.set(false);
        this.notification.error('anomalyExplorer.exportError');
      },
    });
  }

  back(): void {
    this.router.navigate(['/reports', this.reportId]);
  }
}
