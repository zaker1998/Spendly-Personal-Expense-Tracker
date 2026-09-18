import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Endpoints that issue or end a session; the refresh cookie is scoped to these. */
const AUTH_PATH = '/auth/';

/** Requests whose own 401 means "wrong credentials", not "token expired". */
const CREDENTIAL_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  return next(prepare(req, auth.token())).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      if (CREDENTIAL_PATHS.some((path) => req.url.includes(path))) {
        return throwError(() => err);
      }

      // The access token expired mid-session. The refresh cookie — not anything
      // this page can read — decides whether the session is still alive, so try
      // it before giving up. One retry only: if the renewed token is rejected
      // too, the session really is over.
      return auth.refresh().pipe(
        switchMap((renewed) => next(prepare(req, renewed.accessToken))),
        catchError(() => {
          auth.forceLogout();
          return throwError(() => err);
        })
      );
    })
  );
};

function prepare(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return req.clone({
    // Without this the browser neither stores nor sends the refresh cookie on a
    // cross-origin call — which is what `ng serve` on :4200 talking to the API
    // on :8080 is. Deployed, the API sits under the SPA's own origin and the
    // flag changes nothing.
    withCredentials: req.url.includes(AUTH_PATH),
    setHeaders: token ? { Authorization: `Bearer ${token}` } : {}
  });
}
