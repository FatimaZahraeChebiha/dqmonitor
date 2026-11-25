import { QualityReport } from './report.model';

export interface ScoreTrendPoint {
  date: string;
  score: number;
}

export interface AnomalyDistributionEntry {
  checkName: string;
  count: number;
}

export interface ColumnAnomalyEntry {
  columnName: string;
  count: number;
  percentage: number;
}

// dimension is a stable key (COMPLETENESS, VALIDITY, UNIQUENESS, CONFORMITY),
// not a display label - the template maps it to a translated string, same
// pattern already used for score/status badges elsewhere on this page.
export interface QualityDimensionEntry {
  dimension: string;
  score: number;
}

// Counts datasets by their latest report's score, using the same >=80/>=50
// thresholds as scoreClass() elsewhere on this page - a dataset is never
// "compliant" here and "warning" somewhere else.
export interface DatasetStatusDistribution {
  compliant: number;
  warning: number;
  critical: number;
}

// One point per week, including weeks with zero analyses - a real zero, not
// a missing data point, so the series has no gaps.
export interface AnalysisVolumePoint {
  weekStart: string;
  count: number;
}

// A dataset whose latest report scored below the compliant threshold,
// worst-first. reportId links straight to that report.
export interface DatasetAttentionEntry {
  reportId: number;
  fileName: string;
  score: number;
  generatedAt: string;
}

export interface DashboardSummary {
  avgQualityScore: number;
  totalFiles: number;
  totalAnomalies: number;
  totalValidations: number;
  scoreTrend: ScoreTrendPoint[];
  anomalyDistribution: AnomalyDistributionEntry[];
  topProblematicColumns: ColumnAnomalyEntry[];
  // One entry per file name (its most recent analysis only) - neither a
  // dataset re-analyzed several times nor a file re-imported as a new
  // dataset contributes more than a single row here.
  recentActivity: QualityReport[];
  qualityDimensions: QualityDimensionEntry[];
  datasetStatusDistribution: DatasetStatusDistribution;
  analysisVolumeTrend: AnalysisVolumePoint[];
  datasetsNeedingAttention: DatasetAttentionEntry[];
}
