import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { LanguageService } from '../../core/services/language.service';
import { ThemeService } from '../../core/services/theme.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardSummary } from '../../core/models/dashboard.model';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

const BRAND_LINE = '#3B6EA8';
const BRAND_FILL = 'rgba(59, 110, 168, 0.1)';

// Chart.js draws onto a <canvas>, which can't read CSS custom properties -
// axis/grid colors that read fine as "black at low opacity" against the light
// theme's white surface would be nearly invisible against the dark theme's
// near-black surface, so these need an explicit light/dark pair rather than
// a single constant.
const GRID_COLOR = { light: 'rgba(0, 0, 0, 0.08)', dark: 'rgba(255, 255, 255, 0.14)' };
const AXIS_TEXT_COLOR = { light: 'rgba(0, 0, 0, 0.6)', dark: 'rgba(255, 255, 255, 0.7)' };
const POINT_BORDER_COLOR = { light: '#ffffff', dark: '#1d2024' };
// Mirrors --dq-status-success/warning/error from material-theme.scss - same
// values already used for every badge/score color elsewhere on this page,
// duplicated here only because canvas can't read CSS custom properties.
const STATUS_COLOR = {
  light: { success: '#1e8e5a', warning: '#b7791f', error: '#c62828' },
  dark: { success: '#34d399', warning: '#fbbf24', error: '#f87171' },
};

const EMPTY_SUMMARY: DashboardSummary = {
  avgQualityScore: 0,
  totalFiles: 0,
  totalAnomalies: 0,
  totalValidations: 0,
  scoreTrend: [],
  anomalyDistribution: [],
  topProblematicColumns: [],
  recentActivity: [],
  qualityDimensions: [],
  datasetStatusDistribution: { compliant: 0, warning: 0, critical: 0 },
  analysisVolumeTrend: [],
  datasetsNeedingAttention: [],
};

// Maps the backend's stable dimension key to its translation key - kept next
// to EMPTY_SUMMARY rather than in i18n itself so the fixed 4-dimension order
// the backend already guarantees (see DashboardService.DIMENSION_ORDER) is
// mirrored here instead of depending on JSON key order.
const DIMENSION_LABEL_KEYS: Record<string, string> = {
  COMPLETENESS: 'dashboard.dimension.completeness',
  VALIDITY: 'dashboard.dimension.validity',
  UNIQUENESS: 'dashboard.dimension.uniqueness',
  CONFORMITY: 'dashboard.dimension.conformity',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, MatCardModule, MatIconModule, TranslocoModule, BaseChartDirective, LocalizedDatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    // Only the line/bar chart registerables this page actually uses, scoped to
    // this lazy-loaded route instead of the root app config - Chart.js is large
    // enough that registering everything ("withDefaultRegisterables") in the
    // root providers was pulling the whole library into the eager main bundle.
    provideCharts({
      registerables: [
        LineController,
        BarController,
        DoughnutController,
        LineElement,
        BarElement,
        ArcElement,
        PointElement,
        CategoryScale,
        LinearScale,
        Legend,
        Tooltip,
        Filler,
      ],
    }),
  ],
})
export class Dashboard implements OnInit {
  summary = signal<DashboardSummary>(EMPTY_SUMMARY);

  scoreTrendData = computed<ChartConfiguration<'line'>['data']>(() => {
    const trend = this.summary().scoreTrend;
    return {
      labels: trend.map((p) => new Date(p.date).toLocaleDateString()),
      datasets: [
        {
          data: trend.map((p) => p.score),
          borderColor: BRAND_LINE,
          backgroundColor: BRAND_FILL,
          borderWidth: 2,
          pointRadius: 4,
          pointBackgroundColor: BRAND_LINE,
          pointBorderColor: POINT_BORDER_COLOR[this.theme.currentTheme()],
          pointBorderWidth: 2,
          fill: true,
          tension: 0.3,
          // By default Chart.js clips a dataset's line/points to the exact
          // chartArea boundary - a point at 0% or 100% sits precisely on that
          // boundary, so half its marker gets clipped no matter how much
          // layout.padding surrounds the chart. Disabling clipping lets the
          // marker use that padding instead of being cut off by it.
          clip: false,
        },
      ],
    };
  });

  anomalyDistributionData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const distribution = this.summary().anomalyDistribution;
    return {
      labels: distribution.map((d) => d.checkName),
      datasets: [
        {
          data: distribution.map((d) => d.count),
          backgroundColor: BRAND_LINE,
          borderRadius: 4,
          maxBarThickness: 24,
        },
      ],
    };
  });

  statusDistributionData = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const dist = this.summary().datasetStatusDistribution;
    const colors = STATUS_COLOR[this.theme.currentTheme()];
    this.language.currentLang(); // establishes a signal dependency so labels re-translate on language switch
    return {
      labels: [
        this.transloco.translate('reports.scoreLabel.success'),
        this.transloco.translate('reports.scoreLabel.warning'),
        this.transloco.translate('reports.scoreLabel.error'),
      ],
      datasets: [
        {
          data: [dist.compliant, dist.warning, dist.critical],
          backgroundColor: [colors.success, colors.warning, colors.error],
          borderWidth: 0,
        },
      ],
    };
  });

  statusDistributionOptions = computed<ChartConfiguration<'doughnut'>['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    cutout: '65%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color: AXIS_TEXT_COLOR[this.theme.currentTheme()], boxWidth: 10, padding: 12 },
      },
    },
  }));

  analysisVolumeData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const trend = this.summary().analysisVolumeTrend;
    return {
      labels: trend.map((p) => new Date(p.weekStart).toLocaleDateString()),
      datasets: [
        {
          data: trend.map((p) => p.count),
          backgroundColor: BRAND_LINE,
          borderRadius: 4,
          maxBarThickness: 28,
        },
      ],
    };
  });

  analysisVolumeOptions = computed<ChartConfiguration<'bar'>['options']>(() => {
    const gridColor = GRID_COLOR[this.theme.currentTheme()];
    const axisTextColor = AXIS_TEXT_COLOR[this.theme.currentTheme()];
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { color: axisTextColor } },
        y: {
          beginAtZero: true,
          ticks: { color: axisTextColor, precision: 0 },
          grid: { color: gridColor },
        },
      },
    };
  });

  lineChartOptions = computed<ChartConfiguration<'line'>['options']>(() => {
    const gridColor = GRID_COLOR[this.theme.currentTheme()];
    const axisTextColor = AXIS_TEXT_COLOR[this.theme.currentTheme()];
    return {
      responsive: true,
      maintainAspectRatio: false,
      // A point sitting exactly at 0% or 100% is centered right on the plot
      // area's edge, so its marker (radius 4 + a 2px border, ~6px of visual
      // radius) gets clipped in half by Chart.js's default clip-to-chart-area
      // behavior. 14px gives that marker over double the clearance it needs,
      // rather than the ~2px margin an exact-fit padding value would leave -
      // that thin a margin is easily eaten by sub-pixel rounding or
      // devicePixelRatio scaling on high-DPI displays. Padding the layout (not
      // the 0-100% scale bounds, which must stay exact) gives the canvas that
      // breathing room above/below so every marker renders as a full circle.
      layout: { padding: { top: 14, bottom: 14 } },
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { color: axisTextColor } },
        y: {
          min: 0,
          max: 100,
          grid: { color: gridColor },
          ticks: { color: axisTextColor, callback: (value) => `${value}%` },
        },
      },
    };
  });

  barChartOptions = computed<ChartConfiguration<'bar'>['options']>(() => {
    const gridColor = GRID_COLOR[this.theme.currentTheme()];
    const axisTextColor = AXIS_TEXT_COLOR[this.theme.currentTheme()];
    return {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: { display: false },
        // Great Expectations check names (e.g.
        // "expect_column_values_to_match_regex_email") are long identifiers, not
        // display labels - truncate on the axis but show the full name on hover.
        tooltip: {
          callbacks: {
            title: (items) => items.map((item) => item.label).join(', '),
          },
        },
      },
      scales: {
        x: { grid: { color: gridColor }, ticks: { color: axisTextColor } },
        y: {
          grid: { display: false },
          ticks: {
            color: axisTextColor,
            callback(value) {
              const label = this.getLabelForValue(value as number);
              return label.length > 22 ? `${label.slice(0, 21)}…` : label;
            },
          },
        },
      },
    };
  });

  constructor(
    readonly language: LanguageService,
    readonly theme: ThemeService,
    private readonly dashboardService: DashboardService,
    private readonly transloco: TranslocoService,
  ) {}

  ngOnInit(): void {
    this.dashboardService.getSummary().subscribe((summary) => this.summary.set(summary));
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

  dimensionLabelKey(dimension: string): string {
    return DIMENSION_LABEL_KEYS[dimension] ?? dimension;
  }
}
