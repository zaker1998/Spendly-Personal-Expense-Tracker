import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminExpense,
  AppUser,
  Budget,
  Category,
  CategorySuggestion,
  Expense,
  MonthlySummary,
  PageResponse
} from './models';

/**
 * Currency is set by the server, so it is deliberately not part of the payload.
 *
 * <p>{@link ExpensePayload.version} is the value the row had when it was loaded.
 * Sending it back is what lets the server reject an edit built on a copy someone
 * else has already replaced, instead of silently discarding their change.
 */
export interface ExpensePayload {
  categoryId: number;
  amount: number;
  spentOn: string;
  description?: string;
  version?: number;
}

export interface CategoryPayload {
  name: string;
  color?: string | null;
  version?: number;
}

export interface BudgetPayload {
  categoryId?: number | null;
  amount: number;
  year: number;
  month: number;
  version?: number;
}

export interface ExpenseFilters {
  categoryId?: number | null;
  from?: string | null;
  to?: string | null;
  minAmount?: number | null;
  maxAmount?: number | null;
  search?: string | null;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${environment.apiUrl}/categories`);
  }

  createCategory(body: CategoryPayload): Observable<Category> {
    return this.http.post<Category>(`${environment.apiUrl}/categories`, body);
  }

  updateCategory(id: number, body: CategoryPayload): Observable<Category> {
    return this.http.put<Category>(`${environment.apiUrl}/categories/${id}`, body);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/categories/${id}`);
  }

  getExpenses(filters: ExpenseFilters = {}): Observable<PageResponse<Expense>> {
    const params = this.filterParams(filters)
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20))
      .set('sort', 'spentOn,desc');

    return this.http.get<PageResponse<Expense>>(`${environment.apiUrl}/expenses`, { params });
  }

  createExpense(body: ExpensePayload): Observable<Expense> {
    return this.http.post<Expense>(`${environment.apiUrl}/expenses`, body);
  }

  updateExpense(id: number, body: ExpensePayload): Observable<Expense> {
    return this.http.put<Expense>(`${environment.apiUrl}/expenses/${id}`, body);
  }

  deleteExpense(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/expenses/${id}`);
  }

  suggestCategory(description: string): Observable<CategorySuggestion> {
    return this.http.post<CategorySuggestion>(`${environment.apiUrl}/expenses/suggest-category`, {
      description
    });
  }

  getMonthlySummary(year?: number, month?: number): Observable<MonthlySummary> {
    return this.http.get<MonthlySummary>(`${environment.apiUrl}/summary/monthly`, {
      params: this.periodParams(year, month)
    });
  }

  getBudgets(year?: number, month?: number): Observable<Budget[]> {
    return this.http.get<Budget[]>(`${environment.apiUrl}/budgets`, {
      params: this.periodParams(year, month)
    });
  }

  createBudget(body: BudgetPayload): Observable<Budget> {
    return this.http.post<Budget>(`${environment.apiUrl}/budgets`, body);
  }

  updateBudget(id: number, body: BudgetPayload): Observable<Budget> {
    return this.http.put<Budget>(`${environment.apiUrl}/budgets/${id}`, body);
  }

  deleteBudget(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/budgets/${id}`);
  }

  exportExpensesCsv(filters: ExpenseFilters = {}): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/expenses/export`, {
      params: this.filterParams(filters),
      responseType: 'blob'
    });
  }

  getAdminUsers(page = 0, size = 20): Observable<PageResponse<AppUser>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PageResponse<AppUser>>(`${environment.apiUrl}/admin/users`, { params });
  }

  getAdminExpenses(
    filters: { from?: string | null; to?: string | null; page?: number; size?: number } = {}
  ): Observable<PageResponse<AdminExpense>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<PageResponse<AdminExpense>>(`${environment.apiUrl}/admin/expenses`, {
      params
    });
  }

  /** The list and the export take the same filters; building them twice drifted. */
  private filterParams(filters: ExpenseFilters): HttpParams {
    let params = new HttpParams();
    if (filters.categoryId != null) params = params.set('categoryId', filters.categoryId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.minAmount != null) params = params.set('minAmount', filters.minAmount);
    if (filters.maxAmount != null) params = params.set('maxAmount', filters.maxAmount);
    if (filters.search) params = params.set('search', filters.search);
    return params;
  }

  private periodParams(year?: number, month?: number): HttpParams {
    let params = new HttpParams();
    if (year != null) params = params.set('year', year);
    if (month != null) params = params.set('month', month);
    return params;
  }
}
