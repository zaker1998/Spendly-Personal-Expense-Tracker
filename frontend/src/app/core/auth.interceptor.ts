import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err: unknown) => {
      // Expired/invalid token: force logout so the user lands on the login page
      // instead of staying on a dead session. 401 from the auth endpoints
      // themselves means wrong credentials and is handled by the login form.
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !req.url.includes('/auth/') &&
        auth.isAuthenticated()
      ) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
