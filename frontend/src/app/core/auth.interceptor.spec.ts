import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import { Subject, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { AuthResponse } from './models';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  const renewed: AuthResponse = {
    accessToken: 'renewed',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    userId: 1,
    email: 'a@b.c',
    role: 'USER'
  };

  function setUp(token: string | null) {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['token', 'refresh', 'forceLogout']);
    auth.token.and.returnValue(token);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth }
      ]
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => controller.verify());

  it('attaches the bearer token when there is a session', () => {
    setUp('token-123');

    http.get('/api/expenses').subscribe();

    const req = controller.expectOne('/api/expenses');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-123');
    req.flush({});
  });

  it('sends no Authorization header when logged out', () => {
    setUp(null);

    http.get('/api/expenses').subscribe();

    const req = controller.expectOne('/api/expenses');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  /** The refresh cookie is only attached to cross-origin calls that ask for it. */
  it('sends credentials to the auth endpoints and nowhere else', () => {
    setUp(null);

    http.post('/api/auth/refresh', {}).subscribe();
    http.get('/api/expenses').subscribe();

    const refresh = controller.expectOne('/api/auth/refresh');
    const expenses = controller.expectOne('/api/expenses');
    expect(refresh.request.withCredentials).toBeTrue();
    expect(expenses.request.withCredentials).toBeFalse();
    refresh.flush(renewed);
    expenses.flush({});
  });

  it('renews an expired token and replays the request with the new one', () => {
    setUp('expired');
    const refreshed = new Subject<AuthResponse>();
    auth.refresh.and.returnValue(refreshed);

    let body: unknown;
    http.get('/api/expenses').subscribe((b) => (body = b));

    controller
      .expectOne('/api/expenses')
      .flush('expired', { status: 401, statusText: 'Unauthorized' });
    refreshed.next(renewed);
    refreshed.complete();

    const retry = controller.expectOne('/api/expenses');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer renewed');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
    expect(auth.forceLogout).not.toHaveBeenCalled();
  });

  it('signs out when the session cannot be renewed', () => {
    setUp('expired');
    auth.refresh.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
    );

    let status = 0;
    http
      .get('/api/expenses')
      .subscribe({ error: (err: HttpErrorResponse) => (status = err.status) });

    controller
      .expectOne('/api/expenses')
      .flush('expired', { status: 401, statusText: 'Unauthorized' });

    expect(auth.forceLogout).toHaveBeenCalled();
    expect(status).toBe(401);
  });

  it('leaves a failed login alone so the form can show the error', () => {
    setUp(null);

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    controller
      .expectOne('/api/auth/login')
      .flush('bad credentials', { status: 401, statusText: 'Unauthorized' });

    expect(auth.refresh).not.toHaveBeenCalled();
    expect(auth.forceLogout).not.toHaveBeenCalled();
  });

  /** A failed refresh must not try to refresh itself. */
  it('does not recurse when the refresh call itself is rejected', () => {
    setUp(null);

    http.post('/api/auth/refresh', {}).subscribe({ error: () => undefined });

    controller
      .expectOne('/api/auth/refresh')
      .flush('no session', { status: 401, statusText: 'Unauthorized' });

    expect(auth.refresh).not.toHaveBeenCalled();
  });

  it('passes other errors through without touching the session', () => {
    setUp('token-123');

    let status = 0;
    http
      .get('/api/expenses')
      .subscribe({ error: (err: HttpErrorResponse) => (status = err.status) });

    controller
      .expectOne('/api/expenses')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
    expect(auth.refresh).not.toHaveBeenCalled();
    expect(auth.forceLogout).not.toHaveBeenCalled();
  });
});
