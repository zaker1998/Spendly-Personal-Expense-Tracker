import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AdminComponent } from './admin.component';
import { AdminExpense, AppUser, PageResponse } from '../../core/models';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;

function pageOf<T>(content: T[], totalPages = 1, number = 0): PageResponse<T> {
  return { content, totalElements: content.length * totalPages, totalPages, number, size: 20 };
}

describe('AdminComponent', () => {
  let fixture: ComponentFixture<AdminComponent>;
  let component: AdminComponent;
  let http: HttpTestingController;

  const user: AppUser = {
    id: 1,
    email: 'demo@spendly.app',
    role: 'USER',
    createdAt: '2026-01-01T00:00:00Z'
  };
  const row: AdminExpense = {
    id: 5,
    userId: 1,
    userEmail: 'demo@spendly.app',
    categoryId: 1,
    categoryName: 'Food',
    amount: 9,
    currency: 'EUR',
    spentOn: '2026-03-01',
    description: 'Snack',
    createdAt: '2026-03-01T00:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(AdminComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('opens on the users tab', () => {
    http.expectOne((r) => r.url === `${API}/admin/users`).flush(pageOf([user]));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('demo@spendly.app');
  });

  /** A slow users response must not land on the expenses view. */
  it('cancels the users load when switching to expenses', () => {
    const users = http.expectOne((r) => r.url === `${API}/admin/users`);

    component.showExpenses();

    expect(users.cancelled).toBeTrue();
    http.expectOne((r) => r.url === `${API}/admin/expenses`).flush(pageOf([row]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Snack');
  });

  it('passes the date filters to the expenses query', () => {
    http.expectOne((r) => r.url === `${API}/admin/users`).flush(pageOf([user]));
    component.showExpenses();
    http.expectOne((r) => r.url === `${API}/admin/expenses`).flush(pageOf([row]));

    component.filters.setValue({ from: '2026-03-01', to: '2026-03-31' });
    component.loadExpenses();

    const req = http.expectOne((r) => r.url === `${API}/admin/expenses`);
    expect(req.request.params.get('from')).toBe('2026-03-01');
    expect(req.request.params.get('to')).toBe('2026-03-31');
    req.flush(pageOf([]));
  });

  it('ignores a page number outside the result', () => {
    http.expectOne((r) => r.url === `${API}/admin/users`).flush(pageOf([user], 2));

    component.goToPage(5);
    component.goToPage(-1);
    http.expectNone((r) => r.url === `${API}/admin/users`);

    component.goToPage(1);
    const next = http.expectOne((r) => r.url === `${API}/admin/users`);
    expect(next.request.params.get('page')).toBe('1');
    next.flush(pageOf([user], 2, 1));
  });
});
