import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, Role } from './models';

interface Session {
  accessToken: string;
  /** Epoch milliseconds. */
  expiresAt: number;
  userId: number;
  email: string;
  role: Role;
}

/**
 * Holds the access token and renews it.
 *
 * <p>The access token is deliberately short-lived and is the only thing kept in
 * localStorage. The refresh token lives in an httpOnly cookie the server sets,
 * which script — including anything injected into the page — cannot read. The
 * interceptor is what turns on `withCredentials` for the auth endpoints so the
 * browser attaches that cookie; everything the page itself can see comes back in
 * the response body.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'spendly_session';

  /**
   * Renew this long before the token actually expires, so a request that is
   * already in flight when the clock runs out still carries a valid token.
   */
  private static readonly RENEW_MARGIN_MS = 30_000;

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sessionSignal = signal<Session | null>(this.readSession());
  private refreshInFlight: Observable<AuthResponse> | null = null;

  readonly session = this.sessionSignal.asReadonly();
  readonly isAuthenticated = computed(() => {
    const session = this.sessionSignal();
    return session != null && session.expiresAt > Date.now();
  });
  readonly isAdmin = computed(() => this.sessionSignal()?.role === 'ADMIN');

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, { email, password })
      .pipe(tap((res) => this.persist(res)));
  }

  register(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/register`, { email, password })
      .pipe(tap((res) => this.persist(res)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/change-password`, {
        currentPassword,
        newPassword
      })
      .pipe(tap((res) => this.persist(res)));
  }

  /**
   * Swaps the refresh cookie for a new access token.
   *
   * <p>Shared while in flight: a burst of calls that all hit 401 at once — the
   * dashboard fires three in parallel — must produce one refresh, not three.
   * Three would be worse than wasteful: the server rotates the token on every
   * use, so the second and third would present one that had just been revoked.
   */
  refresh(): Observable<AuthResponse> {
    this.refreshInFlight ??= this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/refresh`, {})
      .pipe(
        tap((res) => this.persist(res)),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    return this.refreshInFlight;
  }

  /**
   * True when there is a valid token, renewing from the cookie first if needed.
   *
   * <p>This is what the route guard calls. With a fifteen-minute access token,
   * a guard that only looked at localStorage would sign the user out on the
   * first reload after lunch, even though their session is good for a month.
   */
  ensureSession(): Observable<boolean> {
    if (this.isAuthenticated()) {
      return of(true);
    }
    return this.refresh().pipe(
      map(() => true),
      // A missing or revoked cookie is the ordinary "not signed in" case, not an
      // error worth showing anyone.
      catchError(() => {
        this.clear();
        return of(false);
      })
    );
  }

  /** Ends the session on the server as well as in this tab. */
  logout(): void {
    this.http.post(`${environment.apiUrl}/auth/logout`, {}).subscribe({
      next: () => this.finishLogout(),
      // The local session goes either way: staying signed in because the server
      // could not be reached is the wrong failure mode for a log-out button.
      error: () => this.finishLogout()
    });
  }

  /** Session is already dead server-side (a refresh failed); just clean up. */
  forceLogout(): void {
    this.finishLogout();
  }

  token(): string | null {
    return this.sessionSignal()?.accessToken ?? null;
  }

  /** True when the token is close enough to expiry to be worth renewing. */
  needsRenewal(): boolean {
    const session = this.sessionSignal();
    return session != null && session.expiresAt - Date.now() < AuthService.RENEW_MARGIN_MS;
  }

  private finishLogout(): void {
    this.clear();
    void this.router.navigateByUrl('/login');
  }

  private clear(): void {
    localStorage.removeItem(this.storageKey);
    this.sessionSignal.set(null);
  }

  private persist(res: AuthResponse): void {
    const session: Session = {
      accessToken: res.accessToken,
      expiresAt: Date.now() + res.expiresInSeconds * 1000,
      userId: res.userId,
      email: res.email,
      role: res.role
    };
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.sessionSignal.set(session);
  }

  /**
   * An expired token is the same as no token. Before this, a stale entry in
   * localStorage counted as a live session: the guard let the user in, the shell
   * rendered, and the first API call bounced them back to the login page.
   */
  private readSession(): Session | null {
    const raw = localStorage.getItem(this.storageKey);
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as Session;
      return typeof parsed?.accessToken === 'string' && typeof parsed?.expiresAt === 'number'
        ? parsed
        : null;
    } catch {
      return null;
    }
  }
}
