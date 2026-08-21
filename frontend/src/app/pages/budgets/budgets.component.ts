import { CurrencyPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { Budget, Category } from '../../core/models';

@Component({
  selector: 'app-budgets',
  standalone: true,
  imports: [ReactiveFormsModule, NgFor, NgIf, CurrencyPipe],
  templateUrl: './budgets.component.html',
  styleUrl: './budgets.component.css'
})
export class BudgetsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  budgets: Budget[] = [];
  categories: Category[] = [];
  error = '';
  saving = false;
  editingId: number | null = null;

  readonly now = new Date();

  form = this.fb.nonNullable.group({
    categoryId: [''],
    amount: [100, [Validators.required, Validators.min(0.01)]],
    year: [this.now.getFullYear(), Validators.required],
    month: [this.now.getMonth() + 1, Validators.required]
  });

  ngOnInit(): void {
    this.api.getCategories().subscribe({ next: (c) => (this.categories = c) });
    this.reload();
  }

  reload(): void {
    const { year, month } = this.form.getRawValue();
    this.api.getBudgets(year, month).subscribe({
      next: (budgets) => {
        this.budgets = budgets;
        this.error = '';
      },
      error: () => (this.error = 'Failed to load budgets')
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = {
      categoryId: value.categoryId ? Number(value.categoryId) : null,
      amount: Number(value.amount),
      year: Number(value.year),
      month: Number(value.month)
    };

    const req$ =
      this.editingId == null
        ? this.api.createBudget(body)
        : this.api.updateBudget(this.editingId, body);

    this.saving = true;
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.error = '';
        this.editingId = null;
        this.form.patchValue({ amount: 100, categoryId: '' });
        this.reload();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Save failed';
      }
    });
  }

  edit(budget: Budget): void {
    this.editingId = budget.id;
    this.form.patchValue({
      categoryId: budget.categoryId != null ? String(budget.categoryId) : '',
      amount: budget.limitAmount,
      year: budget.year,
      month: budget.month
    });
  }

  remove(budget: Budget): void {
    if (!confirm(`Delete the ${budget.categoryName} budget?`)) {
      return;
    }
    this.api.deleteBudget(budget.id).subscribe({
      next: () => {
        this.error = '';
        this.reload();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Delete failed')
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form.patchValue({ amount: 100, categoryId: '' });
  }
}
