import { CurrencyPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { localToday } from '../../core/date-utils';
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
  /** Guards against out-of-order responses when filters/pages change quickly. */
  private loadSeq = 0;

  categories: Category[] = [];
  expenses: Expense[] = [];
  totalElements = 0;
  page = 0;
  loading = false;
  saving = false;
  error = '';
  editingId: number | null = null;
  suggesting = false;
  suggestionNote = '';

  filters = this.fb.nonNullable.group({
    categoryId: [''],
    from: [''],
    to: [''],
    search: ['']
  });

  form = this.fb.nonNullable.group({
    categoryId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    spentOn: [localToday(), Validators.required],
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
      },
      error: () => (this.error = 'Failed to load categories')
    });
    this.load();
  }

  load(page = 0): void {
    this.page = page;
    this.loading = true;
    const seq = ++this.loadSeq;
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
          if (seq !== this.loadSeq) {
            return;
          }
          this.expenses = res.content;
          this.totalElements = res.totalElements;
          this.loading = false;
          this.error = '';
        },
        error: () => {
          if (seq !== this.loadSeq) {
            return;
          }
          this.loading = false;
          this.error = 'Failed to load expenses';
        }
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

    this.saving = true;
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.error = '';
        this.editingId = null;
        this.resetForm();
        this.load(this.page);
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Save failed';
      }
    });
  }

  suggestCategory(): void {
    const description = this.form.value.description?.trim();
    if (!description) {
      this.suggestionNote = 'Type a description first';
      return;
    }
    this.suggesting = true;
    this.suggestionNote = '';
    this.api.suggestCategory(description).subscribe({
      next: (s) => {
        this.suggesting = false;
        if (s.categoryId != null) {
          this.form.patchValue({ categoryId: String(s.categoryId) });
          this.suggestionNote =
            s.source === 'AI' ? `AI suggests: ${s.categoryName}` : `Suggested: ${s.categoryName}`;
        } else {
          this.suggestionNote = 'No suggestion found';
        }
      },
      error: () => {
        this.suggesting = false;
        this.suggestionNote = 'Suggestion failed';
      }
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

  remove(expense: Expense): void {
    if (!confirm(`Delete this expense (${expense.description || expense.categoryName})?`)) {
      return;
    }
    this.api.deleteExpense(expense.id).subscribe({
      next: () => {
        this.error = '';
        // If this was the last row on the current page, step back one page
        // instead of reloading an empty page.
        const targetPage = this.expenses.length === 1 && this.page > 0 ? this.page - 1 : this.page;
        this.load(targetPage);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Delete failed')
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.resetForm();
  }

  exportCsv(): void {
    const f = this.filters.getRawValue();
    this.api
      .exportExpensesCsv({
        categoryId: f.categoryId ? Number(f.categoryId) : null,
        from: f.from || null,
        to: f.to || null,
        search: f.search || null
      })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'expenses.csv';
          a.click();
          URL.revokeObjectURL(url);
        },
        error: () => (this.error = 'Export failed')
      });
  }

  private resetForm(): void {
    this.form.patchValue({
      amount: 0,
      description: '',
      spentOn: localToday(),
      currency: 'EUR',
      categoryId: this.categories.length ? String(this.categories[0].id) : ''
    });
  }
}
