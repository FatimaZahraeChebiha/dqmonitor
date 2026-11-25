import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly baseUrl = `${environment.apiUrl}/api/admin`;

  constructor(private readonly http: HttpClient) {}

  resetDatabase(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/reset-database`);
  }
}
