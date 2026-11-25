import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoModule } from '@jsverse/transloco';
import { forkJoin } from 'rxjs';
import { QualityReport, ReportCheckResult, parseReportDetails } from '../../../core/models/report.model';
import { ReportService } from '../../../core/services/report.service';
import { LanguageService } from '../../../core/services/language.service';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

export type CompareStatus =
  | 'NEW'
  | 'REMOVED'
  | 'FIXED'
  | 'REGRESSION'
  | 'IMPROVED'
  | 'DEGRADED'
  | 'UNCHANGED'
  | 'STILL_COMPLIANT';

export interface CompareRow {
  checkName: string;
  before: ReportCheckResult | null;
  after: ReportCheckResult | null;
  status: CompareStatus;
}

/**
 * Classifies one check across two reports. Matched by check_name (the same
 * identifier already shown as-is in the single-report detail view), since a
 * check's identity comes from its rule + column, not from row position in the
 * reportDetails array.
 */
export function classifyCheck(before: ReportCheckResult | null, after: ReportCheckResult | null): CompareStatus {
  if (!before && after) return 'NEW';
  if (before && !after) return 'REMOVED';
  if (!before || !after) return 'UNCHANGED'; // unreachable, satisfies the type checker

  if (before.success && after.success) return 'STILL_COMPLIANT';
  if (!before.success && after.success) return 'FIXED';
  if (before.success && !after.success) return 'REGRESSION';
  // Both failing - still meaningful to know whether it got better or worse.
  if (after.unexpected_count < before.unexpected_count) return 'IMPROVED';
  if (after.unexpected_count > before.unexpected_count) return 'DEGRADED';
  return 'UNCHANGED';
}

export function buildCompareRows(before: ReportCheckResult[], after: ReportCheckResult[]): CompareRow[] {
  const beforeByName = new Map(before.map((c) => [c.check_name, c]));
  const afterByName = new Map(after.map((c) => [c.check_name, c]));
  const allNames = [...new Set([...beforeByName.keys(), ...afterByName.keys()])];

  return allNames.map((checkName) => {
    const b = beforeByName.get(checkName) ?? null;
    const a = afterByName.get(checkName) ?? null;
    return { checkName, before: b, after: a, status: classifyCheck(b, a) };
  });
}

const STATUS_BADGE_CLASS: Record<CompareStatus, string> = {
  NEW: 'neutral',
  REMOVED: 'neutral',
  FIXED: 'success',
  REGRESSION: 'error',
  IMPROVED: 'success',
  DEGRADED: 'warning',
  UNCHANGED: 'neutral',
  STILL_COMPLIANT: 'success',
};

const STATUS_LABEL_KEYS: Record<CompareStatus, string> = {
  NEW: 'reports.compare.status.new',
  REMOVED: 'reports.compare.status.removed',
  FIXED: 'reports.compare.status.fixed',
  REGRESSION: 'reports.compare.status.regression',
  IMPROVED: 'reports.compare.status.improved',
  DEGRADED: 'reports.compare.status.degraded',
  UNCHANGED: 'reports.compare.status.unchanged',
  STILL_COMPLIANT: 'reports.compare.status.stillCompliant',
};

@Component({
  selector: 'app-reports-compare',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, TranslocoModule, LocalizedDatePipe],
  templateUrl: './reports-compare.html',
  styleUrl: './reports-compare.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsCompare implements OnInit {
  reportA = signal<QualityReport | null>(null);
  reportB = signal<QualityReport | null>(null);
  loadError = signal(false);

  rows = computed<CompareRow[]>(() => {
    const a = this.reportA();
    const b = this.reportB();
    if (!a || !b) return [];
    return buildCompareRows(parseReportDetails(a), parseReportDetails(b));
  });

  scoreDelta = computed(() => {
    const a = this.reportA();
    const b = this.reportB();
    if (!a || !b) return 0;
    return Math.round((b.qualityScore - a.qualityScore) * 10) / 10;
  });

  constructor(
    readonly language: LanguageService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reportService: ReportService,
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const idA = Number(params.get('a'));
    const idB = Number(params.get('b'));
    if (!idA || !idB) {
      this.loadError.set(true);
      return;
    }
    forkJoin([this.reportService.getById(idA), this.reportService.getById(idB)]).subscribe({
      next: ([a, b]) => {
        // Always show chronologically: the older analysis on the left, the
        // newer one on the right, regardless of selection order in the table.
        if (new Date(a.generatedAt).getTime() <= new Date(b.generatedAt).getTime()) {
          this.reportA.set(a);
          this.reportB.set(b);
        } else {
          this.reportA.set(b);
          this.reportB.set(a);
        }
      },
      error: () => this.loadError.set(true),
    });
  }

  back(): void {
    this.router.navigate(['/reports']);
  }

  badgeClass(status: CompareStatus): string {
    return STATUS_BADGE_CLASS[status];
  }

  statusLabelKey(status: CompareStatus): string {
    return STATUS_LABEL_KEYS[status];
  }

  scoreClass(score: number): string {
    if (score >= 80) return 'success';
    if (score >= 50) return 'warning';
    return 'error';
  }
}
