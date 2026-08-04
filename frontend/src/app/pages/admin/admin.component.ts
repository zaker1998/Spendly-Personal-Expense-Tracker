import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AdminExpense, AppUser } from '../../core/models';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [NgFor, NgIf, CurrencyPipe, DatePipe, ReactiveFormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  tab: 'users' | 'expenses' = 'users';
  users: AppUser[] = [];
  expenses: AdminExpense[] = [];
  loading = true;
  error = '';

  filters = this.fb.nonNullable.group({
    from: [''],
    to: ['']
  });

  ngOnInit(): void {
    this.loadUsers();
  }

  showUsers(): void {
    this.tab = 'users';
    this.loadUsers();
  }

  showExpenses(): void {
    this.tab = 'expenses';
    this.loadExpenses();
  }

  loadUsers(): void {
    this.loading = true;
    this.error = '';
    this.api.getAdminUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load users';
      }
    });
  }

  loadExpenses(): void {
    this.loading = true;
    this.error = '';
    const f = this.filters.getRawValue();
    this.api.getAdminExpenses({ from: f.from || null, to: f.to || null }).subscribe({
      next: (expenses) => {
        this.expenses = expenses;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load expenses';
      }
    });
  }
}
