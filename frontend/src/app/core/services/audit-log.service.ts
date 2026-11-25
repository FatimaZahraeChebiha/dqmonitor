import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLog } from '../models/audit-log.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private readonly baseUrl = `${environment.apiUrl}/api/audit-logs`;

  constructor(private readonly http: HttpClient) {}

  getPage(page: number, size: number): Observable<PageResponse<AuditLog>> {
    return this.http.get<PageResponse<AuditLog>>(this.baseUrl, {
      params: { page: page.toString(), size: size.toString() },
    });
  }
}
