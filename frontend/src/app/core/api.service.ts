import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Budget, Category, Expense, MonthlySummary, PageResponse } from './models';

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
  constructor(private http: HttpClient) {}

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${environment.apiUrl}/categories`);
  }

  createCategory(body: { name: string; color?: string }): Observable<Category> {
    return this.http.post<Category>(`${environment.apiUrl}/categories`, body);
  }

  updateCategory(id: number, body: { name: string; color?: string }): Observable<Category> {
    return this.http.put<Category>(`${environment.apiUrl}/categories/${id}`, body);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/categories/${id}`);
  }

  getExpenses(filters: ExpenseFilters = {}): Observable<PageResponse<Expense>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20))
      .set('sort', 'spentOn,desc');

    if (filters.categoryId != null) params = params.set('categoryId', filters.categoryId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.minAmount != null) params = params.set('minAmount', filters.minAmount);
    if (filters.maxAmount != null) params = params.set('maxAmount', filters.maxAmount);
    if (filters.search) params = params.set('search', filters.search);

    return this.http.get<PageResponse<Expense>>(`${environment.apiUrl}/expenses`, { params });
  }

  createExpense(body: {
    categoryId: number;
    amount: number;
    currency?: string;
    spentOn: string;
    description?: string;
  }): Observable<Expense> {
    return this.http.post<Expense>(`${environment.apiUrl}/expenses`, body);
  }

  updateExpense(
    id: number,
    body: {
      categoryId: number;
      amount: number;
      currency?: string;
      spentOn: string;
      description?: string;
    }
  ): Observable<Expense> {
    return this.http.put<Expense>(`${environment.apiUrl}/expenses/${id}`, body);
  }

  deleteExpense(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/expenses/${id}`);
  }

  getMonthlySummary(year?: number, month?: number): Observable<MonthlySummary> {
    let params = new HttpParams();
    if (year != null) params = params.set('year', year);
    if (month != null) params = params.set('month', month);
    return this.http.get<MonthlySummary>(`${environment.apiUrl}/summary/monthly`, { params });
  }

  getBudgets(year?: number, month?: number): Observable<Budget[]> {
    let params = new HttpParams();
    if (year != null) params = params.set('year', year);
    if (month != null) params = params.set('month', month);
    return this.http.get<Budget[]>(`${environment.apiUrl}/budgets`, { params });
  }

  createBudget(body: {
    categoryId?: number | null;
    amount: number;
    year: number;
    month: number;
  }): Observable<Budget> {
    return this.http.post<Budget>(`${environment.apiUrl}/budgets`, body);
  }

  updateBudget(
    id: number,
    body: { categoryId?: number | null; amount: number; year: number; month: number }
  ): Observable<Budget> {
    return this.http.put<Budget>(`${environment.apiUrl}/budgets/${id}`, body);
  }

  deleteBudget(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/budgets/${id}`);
  }

  exportExpensesCsv(filters: ExpenseFilters = {}): Observable<Blob> {
    let params = new HttpParams();
    if (filters.categoryId != null) params = params.set('categoryId', filters.categoryId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.minAmount != null) params = params.set('minAmount', filters.minAmount);
    if (filters.maxAmount != null) params = params.set('maxAmount', filters.maxAmount);
    if (filters.search) params = params.set('search', filters.search);
    return this.http.get(`${environment.apiUrl}/expenses/export`, {
      params,
      responseType: 'blob'
    });
  }
}
