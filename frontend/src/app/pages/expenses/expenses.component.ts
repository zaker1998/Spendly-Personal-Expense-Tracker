import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { ApiService, ExpenseFilters } from '../../core/api.service';
import { localToday } from '../../core/date-utils';
import { Category, Expense, PageResponse } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-expenses',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './expenses.component.html',
  styleUrl: './expenses.component.css'
})
export class ExpensesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Every load goes through here so switchMap can cancel the previous one.
   * This used to be a hand-rolled sequence number compared in each callback;
   * switchMap does the same job by construction, and also stops the abandoned
   * request rather than just ignoring its answer.
   */
  private readonly loadRequests = new Subject<number>();

  readonly expenses = signal<Expense[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly totalElements = signal(0);
  readonly page = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly suggesting = signal(false);
  readonly suggestionNote = signal('');
  readonly editing = signal<Expense | null>(null);

  readonly pageSize = PAGE_SIZE;
  readonly hasNextPage = computed(() => (this.page() + 1) * PAGE_SIZE < this.totalElements());
  readonly showPager = computed(() => this.totalElements() > PAGE_SIZE);

  readonly filters = this.fb.nonNullable.group({
    categoryId: [''],
    from: [''],
    to: [''],
    search: ['']
  });

  readonly form = this.fb.nonNullable.group({
    categoryId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    spentOn: [localToday(), Validators.required],
    description: ['']
  });

  constructor() {
    this.loadRequests
      .pipe(
        tap((page) => {
          this.page.set(page);
          this.loading.set(true);
        }),
        switchMap((page) =>
          this.api.getExpenses({ ...this.currentFilters(), page, size: PAGE_SIZE }).pipe(
            catchError((err: unknown) => {
              this.notifications.error(describeError(err, 'Could not load expenses'));
              return of(null);
            })
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe((result: PageResponse<Expense> | null) => {
        this.loading.set(false);
        if (result) {
          this.expenses.set(result.content);
          this.totalElements.set(result.totalElements);
        }
      });
  }

  ngOnInit(): void {
    this.api
      .getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => {
          this.categories.set(categories);
          if (categories.length && !this.form.value.categoryId) {
            this.form.patchValue({ categoryId: String(categories[0].id) });
          }
        },
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not load categories'))
      });
    this.load(0);
  }

  load(page = 0): void {
    this.loadRequests.next(Math.max(0, page));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const editing = this.editing();
    const value = this.form.getRawValue();
    const body = {
      categoryId: Number(value.categoryId),
      amount: Number(value.amount),
      spentOn: value.spentOn,
      description: value.description || undefined,
      version: editing?.version
    };

    this.saving.set(true);
    const request$ = editing
      ? this.api.updateExpense(editing.id, body)
      : this.api.createExpense(body);

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.load(this.page());
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.notifications.error(describeError(err, 'Could not save the expense'));
      }
    });
  }

  suggestCategory(): void {
    const description = this.form.value.description?.trim();
    if (!description) {
      this.suggestionNote.set('Type a description first');
      return;
    }
    this.suggesting.set(true);
    this.suggestionNote.set('');
    this.api
      .suggestCategory(description)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (suggestion) => {
          this.suggesting.set(false);
          if (suggestion.categoryId == null) {
            this.suggestionNote.set('No suggestion found');
            return;
          }
          this.form.patchValue({ categoryId: String(suggestion.categoryId) });
          this.suggestionNote.set(
            suggestion.source === 'AI'
              ? `AI suggests: ${suggestion.categoryName}`
              : `Suggested: ${suggestion.categoryName}`
          );
        },
        error: () => {
          this.suggesting.set(false);
          this.suggestionNote.set('Suggestion failed');
        }
      });
  }

  edit(expense: Expense): void {
    this.editing.set(expense);
    this.form.patchValue({
      categoryId: String(expense.categoryId),
      amount: expense.amount,
      spentOn: expense.spentOn,
      description: expense.description ?? ''
    });
  }

  remove(expense: Expense): void {
    if (!confirm(`Delete this expense (${expense.description || expense.categoryName})?`)) {
      return;
    }
    this.api
      .deleteExpense(expense.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // If this was the last row on the current page, step back one page
          // instead of reloading an empty page.
          const page =
            this.expenses().length === 1 && this.page() > 0 ? this.page() - 1 : this.page();
          this.load(page);
        },
        error: (err: unknown) =>
          this.notifications.error(describeError(err, 'Could not delete the expense'))
      });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.form.patchValue({
      amount: 0,
      description: '',
      spentOn: localToday(),
      categoryId: this.categories().length ? String(this.categories()[0].id) : ''
    });
  }

  exportCsv(): void {
    this.api
      .exportExpensesCsv(this.currentFilters())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = 'expenses.csv';
          link.click();
          URL.revokeObjectURL(url);
        },
        error: (err: unknown) => this.notifications.error(describeError(err, 'Export failed'))
      });
  }

  private currentFilters(): ExpenseFilters {
    const value = this.filters.getRawValue();
    return {
      categoryId: value.categoryId ? Number(value.categoryId) : null,
      from: value.from || null,
      to: value.to || null,
      search: value.search || null
    };
  }
}
