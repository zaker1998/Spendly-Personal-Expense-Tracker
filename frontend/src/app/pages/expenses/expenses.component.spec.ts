import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExpensesComponent } from './expenses.component';
import { NotificationService } from '../../core/notification.service';
import { environment } from '../../../environments/environment';
import { category, expense, page } from '../../testing/http-fixtures';

const API = environment.apiUrl;

describe('ExpensesComponent', () => {
  let fixture: ComponentFixture<ExpensesComponent>;
  let component: ExpensesComponent;
  let http: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ExpensesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(ExpensesComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => http.verify());

  function expensesRequest(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/expenses` && r.method === 'GET');
  }

  /** Runs ngOnInit and answers its two requests. */
  function start(rows = [expense()], total = rows.length): void {
    fixture.detectChanges();
    http
      .expectOne(`${API}/categories`)
      .flush([category({ id: 1 }), category({ id: 2, name: 'Rent' })]);
    expensesRequest().flush(page(rows, total));
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows the loaded expenses and preselects the first category', () => {
    start([expense({ description: 'Groceries at Billa' })]);

    expect(text()).toContain('Groceries at Billa');
    expect(component.form.value.categoryId).toBe('1');
  });

  it('says so when nothing matches', () => {
    start([]);
    expect(text()).toContain('No expenses match your filters.');
  });

  it('sends the filters with the list request', () => {
    start();
    component.filters.setValue({
      categoryId: '2',
      from: '2026-03-01',
      to: '2026-03-31',
      search: 'coffee'
    });

    component.load(0);

    const req = expensesRequest();
    expect(req.request.params.get('categoryId')).toBe('2');
    expect(req.request.params.get('from')).toBe('2026-03-01');
    expect(req.request.params.get('to')).toBe('2026-03-31');
    expect(req.request.params.get('search')).toBe('coffee');
    req.flush(page([]));
  });

  /**
   * The sequence-number guard this replaced only ignored a stale answer;
   * switchMap cancels the stale request outright.
   */
  it('cancels an in-flight load when a newer one starts', () => {
    start();

    component.load(1);
    const first = expensesRequest();
    component.load(2);
    const second = expensesRequest();

    expect(first.cancelled).toBeTrue();
    second.flush(page([expense({ description: 'From page three' })], 30, 2));
    fixture.detectChanges();

    expect(component.page()).toBe(2);
    expect(text()).toContain('From page three');
  });

  it('creates a new expense and reloads the page', () => {
    start();
    component.form.setValue({
      categoryId: '2',
      amount: 42.5,
      spentOn: '2026-03-20',
      description: 'Rent top-up'
    });

    component.submit();

    const create = http.expectOne(`${API}/expenses`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      categoryId: 2,
      amount: 42.5,
      spentOn: '2026-03-20',
      description: 'Rent top-up',
      version: undefined
    });
    create.flush(expense({ id: 11 }));
    expensesRequest().flush(page([]));
  });

  /** The version is what lets the server refuse an edit made on a stale copy. */
  it('sends the loaded version back when updating', () => {
    const existing = expense({ id: 10, version: 4 });
    start([existing]);

    component.edit(existing);
    component.form.patchValue({ amount: 99 });
    component.submit();

    const update = http.expectOne(`${API}/expenses/10`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.version).toBe(4);
    expect(update.request.body.amount).toBe(99);
    update.flush({ ...existing, amount: 99, version: 5 });
    expensesRequest().flush(page([]));

    expect(component.editing()).toBeNull();
  });

  it('does not submit an invalid form', () => {
    start();
    component.form.patchValue({ amount: 0 });

    component.submit();

    http.expectNone(`${API}/expenses`);
    expect(component.form.touched).toBeTrue();
  });

  it('reports a failed save through the notification area', () => {
    start();
    spyOn(notifications, 'error');
    component.form.patchValue({ amount: 5, spentOn: '9999-01-01' });

    component.submit();

    http
      .expectOne(`${API}/expenses`)
      .flush(
        { message: 'Validation failed', fields: { spentOn: 'must be between 2000 and 2100' } },
        { status: 400, statusText: 'Bad Request' }
      );

    expect(notifications.error).toHaveBeenCalledWith('Spent on must be between 2000 and 2100');
    expect(component.saving()).toBeFalse();
  });

  it('steps back a page after deleting the last row on it', () => {
    const only = expense({ id: 77 });
    start();
    component.load(3);
    expensesRequest().flush(page([only], 31, 3));
    spyOn(window, 'confirm').and.returnValue(true);

    component.remove(only);
    http.expectOne(`${API}/expenses/77`).flush(null);

    const reload = expensesRequest();
    expect(reload.request.params.get('page')).toBe('2');
    reload.flush(page([expense()], 30, 2));
  });

  it('does nothing when the delete is not confirmed', () => {
    start();
    spyOn(window, 'confirm').and.returnValue(false);

    component.remove(expense());

    http.expectNone(`${API}/expenses/10`);
    expect(window.confirm).toHaveBeenCalled();
  });

  it('applies a category suggestion and announces where it came from', () => {
    start();
    component.form.patchValue({ description: 'monthly rent' });

    component.suggestCategory();
    http
      .expectOne(`${API}/expenses/suggest-category`)
      .flush({ categoryId: 2, categoryName: 'Rent', source: 'AI' });

    expect(component.form.value.categoryId).toBe('2');
    expect(component.suggestionNote()).toBe('AI suggests: Rent');
  });

  it('asks for a description before suggesting', () => {
    start();

    component.suggestCategory();

    http.expectNone(`${API}/expenses/suggest-category`);
    expect(component.suggestionNote()).toBe('Type a description first');
  });

  it('shows the pager only when there is more than one page', () => {
    start([expense()], 1);
    expect(component.showPager()).toBeFalse();

    component.load(0);
    expensesRequest().flush(page([expense()], 25));

    expect(component.showPager()).toBeTrue();
    expect(component.hasNextPage()).toBeTrue();
  });
});
