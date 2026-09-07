import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { ScopeService } from './scope.service';

describe('HTTP session boundaries', () => {
  afterEach(() => TestBed.resetTestingModule());
  function setup() {
    const auth={accessToken:'old',sessionVersion:1,refresh:vi.fn(()=>of({accessToken:'fresh'})),logout:vi.fn(),isSessionInvalid:(e:any)=>[401,403].includes(e?.status)};
    TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),
      {provide:AuthService,useValue:auth},{provide:ScopeService,useValue:{header:()=>null}}]});
    return {auth,http:TestBed.inject(HttpClient),mock:TestBed.inject(HttpTestingController)};
  }

  it('does not log out after a valid refresh when the requested feature is forbidden', () => {
    const {auth,http,mock}=setup();const failed=vi.fn();
    http.get('/api/staff').subscribe({error:failed});
    mock.expectOne('/api/staff').flush({}, {status:401,statusText:'Unauthorized'});
    const retry=mock.expectOne('/api/staff');expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
    retry.flush({}, {status:403,statusText:'Forbidden'});
    expect(failed).toHaveBeenCalled();expect(auth.logout).not.toHaveBeenCalled();mock.verify();
  });

  it('logs out when the refresh itself is rejected', () => {
    const {auth,http,mock}=setup();
    auth.refresh.mockImplementation(()=>throwError(()=>new HttpErrorResponse({status:401})));
    http.get('/api/staff').subscribe({error:()=>undefined});
    mock.expectOne('/api/staff').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.logout).toHaveBeenCalledWith('expired');mock.verify();
  });

  it('does not refresh a different account for a stale request', () => {
    const {auth,http,mock}=setup();
    http.get('/api/staff').subscribe({error:()=>undefined});auth.sessionVersion++;
    mock.expectOne('/api/staff').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.refresh).not.toHaveBeenCalled();expect(auth.logout).not.toHaveBeenCalled();mock.verify();
  });
});
