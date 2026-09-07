import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { FinanceCollectionsComponent } from './finance-collections';

function setup() {
  const c=Object.create(FinanceCollectionsComponent.prototype) as any;
  const response=new Subject<any>();
  Object.assign(c, {
    busy:signal(false),payment:signal(null),selected:signal({enrollmentId:'enrollment'}),
    quote:signal({installments:[]}),selectedChannel:()=>({id:'cash'}),canReview:()=>true,
    amountModel:1000,paymentDateModel:'2026-09-07',selectedTreasuryAccountId:'treasury',
    referenceModel:'',payerModel:'Parent',noteModel:'',selectedPayment:signal(null),
    error:signal(null),correlationId:signal(null),success:signal(null),fr:()=>false,
    api:{post:vi.fn().mockReturnValue(response)},loadPayments:vi.fn(),loadCashier:vi.fn(),
  });
  return {c,response};
}
describe('scheduled collection submission',()=>{
  it('blocks double clicks while posting and after success',()=>{
    const {c,response}=setup();c.postCollection();c.postCollection();
    expect(c.api.post).toHaveBeenCalledTimes(1);
    response.next({id:'payment'});c.postCollection();
    expect(c.api.post).toHaveBeenCalledTimes(1);
  });
  it('retains the idempotency key when retrying an unchanged uncertain payment',()=>{
    const {c,response}=setup();c.postCollection();const key=c.api.post.mock.calls[0][1];
    response.error({status:0});c.api.post.mockReturnValue(new Subject());c.postCollection();
    expect(c.api.post.mock.calls[1][1]).toBe(key);
  });
  it('assigns a different key after explicitly changing the payment',()=>{
    const {c,response}=setup();c.postCollection();const key=c.api.post.mock.calls[0][1];
    response.error({status:0});c.amountModel=2000;c.api.post.mockReturnValue(new Subject());c.postCollection();
    expect(c.api.post.mock.calls[1][1]).not.toBe(key);
  });
});
