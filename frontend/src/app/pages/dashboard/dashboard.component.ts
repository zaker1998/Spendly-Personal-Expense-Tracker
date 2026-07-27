import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Expense, MonthlySummary } from '../../core/models';

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
  recent: Expense[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.api.getMonthlySummary().subscribe({
      next: (summary) => {
        this.summary = summary;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load summary';
        this.loading = false;
      }
    });

    this.api.getExpenses({ page: 0, size: 5 }).subscribe({
      next: (page) => (this.recent = page.content),
      error: () => (this.error = 'Could not load recent expenses')
    });
  }
}
