import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, Subject, catchError, of, switchMap, tap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AdminExpense, AppUser, PageResponse } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

const PAGE_SIZE = 20;

type Tab = 'users' | 'expenses';

interface LoadRequest {
  tab: Tab;
  page: number;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  /**
   * One stream for both tabs: switching tab while the other one is still
   * loading cancels it, instead of letting a late users response land on the
   * expenses view.
   */
  private readonly loads = new Subject<LoadRequest>();

  readonly tab = signal<Tab>('users');
  readonly users = signal<AppUser[]>([]);
  readonly expenses = signal<AdminExpense[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(true);

  readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: ['']
  });

  constructor() {
    this.loads
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap((request) =>
          this.fetch(request).pipe(
            catchError((err: unknown) => {
              this.notifications.error(
                describeError(
                  err,
                  request.tab === 'users' ? 'Could not load users' : 'Could not load expenses'
                )
              );
              return of(null);
            })
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe(() => this.loading.set(false));
  }

  ngOnInit(): void {
    this.showUsers();
  }

  showUsers(): void {
    this.tab.set('users');
    this.loads.next({ tab: 'users', page: 0 });
  }

  showExpenses(): void {
    this.tab.set('expenses');
    this.loads.next({ tab: 'expenses', page: 0 });
  }

  loadExpenses(): void {
    this.loads.next({ tab: 'expenses', page: 0 });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) {
      return;
    }
    this.loads.next({ tab: this.tab(), page });
  }

  private fetch(request: LoadRequest): Observable<unknown> {
    if (request.tab === 'users') {
      return this.api.getAdminUsers(request.page, PAGE_SIZE).pipe(
        tap((res) => {
          this.users.set(res.content);
          this.applyPageMeta(res);
        })
      );
    }
    const f = this.filters.getRawValue();
    return this.api
      .getAdminExpenses({
        from: f.from || null,
        to: f.to || null,
        page: request.page,
        size: PAGE_SIZE
      })
      .pipe(
        tap((res) => {
          this.expenses.set(res.content);
          this.applyPageMeta(res);
        })
      );
  }

  private applyPageMeta(res: PageResponse<unknown>): void {
    this.page.set(res.number);
    this.totalPages.set(res.totalPages);
    this.totalElements.set(res.totalElements);
  }
}
