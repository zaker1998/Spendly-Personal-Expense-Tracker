import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AdminExpense, AppUser, PageResponse } from '../../core/models';

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

  private static readonly PAGE_SIZE = 20;

  tab: 'users' | 'expenses' = 'users';
  users: AppUser[] = [];
  expenses: AdminExpense[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
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

  loadUsers(page = 0): void {
    this.loading = true;
    this.error = '';
    this.api.getAdminUsers(page, AdminComponent.PAGE_SIZE).subscribe({
      next: (res) => {
        this.users = res.content;
        this.applyPageMeta(res);
      },
      error: (err) => this.fail(err, 'Failed to load users')
    });
  }

  loadExpenses(page = 0): void {
    this.loading = true;
    this.error = '';
    const f = this.filters.getRawValue();
    this.api
      .getAdminExpenses({
        from: f.from || null,
        to: f.to || null,
        page,
        size: AdminComponent.PAGE_SIZE
      })
      .subscribe({
        next: (res) => {
          this.expenses = res.content;
          this.applyPageMeta(res);
        },
        error: (err) => this.fail(err, 'Failed to load expenses')
      });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) {
      return;
    }
    if (this.tab === 'users') {
      this.loadUsers(page);
    } else {
      this.loadExpenses(page);
    }
  }

  private applyPageMeta(res: PageResponse<unknown>): void {
    this.page = res.number;
    this.totalPages = res.totalPages;
    this.totalElements = res.totalElements;
    this.loading = false;
  }

  private fail(err: unknown, fallback: string): void {
    this.loading = false;
    this.error = (err as { error?: { message?: string } })?.error?.message ?? fallback;
  }
}
