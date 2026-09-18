import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CategoriesComponent } from './categories.component';
import { NotificationService } from '../../core/notification.service';
import { environment } from '../../../environments/environment';
import { category } from '../../testing/http-fixtures';

const API = environment.apiUrl;

describe('CategoriesComponent', () => {
  let fixture: ComponentFixture<CategoriesComponent>;
  let component: CategoriesComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CategoriesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    fixture = TestBed.createComponent(CategoriesComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/categories`).flush([category({ id: 3, name: 'Travel', version: 2 })]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('lists the categories', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Travel');
  });

  it('creates without a version', () => {
    component.form.setValue({ name: 'Gifts', color: '#112233' });

    component.submit();

    const req = http.expectOne(`${API}/categories`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Gifts', color: '#112233' });
    req.flush(category({ id: 4, name: 'Gifts' }));
    http.expectOne(`${API}/categories`).flush([]);
  });

  it('updates with the version it loaded', () => {
    component.edit(component.categories()[0]);
    component.form.patchValue({ name: 'Trips' });

    component.submit();

    const req = http.expectOne(`${API}/categories/3`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Trips', color: '#E76F51', version: 2 });
    req.flush(category({ id: 3, name: 'Trips', version: 3 }));
    http.expectOne(`${API}/categories`).flush([]);
    expect(component.editing()).toBeNull();
  });

  /** A 409 from the version check has to reach the user, not vanish. */
  it('tells the user when someone else changed the category first', () => {
    const notifications = TestBed.inject(NotificationService);
    spyOn(notifications, 'error');
    component.edit(component.categories()[0]);

    component.submit();
    http
      .expectOne(`${API}/categories/3`)
      .flush(
        { message: 'This item was changed somewhere else. Reload it and try again.', fields: null },
        { status: 409, statusText: 'Conflict' }
      );

    expect(notifications.error).toHaveBeenCalledWith(
      'This item was changed somewhere else. Reload it and try again.'
    );
  });

  it('refuses an empty name without calling the server', () => {
    component.form.setValue({ name: '', color: '#112233' });

    component.submit();

    http.expectNone(`${API}/categories`);
    expect(component.saving()).toBeFalse();
    expect(component.form.controls.name.hasError('required')).toBeTrue();
  });
});
