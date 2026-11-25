import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { LoginResponse } from '../models/auth.model';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const loginResponse: LoginResponse = {
    token: 'access-token-1',
    expiresInMs: 900_000,
    refreshToken: 'refresh-token-1',
    user: { id: 1, username: 'Admin', email: 'admin@dqmonitor.com', role: 'ADMIN', isActive: true, createdAt: '' },
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts unauthenticated with no stored session', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
  });

  it('login stores the session and flips isAuthenticated to true', () => {
    service.login({ email: 'admin@dqmonitor.com', password: 'Admin2026' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush(loginResponse);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe('access-token-1');
    expect(service.isAdmin()).toBe(true);
  });

  it('persists the session to sessionStorage so a page refresh does not log the user out', () => {
    service.login({ email: 'admin@dqmonitor.com', password: 'Admin2026' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/api/auth/login`).flush(loginResponse);

    const stored = JSON.parse(sessionStorage.getItem('dqmonitor.session')!);
    expect(stored.token).toBe('access-token-1');
    expect(stored.refreshToken).toBe('refresh-token-1');
  });

  it('logout clears the session even if the server call fails', () => {
    service.login({ email: 'admin@dqmonitor.com', password: 'Admin2026' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/api/auth/login`).flush(loginResponse);

    service.logout();
    httpMock.expectOne(`${environment.apiUrl}/api/auth/logout`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
    expect(sessionStorage.getItem('dqmonitor.session')).toBeNull();
  });

  it('shares one in-flight refresh call across concurrent callers instead of firing two', () => {
    service.login({ email: 'admin@dqmonitor.com', password: 'Admin2026' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/api/auth/login`).flush(loginResponse);

    let firstToken: string | undefined;
    let secondToken: string | undefined;
    service.refreshAccessToken().subscribe((token) => (firstToken = token));
    service.refreshAccessToken().subscribe((token) => (secondToken = token));

    const requests = httpMock.match(`${environment.apiUrl}/api/auth/refresh`);
    expect(requests.length).toBe(1);
    requests[0].flush({ token: 'access-token-2', expiresInMs: 900_000, refreshToken: 'refresh-token-2' });

    expect(firstToken).toBe('access-token-2');
    expect(secondToken).toBe('access-token-2');
  });
});
