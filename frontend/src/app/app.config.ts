import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { globalLoadingInterceptor } from './core/global-loading.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    // Angular 21 zoneless change detection — driven by Signals.
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([globalLoadingInterceptor, authInterceptor])),
  ],
};
