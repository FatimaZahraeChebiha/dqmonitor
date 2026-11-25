export interface CorrectionRequest {
  rowIndex: number;
  columnName: string;
  newValue: string;
}

export interface CorrectionRecord {
  id: number;
  rowIndex: number;
  columnName: string;
  oldValue: string | null;
  newValue: string | null;
  correctedBy: string | null;
  correctedAt: string;
}
