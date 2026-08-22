import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  function setUp(token: string | null, authenticated = token !== null) {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['token', 'logout', 'isAuthenticated']);
    auth.token.and.returnValue(token);
    auth.isAuthenticated.and.returnValue(authenticated);

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

  it('logs out when the API rejects an expired token', () => {
    setUp('expired');

    http.get('/api/expenses').subscribe({ error: () => undefined });

    controller.expectOne('/api/expenses').flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalled();
  });

  it('leaves a failed login alone so the form can show the error', () => {
    setUp(null, false);

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    controller
      .expectOne('/api/auth/login')
      .flush('bad credentials', { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('passes other errors through without logging out', () => {
    setUp('token-123');

    let status = 0;
    http.get('/api/expenses').subscribe({ error: (err) => (status = err.status) });

    controller.expectOne('/api/expenses').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
