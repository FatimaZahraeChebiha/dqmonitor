export interface ReportCheckResult {
  check_name: string;
  success: boolean;
  unexpected_count: number;
  unexpected_pct: number;
}

export interface DatasetSummary {
  id: number;
  fileName: string;
}

export interface QualityReport {
  id: number;
  dataset: DatasetSummary;
  qualityScore: number;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  status: string;
  generatedAt: string;
  reportDetails: string; // JSON-encoded ReportCheckResult[] - parse with parseReportDetails()
}

export function parseReportDetails(report: QualityReport): ReportCheckResult[] {
  // reportDetails is null for a failed analysis (no checks ever ran) - guard
  // against that in addition to malformed JSON, since JSON.parse(null) parses
  // successfully to the value null rather than throwing.
  try {
    const parsed = JSON.parse(report.reportDetails) as unknown;
    return Array.isArray(parsed) ? (parsed as ReportCheckResult[]) : [];
  } catch {
    return [];
  }
}
