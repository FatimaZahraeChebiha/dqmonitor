import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { errorInterceptor } from './error.interceptor';
import { AuthService } from '../services/auth.service';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';

describe('errorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let snackBarOpenSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [getTranslocoTestingModule()],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    snackBarOpenSpy = vi.fn();
    TestBed.inject(MatSnackBar).open = snackBarOpenSpy as unknown as MatSnackBar['open'];
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('retries a GET after a transient network error and succeeds without a toast', async () => {
    const result = vi.fn();
    httpClient.get('/api/datasets').subscribe(result);
    const first = httpMock.expectOne('/api/datasets');

    vi.useFakeTimers();
    first.error(new ProgressEvent('error'));
    await vi.runAllTimersAsync();
    vi.useRealTimers();

    httpMock.expectOne('/api/datasets').flush([{ id: 1 }]);

    expect(result).toHaveBeenCalledWith([{ id: 1 }]);
    expect(snackBarOpenSpy).not.toHaveBeenCalled();
  });

  it('shows the error toast once the network-retry budget is exhausted', async () => {
    const errorSpy = vi.fn();
    httpClient.get('/api/datasets').subscribe({ error: errorSpy });

    // 1 initial attempt + 2 retries = 3 requests total, all failing.
    for (let i = 0; i < 3; i++) {
      const req = httpMock.expectOne('/api/datasets');
      vi.useFakeTimers();
      req.error(new ProgressEvent('error'));
      await vi.runAllTimersAsync();
      vi.useRealTimers();
    }

    expect(errorSpy).toHaveBeenCalled();
    expect(snackBarOpenSpy).toHaveBeenCalled();
  });

  it('does not retry a mutating request on a transient network error', async () => {
    const errorSpy = vi.fn();
    httpClient.post('/api/datasets/1/analyze', {}).subscribe({ error: errorSpy });
    const req = httpMock.expectOne('/api/datasets/1/analyze');

    vi.useFakeTimers();
    req.error(new ProgressEvent('error'));
    await vi.runAllTimersAsync();
    vi.useRealTimers();

    httpMock.verify();
    expect(errorSpy).toHaveBeenCalled();
    expect(snackBarOpenSpy).toHaveBeenCalled();
  });

  it('does not retry a real business error (404) on a GET', async () => {
    const errorSpy = vi.fn();
    httpClient.get('/api/datasets/999').subscribe({ error: errorSpy });
    const req = httpMock.expectOne('/api/datasets/999');

    vi.useFakeTimers();
    req.flush({ code: 'DATASET_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await vi.runAllTimersAsync();
    vi.useRealTimers();

    httpMock.verify();
    expect(errorSpy).toHaveBeenCalled();
    expect(snackBarOpenSpy).toHaveBeenCalled();
  });

  it('still exchanges an expired access token via refresh on a 401, unaffected by the network retry', async () => {
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'refreshAccessToken').mockReturnValue(
      new Observable<string>((subscriber) => {
        subscriber.next('new-access-token');
        subscriber.complete();
      }),
    );

    const result = vi.fn();
    httpClient.get('/api/datasets').subscribe(result);
    const first = httpMock.expectOne('/api/datasets');

    vi.useFakeTimers();
    first.flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
    await vi.runAllTimersAsync();
    vi.useRealTimers();

    const retried = httpMock.expectOne('/api/datasets');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retried.flush([{ id: 1 }]);

    expect(result).toHaveBeenCalledWith([{ id: 1 }]);
    expect(snackBarOpenSpy).not.toHaveBeenCalled();
  });
});
