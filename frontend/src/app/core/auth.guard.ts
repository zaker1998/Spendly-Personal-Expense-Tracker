import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Lets the route through when there is a live session, renewing a lapsed access
 * token from the refresh cookie first.
 *
 * <p>The renewal is the point: the access token lasts fifteen minutes and the
 * session lasts a month, so a guard that only read local state would sign
 * people out on every reload after a short break.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(map((ok) => ok || router.createUrlTree(['/login'])));
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(map((signedIn) => !signedIn || router.createUrlTree(['/'])));
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((signedIn) => {
      if (!signedIn) {
        return router.createUrlTree(['/login']);
      }
      return auth.isAdmin() || router.createUrlTree(['/']);
    })
  );
};
