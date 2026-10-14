import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';
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
      { categoryId: 1, categoryName: 'Rent', categoryColor: '#264653', total: 200 },
      { categoryId: 2, categoryName: 'Food', categoryColor: null, total: 100 }
    ]
  };

  const february: MonthlySummary = { ...summary, month: 2, totalAmount: 250, byCategory: [] };

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

  function summaryFor(year: number, month: number): TestRequest {
    return http.expectOne(
      (r) =>
        r.url === `${API}/summary/monthly` &&
        r.params.get('year') === String(year) &&
        r.params.get('month') === String(month)
    );
  }

  function answerAll(previous: MonthlySummary = february): void {
    summaryFor(2026, 3).flush(summary);
    summaryFor(2026, 2).flush(previous);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([budget]);
    http
      .expectOne((r) => r.url === `${API}/expenses`)
      .flush(page([expense({ description: 'Rent March' })], 12));
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('asks for the whole selected month', () => {
    fixture.detectChanges();

    const recent = http.expectOne((r) => r.url === `${API}/expenses`);
    expect(recent.request.params.get('from')).toBe('2026-03-01');
    expect(recent.request.params.get('to')).toBe('2026-03-31');
    recent.flush(page([]));
    summaryFor(2026, 3).flush(summary);
    summaryFor(2026, 2).flush(february);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
  });

  it('renders the total, the category breakdown and the recent list', () => {
    fixture.detectChanges();
    answerAll();

    expect(text()).toContain('€300.00');
    expect(text()).toContain('Rent March');
    expect(component.barWidth(200)).toBe(100);
    expect(component.barWidth(100)).toBe(50);
    expect(component.share(200)).toBe(67);
  });

  it('compares the month with the one before it', () => {
    fixture.detectChanges();
    answerAll();

    expect(component.changeVsPrevious()).toBe(20);
    expect(text()).toContain('20% more than February');
    expect(text()).toContain('12'); // transactions this month, not just the five listed
  });

  it('compares January with December of the previous year', () => {
    component.period.setValue({ year: 2026, month: 1 });
    fixture.detectChanges();

    summaryFor(2025, 12).flush(february);
    http
      .match(() => true)
      .forEach((r) =>
        r.flush(
          r.request.url.endsWith('/budgets')
            ? []
            : r.request.url.endsWith('/expenses')
              ? page([])
              : summary
        )
      );
  });

  it('still renders when only the previous month fails', () => {
    fixture.detectChanges();
    summaryFor(2026, 2).flush('down', { status: 503, statusText: 'Unavailable' });
    summaryFor(2026, 3).flush(summary);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
    http.expectOne((r) => r.url === `${API}/expenses`).flush(page([]));
    fixture.detectChanges();

    expect(text()).toContain('€300.00');
    expect(component.changeVsPrevious()).toBeNull();
  });

  it('draws one ring slice per category, starting at twelve o’clock', () => {
    fixture.detectChanges();
    answerAll();

    const slices = component.slices();
    expect(slices.length).toBe(2);
    expect(slices[0].offset).toBe(25);
    expect(slices[0].colour).toBe('#264653');
    // No colour of its own: falls back to the palette rather than to nothing.
    expect(slices[1].colour).toMatch(/^#[0-9a-f]{6}$/i);
    expect(Math.round(slices[0].share + slices[1].share)).toBe(100);
  });

  it('marks an overspent budget', () => {
    fixture.detectChanges();
    answerAll();

    const item = (fixture.nativeElement as HTMLElement).querySelector('li.over');
    expect(item?.textContent).toContain('133%');
    expect(component.budgetTone(budget)).toBe('over');
    expect(component.budgetTone({ ...budget, overBudget: false, percentUsed: 90 })).toBe('caution');
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
    const firstSummary = summaryFor(2026, 3);
    http.match(() => true);

    component.shiftMonth(1);

    expect(firstSummary.cancelled).toBeTrue();
    summaryFor(2026, 4).flush(summary);
    summaryFor(2026, 3).flush(february);
    http.expectOne((r) => r.url === `${API}/budgets`).flush([]);
    http.expectOne((r) => r.url === `${API}/expenses`).flush(page([]));
  });

  it('reports a failed load and stops showing the spinner', () => {
    const notifications = TestBed.inject(NotificationService);
    spyOn(notifications, 'error');
    fixture.detectChanges();

    summaryFor(2026, 3).flush('down', { status: 503, statusText: 'Unavailable' });
    http.match(() => true);

    expect(notifications.error).toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });
});
