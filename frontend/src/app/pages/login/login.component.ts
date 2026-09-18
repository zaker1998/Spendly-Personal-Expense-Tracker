import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { describeError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';

/** After this long a login is almost certainly waiting on a cold API, not on bcrypt. */
const SLOW_LOGIN_MS = 4000;

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly error = signal('');
  readonly loading = signal(false);
  /** True once a login is taking long enough that the user deserves an explanation. */
  readonly slow = signal(false);

  private slowTimer?: ReturnType<typeof setTimeout>;

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  ngOnInit(): void {
    // The demo API runs on a free tier that sleeps after 15 minutes, and waking
    // it takes up to a couple of minutes. Starting that here means the wake-up
    // overlaps with the time the visitor spends typing instead of being added
    // to it. Fire and forget: the response is irrelevant, the request itself is
    // what matters, and on a local `ng serve` this path simply 404s.
    this.http
      .get('/actuator/health', { responseType: 'text' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => undefined });

    this.destroyRef.onDestroy(() => this.clearSlowTimer());
  }

  fillDemo(): void {
    this.form.setValue({ email: 'demo@spendly.app', password: 'Demo123!' });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.slow.set(false);
    this.error.set('');
    this.slowTimer = setTimeout(() => this.slow.set(true), SLOW_LOGIN_MS);

    const { email, password } = this.form.getRawValue();
    this.auth
      .login(email, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.finish();
          void this.router.navigateByUrl('/');
        },
        error: (err: unknown) => {
          this.finish();
          this.error.set(describeError(err, 'Login failed'));
        }
      });
  }

  private finish(): void {
    this.loading.set(false);
    this.slow.set(false);
    this.clearSlowTimer();
  }

  private clearSlowTimer(): void {
    if (this.slowTimer) {
      clearTimeout(this.slowTimer);
      this.slowTimer = undefined;
    }
  }
}
