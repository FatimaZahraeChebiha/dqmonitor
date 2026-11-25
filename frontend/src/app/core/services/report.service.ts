import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { QualityReport } from '../models/report.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly baseUrl = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  getPage(page: number, size: number): Observable<PageResponse<QualityReport>> {
    return this.http.get<PageResponse<QualityReport>>(this.baseUrl, {
      params: { page: page.toString(), size: size.toString() },
    });
  }

  getById(id: number): Observable<QualityReport> {
    return this.http.get<QualityReport>(`${this.baseUrl}/${id}`);
  }

  getByDataset(datasetId: number): Observable<QualityReport[]> {
    return this.http.get<QualityReport[]>(`${this.baseUrl}/dataset/${datasetId}`);
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/pdf`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
