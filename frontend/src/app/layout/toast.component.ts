import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from '../core/notification.service';

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
  template: `
    <div class="toasts" role="log" aria-live="polite" aria-relevant="additions">
      @for (notice of notifications.notices(); track notice.id) {
        <div class="toast" [class.error]="notice.kind === 'error'">
          <span>{{ notice.text }}</span>
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

      .toast {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        border-radius: 8px;
        border-left: 4px solid var(--accent-bright);
        background: var(--field-bg);
        color: var(--ink);
        box-shadow: 0 6px 20px var(--shadow-color);
        border: 1px solid var(--line);
      }

      .toast.error {
        border-left-color: var(--danger);
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
