import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Dataset } from '../models/dataset.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class DatasetService {
  private readonly baseUrl = `${environment.apiUrl}/api/datasets`;

  constructor(private readonly http: HttpClient) {}

  getPage(page: number, size: number): Observable<PageResponse<Dataset>> {
    return this.http.get<PageResponse<Dataset>>(this.baseUrl, {
      params: { page: page.toString(), size: size.toString() },
    });
  }

  getById(id: number): Observable<Dataset> {
    return this.http.get<Dataset>(`${this.baseUrl}/${id}`);
  }

  upload(file: File): Observable<Dataset> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Dataset>(`${this.baseUrl}/upload`, formData);
  }

  analyze(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/analyze`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  downloadCorrectedFile(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/corrected-file`, { responseType: 'blob' });
  }
}
