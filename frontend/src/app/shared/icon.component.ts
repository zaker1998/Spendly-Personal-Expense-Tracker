import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** A circle as path data, so every icon is a plain list of `d` strings. */
const circle = (cx: number, cy: number, r: number) =>
  `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`;

/**
 * Stroke icons on a 24×24 grid.
 *
 * Inline rather than an icon font or a library: a dozen glyphs are a few hundred
 * bytes, they inherit `currentColor` so they follow the theme, and nothing has
 * to be loaded before the navigation can paint.
 */
const ICONS = {
  dashboard: ['M4 4h6v8H4z', 'M14 4h6v5h-6z', 'M14 13h6v7h-6z', 'M4 16h6v4H4z'],
  receipt: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6', 'M9 16h3'],
  tag: ['M3.5 12.5V4.5a1 1 0 0 1 1-1h8l8 8-9 9z', circle(8, 8, 1.2)],
  target: [circle(12, 12, 9), circle(12, 12, 5), circle(12, 12, 1)],
  user: [circle(12, 8, 4), 'M4.5 20.5a7.5 7.5 0 0 1 15 0'],
  shield: ['M12 3l8 3v6c0 4.6-3.4 8-8 9-4.6-1-8-4.4-8-9V6z', 'M9 12l2 2 4-4'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  plus: ['M12 5v14', 'M5 12h14'],
  download: ['M12 3v12', 'M7 10l5 5 5-5', 'M5 21h14'],
  sparkles: [
    'M11 3l1.9 5.1L18 10l-5.1 1.9L11 17l-1.9-5.1L4 10l5.1-1.9z',
    'M19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9z'
  ],
  edit: ['M4 20h4L19 9l-4-4L4 16z', 'M13.5 6.5l4 4'],
  trash: [
    'M4 7h16',
    'M10 11v6',
    'M14 11v6',
    'M6 7l1 12.5a1.5 1.5 0 0 0 1.5 1.5h7a1.5 1.5 0 0 0 1.5-1.5L18 7',
    'M9 7V4h6v3'
  ],
  chevronLeft: ['M15 18l-6-6 6-6'],
  chevronRight: ['M9 18l6-6-6-6'],
  search: [circle(11, 11, 7), 'M20 20l-4-4'],
  trendingUp: ['M3 17l6-6 4 4 8-8', 'M15 7h6v6'],
  trendingDown: ['M3 7l6 6 4-4 8 8', 'M15 17h6v-6'],
  wallet: [
    'M19 7V5a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V6',
    'M16.5 14h.01'
  ],
  calendar: [
    'M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z',
    'M4 10h16',
    'M8 3v4',
    'M16 3v4'
  ],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  check: ['M5 12.5l4.5 4.5L19 7.5'],
  arrowRight: ['M5 12h14', 'M13 6l6 6-6 6'],
  lock: ['M6 11h12v10H6z', 'M8.5 11V7.5a3.5 3.5 0 0 1 7 0V11'],
  chart: ['M4 20V11', 'M10 20V5', 'M16 20v-6', 'M3 20h18'],
  bolt: ['M13 3L5 13.5h6L10 21l8-10.5h-6z'],
  filter: ['M4 5h16', 'M7 12h10', 'M10 19h4']
} satisfies Record<string, string[]>;

export type IconName = keyof typeof ICONS;

@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="size()"
      [attr.height]="size()"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: [':host { display: inline-flex; flex-shrink: 0; line-height: 0; }']
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(18);

  protected readonly paths = computed(() => ICONS[this.name()]);
}
