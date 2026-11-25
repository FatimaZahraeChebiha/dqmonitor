import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CorrectionRecord, CorrectionRequest } from '../models/correction.model';

@Injectable({ providedIn: 'root' })
export class CorrectionService {
  private readonly baseUrl = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  apply(reportId: number, corrections: CorrectionRequest[]): Observable<CorrectionRecord[]> {
    return this.http.post<CorrectionRecord[]>(`${this.baseUrl}/${reportId}/corrections`, { corrections });
  }

  getHistory(reportId: number): Observable<CorrectionRecord[]> {
    return this.http.get<CorrectionRecord[]>(`${this.baseUrl}/${reportId}/corrections`);
  }
}
