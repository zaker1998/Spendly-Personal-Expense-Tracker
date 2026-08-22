import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  const loginResponse = {
    accessToken: 'token-123',
    tokenType: 'Bearer',
    userId: 7,
    email: 'demo@spendly.app',
    role: 'USER' as const
  };

  beforeEach(() => {
    localStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });

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

  it('restores a session written by a previous page load', () => {
    localStorage.setItem(
      'spendly_session',
      JSON.stringify({ accessToken: 'persisted', userId: 1, email: 'a@b.c', role: 'ADMIN' })
    );

    // The session is read in a field initialiser, so only a service built after
    // the value is in storage exercises the restore path.
    const restored = freshService();

    expect(restored.isAuthenticated()).toBeTrue();
    expect(restored.token()).toBe('persisted');
    expect(restored.isAdmin()).toBeTrue();
  });

  it('clears the session and redirects on logout', () => {
    service.login('demo@spendly.app', 'Demo123!').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse);

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('spendly_session')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('ignores corrupted session data instead of throwing', () => {
    localStorage.setItem('spendly_session', 'not-json');

    expect(freshService().isAuthenticated()).toBeFalse();
  });

  /** Rebuilds the injector so AuthService is constructed against current storage. */
  function freshService(): AuthService {
    http.verify();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }
});
