import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from '../core/notification.service';
import { IconComponent } from '../shared/icon.component';

/**
 * The one place messages appear.
 *
 * <p>Errors used to be an `error` string rendered somewhere inside whichever
 * page raised them, which meant a failure during a navigation had nowhere to go
 * and a failure below the fold was never seen. Live region so a screen reader
 * hears it without the focus moving.
 */
@Component({
  selector: 'app-toasts',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="toasts" role="log" aria-live="polite" aria-relevant="additions">
      @for (notice of notifications.notices(); track notice.id) {
        <div class="toast" [class.error]="notice.kind === 'error'">
          <app-icon class="kind" [name]="notice.kind === 'error' ? 'close' : 'check'" [size]="16" />
          <span class="text">{{ notice.text }}</span>
          <button
            type="button"
            class="dismiss"
            [attr.aria-label]="'Dismiss: ' + notice.text"
            (click)="notifications.dismiss(notice.id)"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toasts {
        position: fixed;
        inset-block-end: 1rem;
        inset-inline-end: 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        max-width: min(28rem, calc(100vw - 2rem));
        z-index: 50;
      }

      /* border-left after the shorthand: the other way round, the shorthand
         reset it and the success/error stripe never showed. */
      .toast {
        display: flex;
        align-items: flex-start;
        gap: 0.65rem;
        padding: 0.75rem 0.9rem;
        border-radius: var(--radius);
        border: 1px solid var(--line);
        border-left: 4px solid var(--accent-bright);
        background: var(--surface-solid);
        color: var(--ink);
        box-shadow: var(--shadow-md);
        animation: toast-in 0.2s ease-out;
      }

      .toast.error {
        border-left-color: var(--danger);
      }

      .kind {
        display: grid;
        place-items: center;
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 50%;
        background: var(--accent-soft);
        color: var(--heading);
      }

      .error .kind {
        background: var(--danger-soft);
        color: var(--danger);
      }

      .text {
        padding-top: 0.05rem;
      }

      @keyframes toast-in {
        from {
          opacity: 0;
          transform: translateY(8px);
        }
      }

      .dismiss {
        margin-inline-start: auto;
        border: 0;
        background: none;
        color: inherit;
        font-size: 1.25rem;
        line-height: 1;
        cursor: pointer;
        padding: 0 0.25rem;
      }
    `
  ]
})
export class ToastComponent {
  protected readonly notifications = inject(NotificationService);
}
