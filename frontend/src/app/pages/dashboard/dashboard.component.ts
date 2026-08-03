import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Budget, Expense, MonthlySummary } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgFor, NgIf, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  summary: MonthlySummary | null = null;
  budgets: Budget[] = [];
  recent: Expense[] = [];
  loading = true;
  error = '';
  maxCategoryTotal = 0;

  ngOnInit(): void {
    this.api.getMonthlySummary().subscribe({
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

    this.api.getBudgets().subscribe({
      next: (budgets) => (this.budgets = budgets),
      error: () => undefined
    });

    this.api.getExpenses({ page: 0, size: 5 }).subscribe({
      next: (page) => (this.recent = page.content),
      error: () => (this.error = 'Could not load recent expenses')
    });
  }

  barWidth(total: number): number {
    if (this.maxCategoryTotal <= 0) {
      return 0;
    }
    return Math.round((Number(total) / this.maxCategoryTotal) * 100);
  }
}
