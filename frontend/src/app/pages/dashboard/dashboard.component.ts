import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, catchError, forkJoin, of, switchMap, tap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { Budget, Expense, MonthlySummary } from '../../core/models';
import { NotificationService } from '../../core/notification.service';
import { IconComponent } from '../../shared/icon.component';

/** Used when a category has no colour of its own. */
const FALLBACK_COLOURS = ['#2a9d8f', '#e76f51', '#e9c46a', '#264653', '#f4a261', '#8d99ae'];

export interface DonutSlice {
  name: string;
  colour: string;
  /** Share of the month, 0–100. */
  share: number;
  /** stroke-dasharray / -dashoffset on a circle whose circumference is 100. */
  dash: string;
  offset: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink, ReactiveFormsModule, IconComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  /** switchMap here replaces the hand-rolled sequence counter: changing the
   *  month twice quickly cancels the first load instead of racing it. */
  private readonly reloads = new Subject<void>();

  // prettier-ignore
  readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  readonly summary = signal<MonthlySummary | null>(null);
  readonly previous = signal<MonthlySummary | null>(null);
  readonly budgets = signal<Budget[]>([]);
  readonly recent = signal<Expense[]>([]);
  readonly expenseCount = signal(0);
  readonly loading = signal(true);

  readonly maxCategoryTotal = computed(
    () => this.summary()?.byCategory.reduce((max, row) => Math.max(max, Number(row.total)), 0) ?? 0
  );

  /** Percentage change against the month before, or null when there is nothing to compare. */
  readonly changeVsPrevious = computed(() => {
    const current = Number(this.summary()?.totalAmount ?? 0);
    const before = Number(this.previous()?.totalAmount ?? 0);
    if (!this.previous() || before <= 0) {
      return null;
    }
    return Math.round(((current - before) / before) * 100);
  });

  readonly dailyAverage = computed(() => {
    const summary = this.summary();
    if (!summary) {
      return 0;
    }
    return Number(summary.totalAmount) / this.daysCounted(summary.year, summary.month);
  });

  /** The budget without a category, i.e. the limit for the whole month. */
  readonly overallBudget = computed(() => this.budgets().find((b) => b.categoryId == null) ?? null);

  readonly slices = computed<DonutSlice[]>(() => {
    const summary = this.summary();
    const total = Number(summary?.totalAmount ?? 0);
    if (!summary || total <= 0) {
      return [];
    }
    let cursor = 0;
    return summary.byCategory.map((row, index) => {
      const share = (Number(row.total) / total) * 100;
      const slice: DonutSlice = {
        name: row.categoryName,
        colour: this.colourFor(row.categoryColor, index),
        share,
        // A hairline gap between slices, unless one slice is the whole ring.
        dash: `${Math.max(share - (share < 100 ? 0.6 : 0), 0)} ${100 - share + (share < 100 ? 0.6 : 0)}`,
        // Start at 12 o'clock: the circle's own start is at 3 o'clock.
        offset: 25 - cursor
      };
      cursor += share;
      return slice;
    });
  });

  private readonly now = new Date();

  readonly period = this.fb.nonNullable.group({
    year: [this.now.getFullYear()],
    month: [this.now.getMonth() + 1]
  });

  constructor() {
    this.reloads
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap(() => {
          const year = Number(this.period.controls.year.value);
          const month = Number(this.period.controls.month.value);
          const lastDay = new Date(year, month, 0).getDate();
          const pad = (value: number) => String(value).padStart(2, '0');
          const from = `${year}-${pad(month)}-01`;
          const to = `${year}-${pad(month)}-${pad(lastDay)}`;
          const before = month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 };

          return forkJoin({
            summary: this.api.getMonthlySummary(year, month),
            // Only feeds the "vs last month" figure, so a failure here must not
            // take the whole dashboard down with it.
            previous: this.api
              .getMonthlySummary(before.year, before.month)
              .pipe(catchError(() => of(null))),
            budgets: this.api.getBudgets(year, month),
            recent: this.api.getExpenses({ page: 0, size: 5, from, to })
          }).pipe(
            catchError((err: unknown) => {
              this.notifications.error(describeError(err, 'Could not load the dashboard'));
              return of(null);
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (!result) {
          return;
        }
        this.summary.set(result.summary);
        this.previous.set(result.previous);
        this.budgets.set(result.budgets);
        this.recent.set(result.recent.content);
        this.expenseCount.set(result.recent.totalElements);
      });
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.reloads.next();
  }

  shiftMonth(delta: number): void {
    let year = Number(this.period.controls.year.value);
    let month = Number(this.period.controls.month.value) + delta;
    if (month < 1) {
      month = 12;
      year -= 1;
    } else if (month > 12) {
      month = 1;
      year += 1;
    }
    this.period.patchValue({ year, month });
    this.reload();
  }

  barWidth(total: number): number {
    const max = this.maxCategoryTotal();
    return max <= 0 ? 0 : Math.round((Number(total) / max) * 100);
  }

  share(total: number): number {
    const all = Number(this.summary()?.totalAmount ?? 0);
    return all <= 0 ? 0 : Math.round((Number(total) / all) * 100);
  }

  colourFor(colour: string | null | undefined, index: number): string {
    return colour || FALLBACK_COLOURS[index % FALLBACK_COLOURS.length];
  }

  /** Progress-bar state for a budget: calm, getting close, or over. */
  budgetTone(budget: Budget): 'over' | 'caution' | '' {
    if (budget.overBudget) {
      return 'over';
    }
    return budget.percentUsed >= 85 ? 'caution' : '';
  }

  monthLabel(): string {
    const year = Number(this.period.controls.year.value);
    const month = Number(this.period.controls.month.value);
    return new Date(year, month - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric'
    });
  }

  previousMonthName(): string {
    const month = Number(this.period.controls.month.value);
    return this.months[(month + 10) % 12];
  }

  /**
   * Days the average is taken over: the days so far for the current month,
   * every day for a month that is over.
   */
  private daysCounted(year: number, month: number): number {
    const today = new Date();
    const daysInMonth = new Date(year, month, 0).getDate();
    if (year === today.getFullYear() && month === today.getMonth() + 1) {
      return today.getDate();
    }
    return daysInMonth;
  }
}
