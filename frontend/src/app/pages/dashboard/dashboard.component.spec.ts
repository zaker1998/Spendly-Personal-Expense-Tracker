import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { NotificationService } from '../../core/notification.service';
import { Budget, MonthlySummary } from '../../core/models';
import { environment } from '../../../environments/environment';
import { expense, page } from '../../testing/http-fixtures';

const API = environment.apiUrl;

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let http: HttpTestingController;

  const summary: MonthlySummary = {
    year: 2026,
    month: 3,
    totalAmount: 300,
    currency: 'EUR',
    byCategory: [
      { categoryId: 1, categoryName: 'Rent', total: 200 },
      { categoryId: 2, categoryName: 'Food', total: 100 }
    ]
  };

  const budget: Budget = {
    id: 5,
    categoryId: 1,
    categoryName: 'Rent',
    limitAmount: 150,
    spentAmount: 200,
    remainingAmount: -50,
    percentUsed: 133,
    overBudget: true,
    year: 2026,
    month: 3,
    currency: 'EUR',
    version: 0
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    component.period.setValue({ year: 2026, month: 3 });
  });

  afterEach(() => http.verify());

  function answerAll(): void {
    http.expectOne((r) => r.url === `${API}/summary/monthly`).flush(summary);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([budget]);
    http
      .expectOne((r) => r.url === `${API}/expenses`)
      .flush(page([expense({ description: 'Rent March' })]));
    fixture.detectChanges();
  }

  it('asks for the whole selected month', () => {
    fixture.detectChanges();

    const recent = http.expectOne((r) => r.url === `${API}/expenses`);
    expect(recent.request.params.get('from')).toBe('2026-03-01');
    expect(recent.request.params.get('to')).toBe('2026-03-31');
    recent.flush(page([]));
    http.expectOne((r) => r.url === `${API}/summary/monthly`).flush(summary);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
  });

  it('renders the total, the category bars and the recent list', () => {
    fixture.detectChanges();
    answerAll();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('€300.00');
    expect(text).toContain('Rent March');
    expect(component.barWidth(200)).toBe(100);
    expect(component.barWidth(100)).toBe(50);
  });

  it('marks an overspent budget', () => {
    fixture.detectChanges();
    answerAll();

    const item = (fixture.nativeElement as HTMLElement).querySelector('li.over');
    expect(item?.textContent).toContain('133%');
  });

  it('wraps from January back to December of the previous year', () => {
    component.period.setValue({ year: 2026, month: 1 });

    component.shiftMonth(-1);

    expect(component.period.getRawValue()).toEqual({ year: 2025, month: 12 });
    http
      .match(() => true)
      .forEach((r) => r.flush(r.request.url.endsWith('/budgets') ? [] : summary));
  });

  it('cancels the previous month when the user moves on before it loads', () => {
    fixture.detectChanges();
    const firstSummary = http.expectOne((r) => r.url === `${API}/summary/monthly`);
    http.match(() => true);

    component.shiftMonth(1);

    expect(firstSummary.cancelled).toBeTrue();
    answerAll();
  });

  it('reports a failed load and stops showing the spinner', () => {
    const notifications = TestBed.inject(NotificationService);
    spyOn(notifications, 'error');
    fixture.detectChanges();

    http
      .expectOne((r) => r.url === `${API}/summary/monthly`)
      .flush('down', { status: 503, statusText: 'Unavailable' });
    http.match(() => true);

    expect(notifications.error).toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });
});
