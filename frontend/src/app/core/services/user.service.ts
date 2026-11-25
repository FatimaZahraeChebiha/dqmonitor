import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChangePasswordRequest, CreateUserRequest, User, UserStats } from '../models/user.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl = `${environment.apiUrl}/api/users`;

  constructor(private readonly http: HttpClient) {}

  getPage(page: number, size: number): Observable<PageResponse<User>> {
    return this.http.get<PageResponse<User>>(this.baseUrl, {
      params: { page: page.toString(), size: size.toString() },
    });
  }

  getStats(): Observable<UserStats> {
    return this.http.get<UserStats>(`${this.baseUrl}/stats`);
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.baseUrl, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  changeOwnPassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/me/password`, request);
  }
}
