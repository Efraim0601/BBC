import { describe, expect, it } from 'vitest';
import { StaffComponent } from './staff';
describe('individual staff permissions',()=>{
  it('requires the server record grant even when HR management is allowed',()=>{
    const component=Object.create(StaffComponent.prototype) as any;
    component.auth={canAction:()=>true};
    expect(component.canManageEmployee({canManage:false})).toBe(false);
    expect(component.canManageEmployee({})).toBe(false);
    expect(component.canManageEmployee({canManage:true})).toBe(true);
    component.auth={canAction:()=>false};
    expect(component.canManageEmployee({canManage:true})).toBe(false);
  });
});
