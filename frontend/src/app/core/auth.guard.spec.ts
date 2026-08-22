import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('route guards', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  function setUp(authenticated: boolean, admin = false) {
    auth = jasmine.createSpyObj<AuthService>('AuthService', [], {
      isAuthenticated: () => authenticated,
      isAdmin: () => admin
    } as Partial<AuthService>);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
    });
    router = TestBed.inject(Router);
  }

  function run(guard: typeof authGuard) {
    return TestBed.runInInjectionContext(() => guard(null!, null!));
  }

  function pathOf(result: unknown): string {
    return router.serializeUrl(result as UrlTree);
  }

  it('authGuard lets a signed-in user through', () => {
    setUp(true);
    expect(run(authGuard)).toBeTrue();
  });

  it('authGuard sends an anonymous visitor to the login page', () => {
    setUp(false);
    expect(pathOf(run(authGuard))).toBe('/login');
  });

  it('guestGuard keeps a signed-in user off the login page', () => {
    setUp(true);
    expect(pathOf(run(guestGuard))).toBe('/');
  });

  it('adminGuard allows an admin', () => {
    setUp(true, true);
    expect(run(adminGuard)).toBeTrue();
  });

  it('adminGuard redirects a signed-in non-admin home, not to login', () => {
    setUp(true, false);
    expect(pathOf(run(adminGuard))).toBe('/');
  });

  it('adminGuard redirects an anonymous visitor to login', () => {
    setUp(false);
    expect(pathOf(run(adminGuard))).toBe('/login');
  });
});
