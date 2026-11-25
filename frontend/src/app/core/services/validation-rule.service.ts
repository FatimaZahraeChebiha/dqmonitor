import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ValidationRule, ValidationRuleRequest } from '../models/validation-rule.model';

@Injectable({ providedIn: 'root' })
export class ValidationRuleService {
  private readonly baseUrl = `${environment.apiUrl}/api/validation-rules`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<ValidationRule[]> {
    return this.http.get<ValidationRule[]>(this.baseUrl);
  }

  create(request: ValidationRuleRequest): Observable<ValidationRule> {
    return this.http.post<ValidationRule>(this.baseUrl, request);
  }

  update(id: number, request: ValidationRuleRequest): Observable<ValidationRule> {
    return this.http.put<ValidationRule>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
