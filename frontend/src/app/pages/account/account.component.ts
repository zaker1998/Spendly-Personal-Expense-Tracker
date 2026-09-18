import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { describeError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { NotificationService } from '../../core/notification.service';

/**
 * Changing the password is the only way a user can end a session they think
 * somebody else has: the server revokes every other refresh token when it
 * succeeds.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly session = this.auth.session;
  readonly saving = signal(false);
  readonly error = signal('');

  readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required]
    },
    { validators: passwordsMatch }
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(this.form.hasError('mismatch') ? 'The two new passwords do not match.' : '');
      return;
    }
    this.saving.set(true);
    this.error.set('');

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.auth
      .changePassword(currentPassword, newPassword)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.reset();
          this.notifications.success('Password changed. Other devices have been signed out.');
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(describeError(err, 'Could not change the password'));
        }
      });
  }
}

function passwordsMatch(group: AbstractControl): { mismatch: true } | null {
  const next = group.get('newPassword')?.value as string;
  const confirm = group.get('confirmPassword')?.value as string;
  return next && confirm && next !== confirm ? { mismatch: true } : null;
}
