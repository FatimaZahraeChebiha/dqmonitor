export interface Anomaly {
  id: number;
  rowIndex: number | null;
  columnName: string | null;
  ruleName: string;
  checkType: string;
  value: string | null;
}

export interface AnomalyGroupCount {
  key: string;
  count: number;
}

export interface AnomalyStats {
  totalAnomalies: number;
  byColumn: AnomalyGroupCount[];
  byRule: AnomalyGroupCount[];
  byType: AnomalyGroupCount[];
  topColumn: AnomalyGroupCount | null;
  topColumnPct: number | null;
  // false for reports generated before the Anomaly Explorer existed - the old
  // pipeline never persisted more than a capped 20-example sample per rule, so
  // there is nothing complete to show without re-analyzing the dataset.
  hasFullData: boolean;
}

export interface AnomalyFilters {
  column?: string;
  ruleName?: string;
  checkType?: string;
  search?: string;
}
