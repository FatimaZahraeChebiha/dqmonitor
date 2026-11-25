import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Anomaly, AnomalyFilters, AnomalyStats } from '../models/anomaly.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class AnomalyService {
  private readonly baseUrl = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  search(reportId: number, page: number, size: number, filters: AnomalyFilters): Observable<PageResponse<Anomaly>> {
    return this.http.get<PageResponse<Anomaly>>(`${this.baseUrl}/${reportId}/anomalies`, {
      params: this.buildParams(page, size, filters),
    });
  }

  getStats(reportId: number): Observable<AnomalyStats> {
    return this.http.get<AnomalyStats>(`${this.baseUrl}/${reportId}/anomalies/stats`);
  }

  export(reportId: number, filters: AnomalyFilters): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${reportId}/anomalies/export`, {
      params: this.buildParams(undefined, undefined, filters),
      responseType: 'blob',
    });
  }

  private buildParams(page: number | undefined, size: number | undefined, filters: AnomalyFilters): HttpParams {
    let params = new HttpParams();
    if (page !== undefined) params = params.set('page', page.toString());
    if (size !== undefined) params = params.set('size', size.toString());
    if (filters.column) params = params.set('column', filters.column);
    if (filters.ruleName) params = params.set('ruleName', filters.ruleName);
    if (filters.checkType) params = params.set('checkType', filters.checkType);
    if (filters.search) params = params.set('search', filters.search);
    return params;
  }
}
