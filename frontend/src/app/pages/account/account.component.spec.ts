import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AccountComponent } from './account.component';
import { NotificationService } from '../../core/notification.service';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;

describe('AccountComponent', () => {
  let fixture: ComponentFixture<AccountComponent>;
  let component: AccountComponent;
  let http: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AccountComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    fixture = TestBed.createComponent(AccountComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('refuses two different new passwords without asking the server', () => {
    component.form.setValue({
      currentPassword: 'Secret123!',
      newPassword: 'BrandNew456!',
      confirmPassword: 'BrandNew457!'
    });

    component.submit();

    http.expectNone(`${API}/auth/change-password`);
    expect(component.error()).toBe('The two new passwords do not match.');
  });

  it('changes the password and says other devices were signed out', () => {
    spyOn(notifications, 'success');
    component.form.setValue({
      currentPassword: 'Secret123!',
      newPassword: 'BrandNew456!',
      confirmPassword: 'BrandNew456!'
    });

    component.submit();
    const req = http.expectOne(`${API}/auth/change-password`);
    expect(req.request.body).toEqual({
      currentPassword: 'Secret123!',
      newPassword: 'BrandNew456!'
    });
    req.flush({
      accessToken: 'fresh',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 1,
      email: 'a@b.c',
      role: 'USER'
    });

    expect(notifications.success).toHaveBeenCalled();
    expect(component.form.getRawValue().newPassword).toBe('');
  });

  it('shows why the change was refused', () => {
    component.form.setValue({
      currentPassword: 'wrong',
      newPassword: 'BrandNew456!',
      confirmPassword: 'BrandNew456!'
    });

    component.submit();
    http
      .expectOne(`${API}/auth/change-password`)
      .flush(
        { message: 'Invalid email or password', fields: null },
        { status: 401, statusText: 'Unauthorized' }
      );

    expect(component.error()).toBe('Invalid email or password');
    expect(component.saving()).toBeFalse();
  });
});
