export type DatasetStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface Dataset {
  id: number;
  fileName: string;
  fileSize: number;
  status: DatasetStatus;
  uploadedByUsername: string | null;
  uploadedAt: string;
  hasCorrectedFile: boolean;
  lastCorrectedAt: string | null;
}
