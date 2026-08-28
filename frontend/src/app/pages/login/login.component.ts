import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { NgIf } from '@angular/common';
import { AuthService } from '../../core/auth.service';

/** After this long a login is almost certainly waiting on a cold API, not on bcrypt. */
const SLOW_LOGIN_MS = 4000;

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  error = '';
  loading = false;
  /** True once a login is taking long enough that the user deserves an explanation. */
  slow = false;

  private slowTimer?: ReturnType<typeof setTimeout>;

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  ngOnInit(): void {
    // The demo API runs on a free tier that sleeps after 15 minutes, and waking
    // it takes up to a couple of minutes. Starting that here means the wake-up
    // overlaps with the time the visitor spends typing instead of being added
    // to it. Fire and forget: the response is irrelevant, the request itself is
    // what matters, and on a local `ng serve` this path simply 404s.
    this.http.get('/actuator/health', { responseType: 'text' }).subscribe({
      next: () => {},
      error: () => {}
    });
  }

  ngOnDestroy(): void {
    this.clearSlowTimer();
  }

  fillDemo(): void {
    this.form.setValue({ email: 'demo@spendly.app', password: 'Demo123!' });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.slow = false;
    this.error = '';
    this.slowTimer = setTimeout(() => (this.slow = true), SLOW_LOGIN_MS);

    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.finish();
        this.router.navigateByUrl('/');
      },
      error: (err) => {
        this.finish();
        // A cold instance times out at the CDN before it answers, which arrives
        // as a status of 0 or 504 rather than anything the API said.
        const status = err?.status;
        this.error =
          status === 0 || status === 504 || status === 502
            ? 'The demo server is still starting up. Please try again in a moment.'
            : err?.error?.message ?? 'Login failed';
      }
    });
  }

  private finish(): void {
    this.loading = false;
    this.slow = false;
    this.clearSlowTimer();
  }

  private clearSlowTimer(): void {
    if (this.slowTimer) {
      clearTimeout(this.slowTimer);
      this.slowTimer = undefined;
    }
  }
}
