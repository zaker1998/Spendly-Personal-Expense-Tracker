import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from './models';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  const loginResponse: AuthResponse = {
    accessToken: 'token-123',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    userId: 7,
    email: 'demo@spendly.app',
    role: 'USER'
  };

  beforeEach(() => {
    localStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    router.navigateByUrl.and.resolveTo(true);
    configure();
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when nothing is stored', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
  });

  it('stores the session on login', () => {
    service.login('demo@spendly.app', 'Demo123!').subscribe();

    const req = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(loginResponse);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.token()).toBe('token-123');
    expect(service.isAdmin()).toBeFalse();
  });

  it('exposes admin role separately from plain authentication', () => {
    service.login('admin@spendly.app', 'Admin123!').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush({ ...loginResponse, role: 'ADMIN' });

    expect(service.isAdmin()).toBeTrue();
  });

  it('restores an unexpired session written by a previous page load', () => {
    storeSession({ expiresAt: Date.now() + 60_000, role: 'ADMIN' });

    // The session is read in a field initialiser, so only a service built after
    // the value is in storage exercises the restore path.
    const restored = freshService();

    expect(restored.isAuthenticated()).toBeTrue();
    expect(restored.token()).toBe('persisted');
    expect(restored.isAdmin()).toBeTrue();
  });

  /**
   * A stale entry used to count as a live session: the guard let the user in and
   * the first API call threw them back out.
   */
  it('treats an expired stored token as signed out', () => {
    storeSession({ expiresAt: Date.now() - 1 });

    expect(freshService().isAuthenticated()).toBeFalse();
  });

  it('ignores a stored session from before tokens carried an expiry', () => {
    localStorage.setItem(
      'spendly_session',
      JSON.stringify({ accessToken: 'old-shape', userId: 1, email: 'a@b.c', role: 'USER' })
    );

    expect(freshService().isAuthenticated()).toBeFalse();
  });

  it('ends the session on the server and locally on logout', () => {
    signIn();

    service.logout();
    http.expectOne(`${environment.apiUrl}/auth/logout`).flush(null, {
      status: 204,
      statusText: 'No Content'
    });

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('spendly_session')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  /** Staying signed in because the server was unreachable is the wrong failure. */
  it('still signs out locally when the logout call fails', () => {
    signIn();

    service.logout();
    http
      .expectOne(`${environment.apiUrl}/auth/logout`)
      .flush(null, { status: 0, statusText: 'Unknown Error' });

    expect(service.isAuthenticated()).toBeFalse();
  });

  it('refresh stores the renewed token', () => {
    service.refresh().subscribe();
    http
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush({ ...loginResponse, accessToken: 'renewed' });

    expect(service.token()).toBe('renewed');
  });

  /**
   * The server rotates the refresh token on each use, so parallel refreshes
   * would present a token that the first of them had just revoked.
   */
  it('shares one refresh between concurrent callers', () => {
    const results: string[] = [];
    service.refresh().subscribe((r) => results.push(r.accessToken));
    service.refresh().subscribe((r) => results.push(r.accessToken));
    service.refresh().subscribe((r) => results.push(r.accessToken));

    http
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush({ ...loginResponse, accessToken: 'once' });

    expect(results).toEqual(['once', 'once', 'once']);
  });

  it('starts a new refresh once the previous one has finished', () => {
    service.refresh().subscribe();
    http.expectOne(`${environment.apiUrl}/auth/refresh`).flush(loginResponse);

    service.refresh().subscribe();
    http
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush({ ...loginResponse, accessToken: 'second' });

    expect(service.token()).toBe('second');
  });

  it('ensureSession renews a lapsed token from the cookie', () => {
    let result: boolean | undefined;
    service.ensureSession().subscribe((ok) => (result = ok));

    http.expectOne(`${environment.apiUrl}/auth/refresh`).flush(loginResponse);

    expect(result).toBeTrue();
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('ensureSession answers false, quietly, when there is no session to renew', () => {
    let result: boolean | undefined;
    service.ensureSession().subscribe((ok) => (result = ok));

    http
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush(
        { message: 'Invalid or expired session' },
        { status: 401, statusText: 'Unauthorized' }
      );

    expect(result).toBeFalse();
  });

  it('ensureSession does not call the server while the token is valid', () => {
    signIn();

    let result: boolean | undefined;
    service.ensureSession().subscribe((ok) => (result = ok));

    expect(result).toBeTrue();
    http.expectNone(`${environment.apiUrl}/auth/refresh`);
  });

  it('ignores corrupted session data instead of throwing', () => {
    localStorage.setItem('spendly_session', 'not-json');

    expect(freshService().isAuthenticated()).toBeFalse();
  });

  function signIn(): void {
    service.login('demo@spendly.app', 'Demo123!').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse);
  }

  function storeSession(overrides: Partial<{ expiresAt: number; role: string }>): void {
    localStorage.setItem(
      'spendly_session',
      JSON.stringify({
        accessToken: 'persisted',
        expiresAt: Date.now() + 60_000,
        userId: 1,
        email: 'a@b.c',
        role: 'USER',
        ...overrides
      })
    );
  }

  function configure(): void {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });
  }

  /** Rebuilds the injector so AuthService is constructed against current storage. */
  function freshService(): AuthService {
    http.verify();
    TestBed.resetTestingModule();
    configure();
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }
});
