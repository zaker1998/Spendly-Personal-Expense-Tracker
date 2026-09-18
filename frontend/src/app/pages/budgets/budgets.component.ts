import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { describeError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { Budget, Category } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

@Component({
  selector: 'app-budgets',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './budgets.component.html',
  styleUrl: './budgets.component.css'
})
export class BudgetsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly now = new Date();

  readonly budgets = signal<Budget[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly saving = signal(false);
  readonly editing = signal<Budget | null>(null);

  readonly form = this.fb.nonNullable.group({
    categoryId: [''],
    amount: [100, [Validators.required, Validators.min(0.01)]],
    year: [this.now.getFullYear(), Validators.required],
    month: [this.now.getMonth() + 1, Validators.required]
  });

  ngOnInit(): void {
    this.api
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not load categories'))
      });
    this.reload();
  }

  reload(): void {
    const { year, month } = this.form.getRawValue();
    this.api
      .getBudgets(year, month)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (budgets) => this.budgets.set(budgets),
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not load budgets'))
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const editing = this.editing();
    const value = this.form.getRawValue();
    const body = {
      categoryId: value.categoryId ? Number(value.categoryId) : null,
      amount: Number(value.amount),
      year: Number(value.year),
      month: Number(value.month),
      version: editing?.version
    };

    this.saving.set(true);
    const request$ = editing
      ? this.api.updateBudget(editing.id, body)
      : this.api.createBudget(body);

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.reload();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.notifications.error(describeError(err, 'Could not save the budget'));
      }
    });
  }

  edit(budget: Budget): void {
    this.editing.set(budget);
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
    this.api
      .deleteBudget(budget.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(),
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not delete the budget'))
      });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.form.patchValue({ amount: 100, categoryId: '' });
  }
}
