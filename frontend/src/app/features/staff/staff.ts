import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StaffApi, EmployeeUpsert, EmployeeView } from './staff.api';
import { HrApi, DepartmentView, DepartmentUpsert, LeaveView, LeaveCreate } from './hr.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, TabsComponent, ChipFilterComponent,
  DataTableComponent, CellTemplateDirective, Column,
} from '../../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;
const fmtShort = (n: number) => (n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'k' : '' + n);

@Component({
  selector: 'bbc-staff',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, KpiComponent, PageHeaderComponent,
    EmptyComponent, AvatarComponent, TabsComponent, ChipFilterComponent,
    DataTableComponent, CellTemplateDirective,
  ],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="i18n.t('hr')"
        [subtitle]="fr() ? 'Annuaire du personnel, rôles et masse salariale' : 'Staff directory, roles and payroll'">
        <div right class="flex items-center gap-2">
          @if (!editing()) {
            <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
            </button>
            @if (canWrite) {
              <button (click)="openCreate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvel employé' : 'New employee' }}
              </button>
            }
          }
        </div>
      </bbc-page-header>

      @if (!editing()) {
        <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="setTab($event)" />

        <!-- KPIs -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
          <bbc-kpi tone="neutral" icon="users" [label]="fr() ? 'Effectif personnel' : 'Total staff'"
            [value]="rows().length" [sub]="teacherCount() + (fr() ? ' enseignants' : ' teachers')" />
          <bbc-kpi tone="ok" icon="check" [label]="fr() ? 'Permanents' : 'Permanent'"
            [value]="permCount()" [sub]="permPct() + '% ' + (fr() ? 'du staff' : 'of staff')" />
          <bbc-kpi tone="warn" icon="clock" [label]="fr() ? 'Vacataires' : 'Contractors'"
            [value]="vacCount()" [sub]="fr() ? 'payés à l’heure' : 'paid hourly'" />
          <bbc-kpi tone="gold" icon="wallet" [label]="fr() ? 'Masse salariale' : 'Monthly payroll'"
            [value]="money(monthlyPayroll())" [sub]="fr() ? 'salaires + vacations' : 'salaries + contracts'" />
        </div>

        @switch (tab()) {
          @case ('directory') {
            <!-- Filters -->
            <bbc-card className="mb-5">
              <div class="flex items-center gap-3 flex-wrap">
                <div class="relative">
                  <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="16" /></span>
                  <input [ngModel]="search()" (ngModelChange)="search.set($event)"
                    [placeholder]="fr() ? 'Rechercher (nom, code)…' : 'Search (name, code)…'"
                    class="h-9 w-72 pl-9 pr-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
                </div>
                <bbc-chip-filter [allLabel]="fr() ? 'Tous' : 'All'" [value]="roleFilter()"
                  [options]="roleOptions()" (change)="roleFilter.set($event)" />
              </div>
            </bbc-card>

            <!-- High-density directory table -->
            <bbc-card className="mb-5 overflow-hidden">
              <div class="-m-5">
                <div class="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                  <div class="text-sm font-semibold">{{ filtered().length }} {{ fr() ? 'employés' : 'employees' }}</div>
                  <div class="text-xs text-mute">{{ fr() ? 'Cliquez une ligne pour la fiche' : 'Click a row for the profile' }}</div>
                </div>
                <bbc-data-table [columns]="columns()" [rows]="filtered()"
                  [trackBy]="trackId" [activeId]="selectedId()"
                  [emptyLabel]="fr() ? 'Aucun résultat' : 'No results'"
                  (rowClick)="select($event)">

                  <ng-template bbcCell="name" let-e>
                    <div class="flex items-center gap-3">
                      <bbc-avatar [name]="e.name" [hue]="hue(e.id)" [size]="34" />
                      <div class="min-w-0">
                        <div class="font-semibold text-ink truncate flex items-center gap-1.5">
                          {{ e.name }}
                          @if (e.roles.includes('principal')) { <span class="text-[9px] font-bold uppercase bg-brand-100 text-brand-700 px-1.5 py-0.5 rounded">P</span> }
                          @if (e.roles.includes('form_teacher')) { <span class="text-[9px] font-bold uppercase bg-violet-100 text-violet-700 px-1.5 py-0.5 rounded">PP</span> }
                        </div>
                        <div class="text-[11px] text-mute font-mono">{{ e.code }}</div>
                      </div>
                    </div>
                  </ng-template>

                  <ng-template bbcCell="role" let-e>{{ roleLabel(e.roles[0]) }}</ng-template>

                  <ng-template bbcCell="formClass" let-e>
                    @if (e.formClass) { <span class="text-xs font-bold px-2 py-0.5 rounded-full bg-gold-100 text-gold-700">{{ e.formClass }}</span> } @else { — }
                  </ng-template>

                  <ng-template bbcCell="type" let-e>
                    <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded"
                      [class]="e.type === 'Permanent' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'">{{ e.type }}</span>
                  </ng-template>

                  <ng-template bbcCell="comp" let-e>
                    @if (e.type === 'Permanent') { <span class="font-mono">{{ money(e.monthlySalary || 0) }}</span> }
                    @else { <span class="font-mono text-mute">{{ money(e.hourlyRate || 0) }}/h</span> }
                  </ng-template>
                </bbc-data-table>
              </div>
            </bbc-card>

            <!-- Detail panel -->
            @if (selected(); as e) {
              <div class="bg-white rounded-xl2 shadow-card border border-slate-100 overflow-hidden">
                <div class="p-6 bg-gradient-to-br from-brand-700 to-brand-800 text-white relative overflow-hidden">
                  <div class="absolute -top-12 -right-8 w-40 h-40 rounded-full bg-gold-400/15 blur-2xl"></div>
                  <div class="flex items-start gap-4 relative">
                    <bbc-avatar [name]="e.name" [hue]="hue(e.id)" [size]="64" />
                    <div class="flex-1 min-w-0">
                      <div class="text-[10px] uppercase tracking-wider text-gold-200 font-semibold font-mono">{{ e.code }}</div>
                      <div class="text-xl font-bold leading-tight font-display">{{ e.name }}</div>
                      <div class="flex items-center gap-2 mt-1 flex-wrap">
                        @for (r of e.roles; track r) {
                          <span class="text-[10px] font-bold uppercase tracking-wide bg-white/15 px-2 py-0.5 rounded">{{ roleLabel(r) }}</span>
                        }
                        <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded"
                          [class]="e.type === 'Permanent' ? 'bg-emerald-400 text-emerald-900' : 'bg-amber-400 text-amber-900'">{{ e.type }}</span>
                      </div>
                    </div>
                    @if (canWrite) {
                      <div class="flex flex-col gap-1.5 shrink-0">
                        <button (click)="openEdit(e)"
                          class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-gold-400 text-brand-900 hover:bg-gold-300">
                          <bbc-icon name="edit" [s]="14" /> {{ fr() ? 'Modifier' : 'Edit' }}
                        </button>
                        <button (click)="remove(e)" class="text-xs text-rose-200 hover:text-white px-2 py-1">
                          {{ fr() ? 'Supprimer' : 'Delete' }}
                        </button>
                      </div>
                    }
                  </div>
                </div>

                <div class="p-6 space-y-5">
                  <!-- Contact -->
                  <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
                    <div class="flex items-center gap-2.5 p-2 rounded-lg bg-slate-50">
                      <div class="w-7 h-7 rounded-md bg-white text-mute flex items-center justify-center shrink-0"><bbc-icon name="mail" [s]="14" /></div>
                      <div class="min-w-0">
                        <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">Email</div>
                        <div class="text-sm font-semibold text-ink truncate">{{ e.email || '—' }}</div>
                      </div>
                    </div>
                    <div class="flex items-center gap-2.5 p-2 rounded-lg bg-slate-50">
                      <div class="w-7 h-7 rounded-md bg-white text-mute flex items-center justify-center shrink-0"><bbc-icon name="phone" [s]="14" /></div>
                      <div class="min-w-0">
                        <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Téléphone' : 'Phone' }}</div>
                        <div class="text-sm font-semibold text-ink truncate">{{ e.phone || '—' }}</div>
                      </div>
                    </div>
                    <div class="flex items-center gap-2.5 p-2 rounded-lg bg-slate-50">
                      <div class="w-7 h-7 rounded-md bg-white text-mute flex items-center justify-center shrink-0"><bbc-icon name="star" [s]="14" /></div>
                      <div class="min-w-0">
                        <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Rôle principal' : 'Primary role' }}</div>
                        <div class="text-sm font-semibold text-ink truncate">{{ roleLabel(e.roles[0]) }}</div>
                      </div>
                    </div>
                    <div class="flex items-center gap-2.5 p-2 rounded-lg bg-slate-50">
                      <div class="w-7 h-7 rounded-md bg-white text-mute flex items-center justify-center shrink-0"><bbc-icon name="users" [s]="14" /></div>
                      <div class="min-w-0">
                        <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Sexe' : 'Sex' }}</div>
                        <div class="text-sm font-semibold text-ink truncate">{{ sexLabel(e.sex) }}</div>
                      </div>
                    </div>
                  </div>

                  <!-- Compensation -->
                  <div>
                    <div class="text-[11px] uppercase tracking-wider text-mute font-semibold mb-2">{{ fr() ? 'Rémunération' : 'Compensation' }}</div>
                    <div class="grid grid-cols-3 gap-2">
                      @if (e.type === 'Permanent') {
                        <div class="rounded-lg px-3 py-2.5 bg-emerald-50 text-emerald-700">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Salaire mensuel' : 'Monthly salary' }}</div>
                          <div class="text-sm font-bold mt-0.5">{{ money(e.monthlySalary || 0) }}</div>
                        </div>
                        <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Annuel' : 'Annual' }}</div>
                          <div class="text-sm font-bold mt-0.5">{{ short((e.monthlySalary || 0) * 12) }} FCFA</div>
                        </div>
                        <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Statut' : 'Status' }}</div>
                          <div class="text-sm font-bold mt-0.5">{{ e.active ? (fr() ? 'Actif' : 'Active') : (fr() ? 'Inactif' : 'Inactive') }}</div>
                        </div>
                      } @else {
                        <div class="rounded-lg px-3 py-2.5 bg-amber-50 text-amber-700">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Taux horaire' : 'Hourly rate' }}</div>
                          <div class="text-sm font-bold mt-0.5">{{ money(e.hourlyRate || 0) }}/h</div>
                        </div>
                        <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Statut' : 'Status' }}</div>
                          <div class="text-sm font-bold mt-0.5">{{ e.active ? (fr() ? 'Actif' : 'Active') : (fr() ? 'Inactif' : 'Inactive') }}</div>
                        </div>
                        <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                          <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">Type</div>
                          <div class="text-sm font-bold mt-0.5">{{ e.type }}</div>
                        </div>
                      }
                    </div>
                  </div>
                </div>
              </div>
            }
          }

          @case ('payroll') {
            <bbc-card [title]="fr() ? 'Masse salariale' : 'Monthly payroll'"
              [subtitle]="money(monthlyPayroll()) + ' · ' + (fr() ? 'mensuel' : 'monthly')">
              @if (payrollRows().length === 0) {
                <bbc-empty icon="wallet" [label]="fr() ? 'Aucun employé' : 'No employee'" />
              } @else {
                <table class="w-full text-sm">
                  <thead class="border-b border-slate-100">
                    <tr class="text-[11px] uppercase text-mute">
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Nom' : 'Name' }}</th>
                      <th class="text-left font-semibold py-2">Type</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Salaire mensuel' : 'Monthly salary' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Taux horaire' : 'Hourly rate' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Total' : 'Total' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (r of payrollRows(); track r.e.id) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/30">
                        <td class="py-2.5">
                          <div class="flex items-center gap-2.5">
                            <bbc-avatar [name]="r.e.name" [hue]="hue(r.e.id)" [size]="28" />
                            <div>
                              <div class="font-semibold text-ink">{{ r.e.name }}</div>
                              <div class="text-[10px] text-mute">{{ roleLabel(r.e.roles[0]) }}</div>
                            </div>
                          </div>
                        </td>
                        <td class="py-2.5">
                          <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded"
                            [class]="r.e.type === 'Permanent' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'">{{ r.e.type }}</span>
                        </td>
                        <td class="py-2.5 text-right font-mono text-mute">{{ r.base ? money(r.base) : '—' }}</td>
                        <td class="py-2.5 text-right font-mono text-mute">{{ r.e.hourlyRate ? money(r.e.hourlyRate) + '/h' : '—' }}</td>
                        <td class="py-2.5 text-right font-bold text-ink">{{ money(r.total) }}</td>
                      </tr>
                    }
                  </tbody>
                  <tfoot class="bg-brand-50 font-bold border-t-2 border-brand-600">
                    <tr>
                      <td class="py-2.5 text-brand-700" colspan="4">{{ fr() ? 'Total masse salariale' : 'Total payroll' }}</td>
                      <td class="py-2.5 text-right text-brand-700">{{ money(monthlyPayroll()) }}</td>
                    </tr>
                  </tfoot>
                </table>
              }
            </bbc-card>
          }

          @case ('departments') {
            <bbc-card [title]="fr() ? 'Départements' : 'Departments'"
              [subtitle]="fr() ? 'Organisez le personnel en départements' : 'Organise staff into departments'">
              <div action>
                @if (canWrite) {
                  <button (click)="newDept()" class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouveau département' : 'New department' }}
                  </button>
                }
              </div>

              @if (canWrite && deptForm()) {
                <form (ngSubmit)="saveDept()" class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Name' }} *</span>
                    <input [(ngModel)]="deptDraft.name" name="dname" required [placeholder]="fr() ? 'Sciences' : 'Sciences'"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block md:col-span-2">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Responsable' : 'Head' }}</span>
                    <select [(ngModel)]="deptDraft.headEmployeeId" name="dhead" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                      <option [ngValue]="null">{{ fr() ? '— Aucun —' : '— None —' }}</option>
                      @for (e of rows(); track e.id) { <option [ngValue]="e.id">{{ e.name }}</option> }
                    </select>
                  </label>
                  <div class="md:col-span-3 flex items-center justify-end gap-2">
                    <button type="button" (click)="deptForm.set(false)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                    <button type="submit" [disabled]="!deptDraft.name" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">{{ i18n.t('save') }}</button>
                  </div>
                </form>
              }

              @if (departments().length) {
                <table class="w-full text-sm">
                  <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
                    <th class="py-2 pr-3 font-semibold">{{ fr() ? 'Département' : 'Department' }}</th>
                    <th class="py-2 px-3 font-semibold">{{ fr() ? 'Responsable' : 'Head' }}</th>
                    <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Effectif' : 'Members' }}</th>
                    <th></th>
                  </tr></thead>
                  <tbody>
                    @for (d of departments(); track d.id) {
                      <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                        <td class="py-2 pr-3 font-semibold text-ink">{{ d.name }}</td>
                        <td class="py-2 px-3 text-mute">{{ d.headName || '—' }}</td>
                        <td class="py-2 px-3 text-center">{{ d.memberCount }}</td>
                        <td class="py-2 pl-3 text-right whitespace-nowrap">
                          @if (canWrite) {
                            <button (click)="editDept(d)" class="text-mute hover:text-brand-600 px-1.5"><bbc-icon name="edit" [s]="15" /></button>
                            <button (click)="deleteDept(d)" class="text-mute hover:text-rose-600 px-1.5"><bbc-icon name="trash" [s]="15" /></button>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <bbc-empty icon="building" [label]="fr() ? 'Aucun département.' : 'No departments.'" />
              }
              @if (hrErr(); as e) { <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }
            </bbc-card>
          }

          @case ('leave') {
            <bbc-card [title]="fr() ? 'Gestion des congés' : 'Leave management'"
              [subtitle]="fr() ? 'Demandes de congé et validation' : 'Leave requests and approval'">
              <div action>
                @if (canWrite) {
                  <button (click)="newLeave()" [disabled]="!rows().length" class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle demande' : 'New request' }}
                  </button>
                }
              </div>

              @if (canWrite && leaveForm()) {
                <form (ngSubmit)="saveLeave()" class="grid grid-cols-1 md:grid-cols-4 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
                  <label class="block md:col-span-2">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Employé' : 'Employee' }} *</span>
                    <select [(ngModel)]="leaveDraft.employeeId" name="lemp" required class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                      @for (e of rows(); track e.id) { <option [ngValue]="e.id">{{ e.name }}</option> }
                    </select>
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">Type</span>
                    <select [(ngModel)]="leaveDraft.type" name="ltype" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                      @for (t of leaveTypes(); track t.value) { <option [ngValue]="t.value">{{ t.label }}</option> }
                    </select>
                  </label>
                  <div></div>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Du' : 'From' }} *</span>
                    <input type="date" [(ngModel)]="leaveDraft.startDate" name="lstart" required class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Au' : 'To' }} *</span>
                    <input type="date" [(ngModel)]="leaveDraft.endDate" name="lend" required class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200" />
                  </label>
                  <label class="block md:col-span-2">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Motif' : 'Reason' }}</span>
                    <input [(ngModel)]="leaveDraft.reason" name="lreason" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200" />
                  </label>
                  <div class="md:col-span-4 flex items-center justify-end gap-2">
                    <button type="button" (click)="leaveForm.set(false)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                    <button type="submit" [disabled]="!leaveDraft.employeeId || !leaveDraft.startDate || !leaveDraft.endDate" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">{{ i18n.t('save') }}</button>
                  </div>
                </form>
              }

              @if (leaves().length) {
                <table class="w-full text-sm">
                  <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
                    <th class="py-2 pr-3 font-semibold">{{ fr() ? 'Employé' : 'Employee' }}</th>
                    <th class="py-2 px-3 font-semibold">Type</th>
                    <th class="py-2 px-3 font-semibold">{{ fr() ? 'Période' : 'Period' }}</th>
                    <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Jours' : 'Days' }}</th>
                    <th class="py-2 px-3 font-semibold">{{ fr() ? 'Statut' : 'Status' }}</th>
                    <th></th>
                  </tr></thead>
                  <tbody>
                    @for (l of leaves(); track l.id) {
                      <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                        <td class="py-2 pr-3 font-semibold text-ink">{{ l.employeeName || '—' }}</td>
                        <td class="py-2 px-3">{{ leaveTypeLabel(l.type) }}</td>
                        <td class="py-2 px-3 text-mute">{{ l.startDate }} → {{ l.endDate }}</td>
                        <td class="py-2 px-3 text-center">{{ l.days }}</td>
                        <td class="py-2 px-3">
                          <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded" [class]="leaveStatusClass(l.status)">{{ leaveStatusLabel(l.status) }}</span>
                        </td>
                        <td class="py-2 pl-3 text-right whitespace-nowrap">
                          @if (canWrite && l.status === 'pending') {
                            <button (click)="decideLeave(l, 'approved')" class="text-emerald-600 hover:text-emerald-700 px-1.5" title="{{ fr() ? 'Approuver' : 'Approve' }}"><bbc-icon name="check" [s]="16" /></button>
                            <button (click)="decideLeave(l, 'rejected')" class="text-rose-500 hover:text-rose-600 px-1.5" title="{{ fr() ? 'Refuser' : 'Reject' }}"><bbc-icon name="x" [s]="16" /></button>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <bbc-empty icon="calendar" [label]="fr() ? 'Aucune demande de congé.' : 'No leave requests.'" />
              }
              @if (hrErr(); as e) { <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }
            </bbc-card>
          }
        }
      } @else {
        <!-- Full-page employee form (replaces the create/edit modal) -->
        <bbc-card>
          <div class="flex items-center gap-3 pb-4 mb-4 border-b border-slate-100">
            <button type="button" (click)="closeEditor()"
              class="w-9 h-9 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-ink">
              <bbc-icon name="chevronLeft" [s]="18" />
            </button>
            <div class="flex-1">
              <div class="text-[17px] font-bold text-ink font-display">
                {{ editId() ? (fr() ? 'Modifier l’employé' : 'Edit employee') : (fr() ? 'Nouvel employé' : 'New employee') }}
              </div>
              <div class="text-xs text-mute">{{ fr() ? 'Renseignez la fiche complète de l’employé.' : 'Fill in the full employee record.' }}</div>
            </div>
          </div>

          <div class="space-y-8 max-w-3xl">
            <section>
              <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Identité & contact' : 'Identity & contact' }}</div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Name' }} *</span>
                  <input [(ngModel)]="draft.name" placeholder="NOM Prénom"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sexe' : 'Sex' }}</span>
                  <select [(ngModel)]="draft.sex"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="M">{{ fr() ? 'Masculin' : 'Male' }}</option>
                    <option value="F">{{ fr() ? 'Féminin' : 'Female' }}</option>
                  </select>
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">Email</span>
                  <input type="email" [(ngModel)]="draft.email"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                  <input [(ngModel)]="draft.phone" placeholder="+237 6XX XX XX XX"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
              </div>
            </section>

            <section>
              <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Rôles (plusieurs possibles)' : 'Roles (multiple)' }}</div>
              <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                @for (r of roleCatalog(); track r.value) {
                  <button type="button" (click)="toggleRole(r.value)"
                    class="px-3 py-2 text-xs font-semibold rounded-lg border transition flex items-center justify-center gap-1.5"
                    [class]="draftRoles().includes(r.value) ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                    @if (draftRoles().includes(r.value)) { <bbc-icon name="check" [s]="12" /> }
                    {{ r.label }}
                  </button>
                }
              </div>
              @if (draftRoles().includes('form_teacher')) {
                <label class="block mt-3 max-w-xs">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Classe (Prof. Principal)' : 'Form class' }}</span>
                  <input [(ngModel)]="draft.formClass" placeholder="6ème A"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
              }
            </section>

            <section>
              <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Département' : 'Department' }}</div>
              <label class="block max-w-xs">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Rattachement' : 'Assignment' }}</span>
                <select [(ngModel)]="draft.departmentId" name="departmentId"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                  <option [ngValue]="null">{{ fr() ? '— Aucun —' : '— None —' }}</option>
                  @for (d of departments(); track d.id) { <option [ngValue]="d.id">{{ d.name }}</option> }
                </select>
                @if (!departments().length) {
                  <span class="text-[11px] text-mute mt-1 block">{{ fr() ? 'Créez des départements dans l’onglet Départements.' : 'Create departments in the Departments tab.' }}</span>
                }
              </label>
            </section>

            <section>
              <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Contrat & rémunération' : 'Contract & compensation' }}</div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-2 mb-3">
                @for (ty of ['Permanent', 'Vacataire']; track ty) {
                  <button type="button" (click)="draft.type = ty"
                    class="p-3 rounded-lg border transition text-left"
                    [class]="draft.type === ty ? 'border-brand-500 bg-brand-50' : 'border-slate-200 hover:border-brand-300'">
                    <div class="text-sm font-bold text-ink">{{ ty === 'Permanent' ? (fr() ? 'Permanent' : 'Permanent') : (fr() ? 'Vacataire' : 'Contractor') }}</div>
                    <div class="text-[11px] text-mute mt-0.5">
                      {{ ty === 'Permanent' ? (fr() ? 'Salaire mensuel fixe' : 'Fixed monthly salary') : (fr() ? 'Payé à l’heure' : 'Paid hourly') }}
                    </div>
                  </button>
                }
              </div>
              <div class="max-w-xs">
                @if (draft.type === 'Permanent') {
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Salaire mensuel' : 'Monthly salary' }}</span>
                    <div class="relative mt-1">
                      <input type="number" [(ngModel)]="draft.monthlySalary"
                        class="w-full h-10 px-3 pr-16 rounded-lg border border-slate-200 font-mono focus:outline-none focus:border-brand-400" />
                      <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-mute font-semibold">FCFA</span>
                    </div>
                  </label>
                } @else {
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Taux horaire' : 'Hourly rate' }}</span>
                    <div class="relative mt-1">
                      <input type="number" [(ngModel)]="draft.hourlyRate"
                        class="w-full h-10 px-3 pr-16 rounded-lg border border-slate-200 font-mono focus:outline-none focus:border-brand-400" />
                      <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-mute font-semibold">FCFA/h</span>
                    </div>
                  </label>
                }
              </div>
            </section>
          </div>

          <div class="flex items-center justify-end gap-2 mt-8 pt-5 border-t border-slate-100">
            <button (click)="closeEditor()" class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
            <button (click)="save()" [disabled]="!draft.name?.trim()"
              class="inline-flex items-center gap-1.5 h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
              <bbc-icon name="check" [s]="16" /> {{ i18n.t('save') }}
            </button>
          </div>
        </bbc-card>
      }
    </div>
  `,
})
export class StaffComponent {
  protected i18n = inject(I18nService);
  private api = inject(StaffApi);
  private hrApi = inject(HrApi);
  private auth = inject(AuthService);

  protected rows = signal<EmployeeView[]>([]);
  protected tab = signal<'directory' | 'payroll' | 'departments' | 'leave'>('directory');

  // HR — departments & leave
  protected departments = signal<DepartmentView[]>([]);
  protected leaves = signal<LeaveView[]>([]);
  protected hrErr = signal<string | null>(null);
  protected deptForm = signal(false);
  protected deptEditId = signal<string | null>(null);
  protected deptDraft: DepartmentUpsert = { name: '', headEmployeeId: null };
  protected leaveForm = signal(false);
  protected leaveDraft: LeaveCreate = { employeeId: '', type: 'annual', startDate: '', endDate: '', reason: '' };
  protected search = signal('');
  protected roleFilter = signal<string | null>(null);
  protected selectedId = signal<string | null>(null);
  protected editing = signal(false);
  protected editId = signal<string | null>(null);
  protected canWrite = this.auth.can('hr', 'write');
  protected draft: EmployeeUpsert = this.blank();
  protected draftRoles = signal<string[]>([]);
  protected trackId = (e: EmployeeView) => e.id;

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;
  protected short = fmtShort;

  protected tabs = computed(() => [
    { id: 'directory', label: this.fr() ? 'Annuaire' : 'Directory' },
    { id: 'departments', label: this.fr() ? 'Départements' : 'Departments' },
    { id: 'leave', label: this.fr() ? 'Congés' : 'Leave' },
    { id: 'payroll', label: this.fr() ? 'Masse salariale' : 'Payroll' },
  ]);

  protected leaveTypes = computed(() => [
    { value: 'annual', label: this.fr() ? 'Congé annuel' : 'Annual leave' },
    { value: 'sick', label: this.fr() ? 'Maladie' : 'Sick leave' },
    { value: 'maternity', label: this.fr() ? 'Maternité' : 'Maternity' },
    { value: 'unpaid', label: this.fr() ? 'Sans solde' : 'Unpaid' },
    { value: 'other', label: this.fr() ? 'Autre' : 'Other' },
  ]);

  protected roleCatalog = computed(() => [
    { value: 'principal', label: this.fr() ? 'Proviseur' : 'Principal' },
    { value: 'form_teacher', label: this.fr() ? 'Prof. Principal' : 'Form teacher' },
    { value: 'teacher', label: this.fr() ? 'Enseignant' : 'Teacher' },
    { value: 'surveillant', label: this.fr() ? 'Surveillant' : 'Supervisor' },
    { value: 'cashier', label: this.fr() ? 'Caissier' : 'Cashier' },
  ]);

  protected roleOptions = computed(() => this.roleCatalog());

  protected columns = computed<Column<EmployeeView>[]>(() => [
    { key: 'name', label: this.fr() ? 'Employé' : 'Employee', value: (e) => e.name },
    { key: 'role', label: this.fr() ? 'Rôle' : 'Role', value: (e) => this.roleLabel(e.roles[0]) },
    { key: 'formClass', label: this.fr() ? 'Classe' : 'Form class', value: (e) => e.formClass || '' },
    { key: 'type', label: 'Type', value: (e) => e.type },
    { key: 'comp', label: this.fr() ? 'Rémunération' : 'Compensation', align: 'right', value: (e) => (e.type === 'Permanent' ? e.monthlySalary || 0 : e.hourlyRate || 0) },
  ]);

  protected filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const role = this.roleFilter();
    return this.rows().filter((e) => {
      if (role && !e.roles.includes(role)) return false;
      if (q && !e.name.toLowerCase().includes(q) && !(e.code || '').toLowerCase().includes(q)) return false;
      return true;
    });
  });

  protected selected = computed(() => {
    const id = this.selectedId();
    return this.rows().find((e) => e.id === id) ?? this.filtered()[0] ?? null;
  });

  protected teacherCount = computed(() => this.rows().filter((e) => e.roles.includes('teacher') || e.roles.includes('form_teacher')).length);
  protected permCount = computed(() => this.rows().filter((e) => e.type === 'Permanent').length);
  protected vacCount = computed(() => this.rows().filter((e) => e.type === 'Vacataire').length);
  protected permPct = computed(() => (this.rows().length ? Math.round((this.permCount() / this.rows().length) * 100) : 0));
  protected monthlyPayroll = computed(() => this.rows().reduce((a, e) => a + (e.monthlySalary || 0) + (e.hourlyRate || 0), 0));

  protected payrollRows = computed(() =>
    this.rows()
      .map((e) => ({ e, base: e.monthlySalary || 0, total: (e.monthlySalary || 0) + (e.hourlyRate || 0) }))
      .sort((a, b) => b.total - a.total),
  );

  constructor() {
    this.reload();
    this.loadDepartments();
    this.loadLeaves();
  }

  private reload(): void {
    this.api.list().subscribe((r) => {
      this.rows.set(r);
      if (!this.selectedId() && r.length) this.selectedId.set(r[0].id);
    });
  }

  private loadDepartments(): void { this.hrApi.listDepartments().subscribe((d) => this.departments.set(d)); }
  private loadLeaves(): void { this.hrApi.listLeaves().subscribe((l) => this.leaves.set(l)); }

  protected setTab(id: string): void {
    this.tab.set(id === 'payroll' || id === 'departments' || id === 'leave' ? (id as any) : 'directory');
    this.deptForm.set(false);
    this.leaveForm.set(false);
    this.hrErr.set(null);
  }

  // ---- Departments ----
  private hrFail = (e: any) => this.hrErr.set(e?.error?.message ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'));
  protected newDept(): void { this.deptEditId.set(null); this.deptDraft = { name: '', headEmployeeId: null }; this.hrErr.set(null); this.deptForm.set(true); }
  protected editDept(d: DepartmentView): void { this.deptEditId.set(d.id); this.deptDraft = { name: d.name, headEmployeeId: d.headEmployeeId }; this.hrErr.set(null); this.deptForm.set(true); }
  protected saveDept(): void {
    this.hrErr.set(null);
    const id = this.deptEditId();
    const req = id ? this.hrApi.updateDepartment(id, this.deptDraft) : this.hrApi.createDepartment(this.deptDraft);
    req.subscribe({ next: () => { this.deptForm.set(false); this.loadDepartments(); }, error: this.hrFail });
  }
  protected deleteDept(d: DepartmentView): void {
    if (!confirm(this.fr() ? `Supprimer le département « ${d.name} » ?` : `Delete department "${d.name}"?`)) return;
    this.hrErr.set(null);
    this.hrApi.deleteDepartment(d.id).subscribe({ next: () => this.loadDepartments(), error: this.hrFail });
  }

  // ---- Leave ----
  protected newLeave(): void { this.leaveDraft = { employeeId: this.rows()[0]?.id ?? '', type: 'annual', startDate: '', endDate: '', reason: '' }; this.hrErr.set(null); this.leaveForm.set(true); }
  protected saveLeave(): void {
    this.hrErr.set(null);
    this.hrApi.createLeave(this.leaveDraft).subscribe({ next: () => { this.leaveForm.set(false); this.loadLeaves(); }, error: this.hrFail });
  }
  protected decideLeave(l: LeaveView, status: 'approved' | 'rejected'): void {
    this.hrApi.decideLeave(l.id, status).subscribe({ next: () => this.loadLeaves(), error: this.hrFail });
  }
  protected leaveTypeLabel(t: string): string { return this.leaveTypes().find((x) => x.value === t)?.label ?? t; }
  protected leaveStatusLabel(s: string): string {
    const f = this.fr();
    return s === 'approved' ? (f ? 'Approuvé' : 'Approved') : s === 'rejected' ? (f ? 'Refusé' : 'Rejected') : (f ? 'En attente' : 'Pending');
  }
  protected leaveStatusClass(s: string): string {
    return s === 'approved' ? 'bg-emerald-100 text-emerald-700' : s === 'rejected' ? 'bg-rose-100 text-rose-600' : 'bg-amber-100 text-amber-700';
  }

  protected select(e: EmployeeView): void {
    this.selectedId.set(e.id);
  }

  protected hue(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) % 360;
    return h;
  }

  protected roleLabel(role: string | undefined): string {
    if (!role) return '—';
    return this.roleCatalog().find((r) => r.value === role)?.label ?? role;
  }

  protected sexLabel(sex: string): string {
    if (sex === 'M') return this.fr() ? 'Masculin' : 'Male';
    if (sex === 'F') return this.fr() ? 'Féminin' : 'Female';
    return sex || '—';
  }

  protected openCreate(): void {
    this.draft = this.blank();
    this.draftRoles.set(['teacher']);
    this.editId.set(null);
    this.editing.set(true);
  }

  protected openEdit(e: EmployeeView): void {
    this.draft = {
      name: e.name,
      sex: e.sex,
      type: e.type,
      email: e.email,
      phone: e.phone,
      formClass: e.formClass,
      departmentId: e.departmentId,
      monthlySalary: e.monthlySalary,
      hourlyRate: e.hourlyRate,
      roles: [...e.roles],
    };
    this.draftRoles.set([...e.roles]);
    this.editId.set(e.id);
    this.editing.set(true);
  }

  protected toggleRole(role: string): void {
    this.draftRoles.update((rs) => (rs.includes(role) ? rs.filter((r) => r !== role) : [...rs, role]));
  }

  protected closeEditor(): void {
    this.editing.set(false);
  }

  save(): void {
    if (!this.draft.name?.trim()) return;
    const body: EmployeeUpsert = { ...this.draft, roles: this.draftRoles() };
    const id = this.editId();
    const req = id ? this.api.update(id, body) : this.api.create(body);
    req.subscribe((res) => {
      this.editing.set(false);
      this.selectedId.set(res?.id ?? id);
      this.reload();
    });
  }

  remove(e: EmployeeView): void {
    this.api.remove(e.id).subscribe(() => {
      if (this.selectedId() === e.id) this.selectedId.set(null);
      this.reload();
    });
  }

  private blank(): EmployeeUpsert {
    return { name: '', sex: 'M', type: 'Permanent', email: '', phone: '', formClass: '', departmentId: null, monthlySalary: 350000, hourlyRate: 0, roles: [] };
  }
}
