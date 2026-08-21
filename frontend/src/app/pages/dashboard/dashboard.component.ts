import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Budget, Expense, MonthlySummary } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgFor, NgIf, CurrencyPipe, DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  /** Guards against out-of-order responses when the month changes quickly. */
  private loadSeq = 0;

  readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  summary: MonthlySummary | null = null;
  budgets: Budget[] = [];
  recent: Expense[] = [];
  loading = true;
  error = '';
  maxCategoryTotal = 0;

  private readonly now = new Date();

  period = this.fb.nonNullable.group({
    year: [this.now.getFullYear()],
    month: [this.now.getMonth() + 1]
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const year = Number(this.period.controls.year.value);
    const month = Number(this.period.controls.month.value);
    this.loading = true;
    this.error = '';
    const seq = ++this.loadSeq;

    const from = `${year}-${String(month).padStart(2, '0')}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const to = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;

    forkJoin({
      summary: this.api.getMonthlySummary(year, month),
      budgets: this.api.getBudgets(year, month),
      recent: this.api.getExpenses({ page: 0, size: 5, from, to })
    }).subscribe({
      next: ({ summary, budgets, recent }) => {
        if (seq !== this.loadSeq) {
          return;
        }
        this.summary = summary;
        this.maxCategoryTotal = summary.byCategory.reduce(
          (max, row) => Math.max(max, Number(row.total)),
          0
        );
        this.budgets = budgets;
        this.recent = recent.content;
        this.loading = false;
      },
      error: () => {
        if (seq !== this.loadSeq) {
          return;
        }
        this.error = 'Could not load dashboard data';
        this.loading = false;
      }
    });
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
    if (this.maxCategoryTotal <= 0) {
      return 0;
    }
    return Math.round((Number(total) / this.maxCategoryTotal) * 100);
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
