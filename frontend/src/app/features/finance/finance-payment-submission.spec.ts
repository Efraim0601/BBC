import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { FinanceComponent } from './finance';

function paymentForm() {
  // Exercise submission behavior without loading unrelated dashboard requests.
  const component = Object.create(FinanceComponent.prototype) as any;
  const response = new Subject<any>();
  Object.assign(component, {
    draft:{studentId:'student',treasuryAccountId:'cash',amount:1000,method:'CASH',paidOn:'2026-09-07'},
    paymentSaving:signal(false),payError:signal(null),statement:signal({balance:10000}),
    paymentOpen:signal(true),selectedChannel:()=>({requiresReference:false}),fr:()=>false,
    api:{recordPayment:vi.fn().mockReturnValue(response)},
  });
  return {component,response};
}

describe('payment submission safety', () => {
  it('does not use the legacy collection form for a charge-backed account', () => {
    const {component} = paymentForm();
    component.statement.set({balance:10000,legacyCollectionAllowed:false});
    component.save();
    expect(component.api.recordPayment).not.toHaveBeenCalled();
  });

  it('ignores a delayed balance for a previously selected student', () => {
    const {component} = paymentForm();
    const old = new Subject<any>(), current = new Subject<any>();
    component.api.statement = vi.fn().mockReturnValueOnce(old).mockReturnValueOnce(current);
    component.onPayStudent('first'); component.onPayStudent('second');
    current.next({studentId:'second',balance:2000,tranches:[]});
    old.next({studentId:'first',balance:100000,tranches:[]});
    expect(component.statement().studentId).toBe('second');
    expect(component.statement().balance).toBe(2000);
  });
  it('blocks a second click and closing the dialog while collection is pending', () => {
    const {component}=paymentForm();
    component.save(); component.save(); component.closePayment();
    expect(component.api.recordPayment).toHaveBeenCalledTimes(1);
    expect(component.paymentSaving()).toBe(true);
    expect(component.paymentOpen()).toBe(true);
    expect(component.canSubmitPayment()).toBe(false);
  });

  it('reuses the request key after a network failure without changing the payload', () => {
    const {component,response}=paymentForm();
    component.save();
    const firstKey=component.api.recordPayment.mock.calls[0][1];
    response.error({status:0});
    expect(component.paymentSaving()).toBe(false);
    component.api.recordPayment.mockReturnValue(new Subject());
    component.save();
    expect(component.api.recordPayment.mock.calls[1][1]).toBe(firstKey);
  });

  it('captures the submitted payload separately from the editable form', () => {
    const {component}=paymentForm();
    component.save(); component.draft.amount=9999;
    expect(component.api.recordPayment.mock.calls[0][0].amount).toBe(1000);
  });
});
