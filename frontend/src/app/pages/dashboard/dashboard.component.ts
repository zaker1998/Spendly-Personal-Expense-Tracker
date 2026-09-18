import { CurrencyPipe, DatePipe } from '@angular/common';
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

interface DashboardData {
  summary: MonthlySummary;
  budgets: Budget[];
  recent: Expense[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, RouterLink, ReactiveFormsModule],
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
  readonly budgets = signal<Budget[]>([]);
  readonly recent = signal<Expense[]>([]);
  readonly loading = signal(true);

  readonly maxCategoryTotal = computed(
    () => this.summary()?.byCategory.reduce((max, row) => Math.max(max, Number(row.total)), 0) ?? 0
  );

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

          return forkJoin({
            summary: this.api.getMonthlySummary(year, month),
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
        const data: DashboardData = {
          summary: result.summary,
          budgets: result.budgets,
          recent: result.recent.content
        };
        this.summary.set(data.summary);
        this.budgets.set(data.budgets);
        this.recent.set(data.recent);
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

  monthLabel(): string {
    const year = Number(this.period.controls.year.value);
    const month = Number(this.period.controls.month.value);
    return new Date(year, month - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric'
    });
  }
}
