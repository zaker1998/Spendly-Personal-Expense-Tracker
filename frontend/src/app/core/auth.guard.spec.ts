import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { adminGuard, authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('route guards', () => {
  let router: Router;

  /**
   * `signedIn` is what ensureSession resolves to — that is, whether a valid
   * token exists after any renewal the guard asked for.
   */
  function setUp(signedIn: boolean, admin = false) {
    const auth: Partial<AuthService> = {
      ensureSession: () => of(signedIn),
      isAdmin: (() => admin) as AuthService['isAdmin']
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
    });
    router = TestBed.inject(Router);
  }

  function run(guard: typeof authGuard): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() => guard(null!, null!));
    return firstValueFrom(result as Observable<boolean | UrlTree>);
  }

  function pathOf(result: unknown): string {
    return router.serializeUrl(result as UrlTree);
  }

  it('authGuard lets a signed-in user through', async () => {
    setUp(true);
    expect(await run(authGuard)).toBeTrue();
  });

  it('authGuard sends an anonymous visitor to the login page', async () => {
    setUp(false);
    expect(pathOf(await run(authGuard))).toBe('/login');
  });

  it('guestGuard keeps a signed-in user off the login page', async () => {
    setUp(true);
    expect(pathOf(await run(guestGuard))).toBe('/');
  });

  it('guestGuard lets an anonymous visitor reach the login page', async () => {
    setUp(false);
    expect(await run(guestGuard)).toBeTrue();
  });

  it('adminGuard allows an admin', async () => {
    setUp(true, true);
    expect(await run(adminGuard)).toBeTrue();
  });

  it('adminGuard redirects a signed-in non-admin home, not to login', async () => {
    setUp(true, false);
    expect(pathOf(await run(adminGuard))).toBe('/');
  });

  it('adminGuard redirects an anonymous visitor to login', async () => {
    setUp(false);
    expect(pathOf(await run(adminGuard))).toBe('/login');
  });
});
