import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, Role } from './models';

interface Session {
  accessToken: string;
  userId: number;
  email: string;
  role: Role;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'spendly_session';
  private readonly sessionSignal = signal<Session | null>(this.readSession());

  readonly session = this.sessionSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.sessionSignal());
  readonly isAdmin = computed(() => this.sessionSignal()?.role === 'ADMIN');

  constructor(private http: HttpClient, private router: Router) {}

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

  logout(): void {
    localStorage.removeItem(this.storageKey);
    this.sessionSignal.set(null);
    this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return this.sessionSignal()?.accessToken ?? null;
  }

  private persist(res: AuthResponse): void {
    const session: Session = {
      accessToken: res.accessToken,
      userId: res.userId,
      email: res.email,
      role: res.role
    };
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.sessionSignal.set(session);
  }

  private readSession(): Session | null {
    const raw = localStorage.getItem(this.storageKey);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as Session;
    } catch {
      return null;
    }
  }
}
