import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { Category, Expense } from '../../core/models';

@Component({
  selector: 'app-expenses',
  standalone: true,
  imports: [ReactiveFormsModule, NgFor, NgIf, CurrencyPipe, DatePipe],
  templateUrl: './expenses.component.html',
  styleUrl: './expenses.component.css'
})
export class ExpensesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  categories: Category[] = [];
  expenses: Expense[] = [];
  totalElements = 0;
  page = 0;
  error = '';
  editingId: number | null = null;

  filters = this.fb.nonNullable.group({
    categoryId: [''],
    from: [''],
    to: [''],
    search: ['']
  });

  form = this.fb.nonNullable.group({
    categoryId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    spentOn: [new Date().toISOString().slice(0, 10), Validators.required],
    description: [''],
    currency: ['EUR']
  });

  ngOnInit(): void {
    this.api.getCategories().subscribe({
      next: (cats) => {
        this.categories = cats;
        if (cats.length && !this.form.value.categoryId) {
          this.form.patchValue({ categoryId: String(cats[0].id) });
        }
      }
    });
    this.load();
  }

  load(page = 0): void {
    this.page = page;
    const f = this.filters.getRawValue();
    this.api
      .getExpenses({
        page,
        size: 10,
        categoryId: f.categoryId ? Number(f.categoryId) : null,
        from: f.from || null,
        to: f.to || null,
        search: f.search || null
      })
      .subscribe({
        next: (res) => {
          this.expenses = res.content;
          this.totalElements = res.totalElements;
        },
        error: () => (this.error = 'Failed to load expenses')
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = {
      categoryId: Number(value.categoryId),
      amount: Number(value.amount),
      currency: value.currency || 'EUR',
      spentOn: value.spentOn,
      description: value.description || undefined
    };

    const req$ =
      this.editingId == null
        ? this.api.createExpense(body)
        : this.api.updateExpense(this.editingId, body);

    req$.subscribe({
      next: () => {
        this.editingId = null;
        this.form.patchValue({ amount: 0, description: '', spentOn: new Date().toISOString().slice(0, 10) });
        this.load(this.page);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Save failed')
    });
  }

  edit(expense: Expense): void {
    this.editingId = expense.id;
    this.form.patchValue({
      categoryId: String(expense.categoryId),
      amount: expense.amount,
      spentOn: expense.spentOn,
      description: expense.description ?? '',
      currency: expense.currency
    });
  }

  remove(id: number): void {
    this.api.deleteExpense(id).subscribe({
      next: () => this.load(this.page),
      error: (err) => (this.error = err?.error?.message ?? 'Delete failed')
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form.patchValue({ amount: 0, description: '' });
  }
}
