import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiService } from './api.service';
import { environment } from '../../environments/environment';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('omits filter params that are not set', () => {
    api.getExpenses({ page: 1, size: 10 }).subscribe();

    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/expenses`);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('categoryId')).toBeFalse();
    expect(req.request.params.has('search')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('sends the filters that are set', () => {
    api.getExpenses({ categoryId: 3, from: '2026-01-01', search: 'coffee' }).subscribe();

    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/expenses`);
    expect(req.request.params.get('categoryId')).toBe('3');
    expect(req.request.params.get('from')).toBe('2026-01-01');
    expect(req.request.params.get('search')).toBe('coffee');
    expect(req.request.params.has('to')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('does not send a currency when creating an expense', () => {
    api.createExpense({ categoryId: 1, amount: 9.5, spentOn: '2026-05-01' }).subscribe();

    const req = http.expectOne(`${environment.apiUrl}/expenses`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.currency).toBeUndefined();
    req.flush({});
  });

  it('requests admin expenses as a page', () => {
    api.getAdminExpenses({ page: 2, size: 50 }).subscribe();

    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/admin/expenses`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 50 });
  });
});
