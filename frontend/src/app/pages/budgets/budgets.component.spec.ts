import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { BudgetsComponent } from './budgets.component';
import { Budget } from '../../core/models';
import { environment } from '../../../environments/environment';
import { category } from '../../testing/http-fixtures';

const API = environment.apiUrl;

describe('BudgetsComponent', () => {
  let fixture: ComponentFixture<BudgetsComponent>;
  let component: BudgetsComponent;
  let http: HttpTestingController;

  const overall: Budget = {
    id: 9,
    categoryId: null,
    categoryName: 'Overall',
    limitAmount: 1000,
    spentAmount: 250,
    remainingAmount: 750,
    percentUsed: 25,
    overBudget: false,
    year: 2026,
    month: 3,
    currency: 'EUR',
    version: 1
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BudgetsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(BudgetsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/categories`).flush([category()]);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([overall]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows how much of the limit is left', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('€750.00 left');
  });

  it('sends an empty category as an overall budget', () => {
    component.form.setValue({ categoryId: '', amount: 500, year: 2026, month: 4 });

    component.submit();

    const req = http.expectOne(`${API}/budgets`);
    expect(req.request.body).toEqual({
      categoryId: null,
      amount: 500,
      year: 2026,
      month: 4,
      version: undefined
    });
    req.flush(overall);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
  });

  it('updates with the version it loaded', () => {
    component.edit(overall);

    component.submit();

    const req = http.expectOne(`${API}/budgets/9`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(1);
    req.flush(overall);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
  });

  it('reloads for the period in the form', () => {
    component.form.patchValue({ year: 2025, month: 12 });

    component.reload();

    const req = http.expectOne((r) => r.url === `${API}/budgets`);
    expect(req.request.params.get('year')).toBe('2025');
    expect(req.request.params.get('month')).toBe('12');
    req.flush([]);
  });
});
