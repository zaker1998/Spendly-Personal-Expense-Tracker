import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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

    this.api.getMonthlySummary(year, month).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.maxCategoryTotal = summary.byCategory.reduce(
          (max, row) => Math.max(max, Number(row.total)),
          0
        );
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load summary';
        this.loading = false;
      }
    });

    this.api.getBudgets(year, month).subscribe({
      next: (budgets) => (this.budgets = budgets),
      error: () => undefined
    });

    const from = `${year}-${String(month).padStart(2, '0')}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const to = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;

    this.api.getExpenses({ page: 0, size: 5, from, to }).subscribe({
      next: (page) => (this.recent = page.content),
      error: () => (this.error = 'Could not load recent expenses')
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
