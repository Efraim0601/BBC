import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { FoundationApi, TermManagementWindowView } from '../../core/foundation.api';
import { TermManagementWindowsComponent } from './term-management-windows';

const row = (sequenceNo: number, code: string, governedPeriodCodes: string[], limited = false): TermManagementWindowView => ({
  academicSessionId: 'session', termId: `term-${sequenceNo}`, termCode: code, termLabel: `Trimester ${sequenceNo}`,
  termSequenceNo: sequenceNo, termStartDate: '2026-09-01', termEndDate: '2026-11-30', limited,
  opensAt: limited ? '2026-09-01T08:00:00Z' : null, closesAt: limited ? '2026-11-30T17:00:00Z' : null,
  timezone: 'Africa/Douala', governedPeriodCodes, state: limited ? 'OPEN' : 'OPEN', nextTransition: null, version: 4,
});

describe('TermManagementWindowsComponent', () => {
  let fixture: ComponentFixture<TermManagementWindowsComponent>;
  let component: any;
  let api: { updateTermManagementWindow: ReturnType<typeof vi.fn> };

  const setup = (rows: TermManagementWindowView[], english = true) => {
    api = { updateTermManagementWindow: vi.fn((_: string, __: string, body: unknown) => of({ ...rows[0], ...(body as Record<string, unknown>) })) };
    TestBed.configureTestingModule({
      imports: [TermManagementWindowsComponent],
      providers: [{ provide: FoundationApi, useValue: api }],
    });
    fixture = TestBed.createComponent(TermManagementWindowsComponent);
    component = fixture.componentInstance as any;
    component.target = { id: 'session' };
    component.windowRows = rows;
    component.canManage = true;
    component.english = english;
    component.sync(rows);
    fixture.detectChanges();
  };

  it('renders three friendly cards and shows annual result under T3', () => {
    setup([
      row(1, 'T1', ['S1', 'S2', 'T1_RESULT']),
      row(2, 'T2', ['S3', 'S4', 'T2_RESULT']),
      row(3, 'T3', ['S5', 'S6', 'T3_RESULT', 'ANNUAL']),
    ]);

    expect(fixture.nativeElement.querySelectorAll('.term-access-card')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Annual result');
    expect(fixture.nativeElement.textContent).toContain('No date restriction');
  });

  it('allows null/null and one-sided windows', () => {
    setup([row(1, 'T1', ['S1', 'S2', 'T1_RESULT'])], false);
    const current = component.windows()[0];

    component.set(current, 'limited', true);
    component.save(current);
    fixture.detectChanges();

    expect(component.errorFor(current, 'opensAt')).toBeNull();
    expect(component.errorFor(current, 'closesAt')).toBeNull();
    expect(api.updateTermManagementWindow).toHaveBeenCalledWith('session', 'term-1', expect.objectContaining({ limited: true, opensAt: null, closesAt: null, version: 4 }));
    component.set(current, 'opensAt', '2026-10-01T10:00');
    component.save(current);
    expect(api.updateTermManagementWindow).toHaveBeenLastCalledWith('session', 'term-1', expect.objectContaining({ opensAt: expect.any(String), closesAt: null }));
    component.set(current, 'opensAt', '');
    component.set(current, 'closesAt', '2026-11-30T17:00');
    component.save(current);
    expect(api.updateTermManagementWindow).toHaveBeenLastCalledWith('session', 'term-1', expect.objectContaining({ opensAt: null, closesAt: expect.any(String) }));
  });

  it('validates close-after-open and uses a custom confirmation for removal', () => {
    setup([row(1, 'T1', ['S1', 'S2', 'T1_RESULT'], true)], false);
    const current = component.windows()[0];

    component.set(current, 'opensAt', '2026-10-01T10:00');
    component.set(current, 'closesAt', '2026-10-01T09:00');
    component.save(current);
    expect(component.errorFor(current, 'closesAt')).toBe('La fermeture doit être postérieure à l’ouverture.');
    expect(api.updateTermManagementWindow).not.toHaveBeenCalled();

    component.set(current, 'closesAt', '2026-11-30T17:00');
    component.set(current, 'limited', false);
    component.save(current);
    fixture.detectChanges();
    expect(component.pendingRemoval()).toBe(current);
    expect(fixture.nativeElement.textContent).toContain('Retirer la restriction de date de T1 ?');
    expect(api.updateTermManagementWindow).not.toHaveBeenCalled();

    component.cancelRemoval();
    expect(api.updateTermManagementWindow).not.toHaveBeenCalled();
    component.set(current, 'limited', false);
    component.save(current);
    component.confirmRemoval();
    expect(api.updateTermManagementWindow).toHaveBeenCalledWith('session', 'term-1', expect.objectContaining({ limited: false, opensAt: null, closesAt: null, version: 4 }));
  });
});
