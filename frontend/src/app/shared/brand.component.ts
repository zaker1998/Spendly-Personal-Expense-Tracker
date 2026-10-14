import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Logo mark plus wordmark. The mark is the same drawing as public/favicon.svg. */
@Component({
  selector: 'app-brand',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      class="mark"
      viewBox="0 0 32 32"
      [attr.width]="size()"
      [attr.height]="size()"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id="spendly-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#2a9d8f" />
          <stop offset="1" stop-color="#1f5f6b" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill="url(#spendly-mark)" />
      <path
        d="M8 21.5l5.2-5.2 3.6 3.6L24 12.7"
        fill="none"
        stroke="#fff"
        stroke-width="2.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle cx="24" cy="12.7" r="2.2" fill="#f4a261" />
    </svg>
    <span class="word">Spendly</span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        gap: 0.6rem;
      }
      .mark {
        flex-shrink: 0;
        filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.15));
      }
      .word {
        font-family: var(--font-display);
        font-weight: 700;
        font-size: 1.5rem;
        letter-spacing: -0.01em;
        line-height: 1;
      }
    `
  ]
})
export class BrandComponent {
  readonly size = input(32);
}
