import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    // Every component is OnPush and state is held in signals, so change
    // detection only has to visit what actually changed.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // withFetch: the XHR backend cannot stream, and the CSV export is a stream.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor]))
  ]
};
