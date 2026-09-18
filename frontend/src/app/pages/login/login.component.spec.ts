import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';
import { environment } from '../../../environments/environment';

const API = environment.apiUrl;

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
    // The wake-up ping for the sleeping demo server.
    http.expectOne('/actuator/health').flush('ok');
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('fills in the demo credentials', () => {
    component.fillDemo();
    expect(component.form.getRawValue()).toEqual({
      email: 'demo@spendly.app',
      password: 'Demo123!'
    });
  });

  it('signs in and goes to the dashboard', () => {
    component.fillDemo();

    component.submit();
    http.expectOne(`${API}/auth/login`).flush({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 1,
      email: 'demo@spendly.app',
      role: 'USER'
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(component.loading()).toBeFalse();
  });

  it('shows the server message for wrong credentials', () => {
    component.fillDemo();

    component.submit();
    http
      .expectOne(`${API}/auth/login`)
      .flush(
        { message: 'Invalid email or password', fields: null },
        { status: 401, statusText: 'Unauthorized' }
      );
    fixture.detectChanges();

    expect(component.error()).toBe('Invalid email or password');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent
    ).toContain('Invalid email or password');
  });

  /** The lockout answer carries its own explanation; it should reach the user as-is. */
  it('passes the lockout message through', () => {
    component.fillDemo();

    component.submit();
    http
      .expectOne(`${API}/auth/login`)
      .flush(
        { message: 'Too many failed login attempts. Try again in 840s.', fields: null },
        { status: 429, statusText: 'Too Many Requests' }
      );

    expect(component.error()).toContain('Too many failed login attempts');
  });

  it('explains a slow sign-in instead of spinning silently', fakeAsync(() => {
    component.fillDemo();

    component.submit();
    tick(4000);
    fixture.detectChanges();

    expect(component.slow()).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Waking the demo server');

    http
      .expectOne(`${API}/auth/login`)
      .flush('gateway', { status: 504, statusText: 'Gateway Timeout' });
    expect(component.slow()).toBeFalse();
    expect(component.error()).toContain('Cannot reach the server');
  }));

  it('does not send an invalid form', () => {
    component.form.setValue({ email: 'not-an-email', password: '' });

    component.submit();

    http.expectNone(`${API}/auth/login`);
    expect(component.loading()).toBeFalse();
    expect(component.form.touched).toBeTrue();
  });
});
