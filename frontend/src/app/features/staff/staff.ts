import { Component, ChangeDetectionStrategy, inject, signal, computed, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  StaffApi, EmployeeUpsert, EmployeeView, AccountResult, StaffImportRow, StaffImportResult,
  StaffApplicationView, StaffPortalSettingsView, StaffApplicationFinalize,
} from './staff.api';
import { HrApi, DepartmentView, DepartmentUpsert, LeaveView, LeaveCreate } from './hr.api';
import { SettingsApi, RoleView } from '../settings/settings.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { downloadCsv, stampedName } from '../../core/csv';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, TabsComponent, ChipFilterComponent,
  DataTableComponent, CellTemplateDirective, Column, PhotoCaptureComponent,
} from '../../core/ui';
import { PhotoApi } from '../../core/photo.api';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;
const fmtShort = (n: number) => (n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'k' : '' + n);

@Component({
  selector: 'bbc-staff',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, DatePipe, IconComponent, CardComponent, KpiComponent, PageHeaderComponent,
    EmptyComponent, AvatarComponent, TabsComponent, ChipFilterComponent,
    DataTableComponent, CellTemplateDirective, PhotoCaptureComponent,
  ],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="i18n.t('hr')"
        [subtitle]="fr() ? 'Annuaire du personnel, rôles et masse salariale' : 'Staff directory, roles and payroll'">
        <div right class="flex items-center gap-2">
          @if (mode() === 'list') {
            <button (click)="exportList()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
            </button>
            @if (canWrite) {
              <button (click)="downloadStaffTemplate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                {{ fr() ? 'Modèle CSV' : 'CSV template' }}
              </button>
              <button (click)="openImport()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Importer' : 'Import' }}
              </button>
              <button (click)="openCreate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvel employé' : 'New employee' }}
              </button>
            }
          }
        </div>
      </bbc-page-header>

      @if (mode() === 'list') {
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
                    <bbc-avatar [name]="e.name" [hue]="hue(e.id)" [size]="64" [photoUrl]="selectedPhoto()" />
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

                  <!-- Login account -->
                  <div>
                    <div class="text-[11px] uppercase tracking-wider text-mute font-semibold mb-2">{{ fr() ? 'Compte de connexion' : 'Login account' }}</div>
                    <div class="flex items-center justify-between gap-3 p-3 rounded-lg bg-slate-50">
                      <div class="min-w-0">
                        @if (e.hasLogin) {
                          <div class="text-sm font-semibold text-ink">
                            {{ fr() ? 'Identifiant' : 'Username' }} : <span class="font-mono">{{ e.username }}</span>
                          </div>
                          <div class="text-[11px] text-mute mt-0.5">
                            {{ fr() ? 'Le mot de passe n’est jamais affiché — réinitialisez pour en envoyer un nouveau par e-mail.'
                                    : 'The password is never shown — reset to e-mail a new one.' }}
                          </div>
                        } @else {
                          <div class="text-sm font-semibold text-ink">{{ fr() ? 'Aucun compte de connexion' : 'No login account' }}</div>
                          <div class="text-[11px] text-mute mt-0.5">
                            {{ e.email
                                ? (fr() ? 'Créez le compte pour envoyer les identifiants par e-mail.' : 'Create the account to e-mail the credentials.')
                                : (fr() ? 'Ajoutez d’abord un e-mail à la fiche.' : 'Add an e-mail to the record first.') }}
                          </div>
                        }
                      </div>
                      @if (canWrite) {
                        <button (click)="resetCredentials(e)" [disabled]="resetting() || (!e.hasLogin && !e.email)"
                          class="shrink-0 inline-flex items-center gap-1.5 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50">
                          <bbc-icon name="send" [s]="15" />
                          {{ e.hasLogin ? (fr() ? 'Réinitialiser' : 'Reset') : (fr() ? 'Créer le compte' : 'Create account') }}
                        </button>
                      }
                    </div>
                    @if (accountMsg(); as m) {
                      <div class="mt-2 text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">{{ m.text }}</div>
                    }
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

          @case ('applications') {
            <bbc-card className="mb-5" [title]="fr() ? 'Portail d’inscription' : 'Registration portal'"
              [subtitle]="fr() ? 'Lien temporaire pour que le personnel remplisse sa fiche' : 'Temporary link for staff to fill their profile'">
              <div class="space-y-4">
                <div class="flex flex-wrap items-center gap-3">
                  <label class="inline-flex items-center gap-2 cursor-pointer select-none">
                    <input type="checkbox" [ngModel]="portal()?.enabled" (ngModelChange)="setPortalEnabled($event)"
                      [disabled]="!canWrite || portalBusy()"
                      class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                    <span class="text-sm font-semibold text-ink">
                      {{ portal()?.enabled
                          ? (fr() ? 'Portail activé' : 'Portal enabled')
                          : (fr() ? 'Portail désactivé' : 'Portal disabled') }}
                    </span>
                  </label>
                  @if (canWrite) {
                    <button type="button" (click)="regeneratePortalLink()" [disabled]="portalBusy()"
                      class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50">
                      {{ fr() ? 'Régénérer le lien' : 'Regenerate link' }}
                    </button>
                  }
                </div>
                @if (portal()?.publicPath; as path) {
                  <div class="flex flex-col sm:flex-row gap-2">
                    <input readonly [value]="portalAbsoluteUrl()"
                      class="flex-1 h-10 px-3 text-xs font-mono rounded-lg border border-slate-200 bg-slate-50" />
                    <button type="button" (click)="copyPortalLink()"
                      class="h-10 px-4 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 shrink-0">
                      {{ fr() ? 'Copier le lien' : 'Copy link' }}
                    </button>
                  </div>
                  <p class="text-[11px] text-mute">
                    {{ fr()
                      ? 'Partagez ce lien avec le personnel. Désactivez le portail quand le recrutement est terminé.'
                      : 'Share this link with staff. Disable the portal when onboarding is done.' }}
                  </p>
                }
                @if (portalMsg(); as m) {
                  <div class="text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
                }
              </div>
            </bbc-card>

            <bbc-card [title]="fr() ? 'Candidatures' : 'Applications'"
              [subtitle]="fr() ? 'Validation en 2 étapes : accepter → configurer salaire/rôles → finaliser' : 'Two-step validation: accept → set salary/roles → finalize'">
              <div action class="flex items-center gap-2">
                <bbc-chip-filter [allLabel]="fr() ? 'Tous' : 'All'" [value]="appStatusFilter()"
                  [options]="appStatusOptions()" (change)="onAppStatusFilter($event)" />
              </div>

              @if (applications().length) {
                <div class="space-y-3">
                  @for (a of applications(); track a.id) {
                    <div class="p-4 rounded-lg border border-slate-100 bg-slate-50/40">
                      <div class="flex flex-wrap items-start justify-between gap-3">
                        <div class="min-w-0">
                          <div class="flex items-center gap-2 flex-wrap">
                            <div class="font-semibold text-ink">{{ a.name }}</div>
                            <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded"
                              [class]="appStatusClass(a.status)">{{ appStatusLabel(a.status) }}</span>
                            @if (a.employeeCode) {
                              <span class="text-[11px] font-mono text-mute">{{ a.employeeCode }}</span>
                            }
                          </div>
                          <div class="text-xs text-mute mt-1 flex flex-wrap gap-x-3 gap-y-0.5">
                            @if (a.email) { <span>{{ a.email }}</span> }
                            @if (a.phone) { <span>{{ a.phone }}</span> }
                            <span>{{ a.type }}</span>
                            @if (a.desiredRoles) { <span>{{ fr() ? 'Souhait' : 'Wish' }}: {{ a.desiredRoles }}</span> }
                            @if (a.departmentHint) { <span>{{ a.departmentHint }}</span> }
                          </div>
                          @if (a.notes) { <div class="text-xs text-ink mt-2">{{ a.notes }}</div> }
                          @if (a.rejectReason) { <div class="text-xs text-rose-600 mt-1">{{ a.rejectReason }}</div> }
                          <div class="text-[11px] text-mute mt-1">{{ a.submittedAt | date:'short' }}</div>
                        </div>
                        @if (canWrite) {
                          <div class="flex flex-wrap items-center gap-1.5 shrink-0">
                            @if (a.status === 'pending') {
                              <button type="button" (click)="acceptApp(a)"
                                class="h-8 px-3 text-xs font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                                {{ fr() ? 'Accepter' : 'Accept' }}
                              </button>
                              <button type="button" (click)="rejectApp(a)"
                                class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-rose-200 text-rose-600 hover:bg-rose-50">
                                {{ fr() ? 'Refuser' : 'Reject' }}
                              </button>
                            }
                            @if (a.status === 'accepted') {
                              <button type="button" (click)="openFinalize(a)"
                                class="h-8 px-3 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                                {{ fr() ? 'Finaliser' : 'Finalize' }}
                              </button>
                              <button type="button" (click)="rejectApp(a)"
                                class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-rose-200 text-rose-600 hover:bg-rose-50">
                                {{ fr() ? 'Annuler' : 'Cancel' }}
                              </button>
                            }
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>
              } @else {
                <bbc-empty icon="users" [label]="fr() ? 'Aucune candidature.' : 'No applications.'" />
              }
              @if (appErr(); as e) { <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }
            </bbc-card>

            @if (finalizeApp(); as fa) {
              <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 fade-in" (click)="finalizeApp.set(null)">
                <div class="bg-white rounded-xl shadow-pop w-full max-w-lg p-5 space-y-4" (click)="$event.stopPropagation()">
                  <div class="text-lg font-bold font-display text-ink">{{ fr() ? 'Finaliser' : 'Finalize' }} — {{ fa.name }}</div>
                  <p class="text-xs text-mute">{{ fr() ? 'Configurez le salaire et les rôles, puis activez la fiche.' : 'Set salary and roles, then activate the record.' }}</p>
                  <div class="grid grid-cols-2 gap-3">
                    <label class="block">
                      <span class="text-xs font-semibold">Type</span>
                      <select [(ngModel)]="finalizeDraft.type" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                        <option value="Permanent">Permanent</option>
                        <option value="Vacataire">Vacataire</option>
                      </select>
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold">{{ fr() ? 'Département' : 'Department' }}</span>
                      <select [(ngModel)]="finalizeDraft.departmentId" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                        <option [ngValue]="null">—</option>
                        @for (d of departments(); track d.id) { <option [ngValue]="d.id">{{ d.name }}</option> }
                      </select>
                    </label>
                    @if (finalizeDraft.type === 'Permanent') {
                      <label class="block col-span-2">
                        <span class="text-xs font-semibold">{{ fr() ? 'Salaire mensuel' : 'Monthly salary' }}</span>
                        <input type="number" [(ngModel)]="finalizeDraft.monthlySalary" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 font-mono" />
                      </label>
                    } @else {
                      <label class="block col-span-2">
                        <span class="text-xs font-semibold">{{ fr() ? 'Taux horaire' : 'Hourly rate' }}</span>
                        <input type="number" [(ngModel)]="finalizeDraft.hourlyRate" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 font-mono" />
                      </label>
                    }
                    <label class="block col-span-2">
                      <span class="text-xs font-semibold">{{ fr() ? 'Classe (PP)' : 'Form class' }}</span>
                      <select [(ngModel)]="finalizeDraft.formClass"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                        <option value="">{{ fr() ? '— Aucune —' : '— None —' }}</option>
                        @for (c of setupClasses(); track c.id) {
                          <option [value]="c.name">{{ c.name }}</option>
                        }
                      </select>
                    </label>
                  </div>
                  <div>
                    <div class="text-xs font-semibold mb-2">{{ fr() ? 'Rôles' : 'Roles' }}</div>
                    <div class="flex flex-wrap gap-1.5">
                      @for (r of roleCatalog(); track r.value) {
                        <button type="button" (click)="toggleFinalizeRole(r.value)"
                          class="px-2.5 py-1.5 text-xs font-semibold rounded-lg border"
                          [class]="finalizeRoles().includes(r.value) ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute'">
                          {{ r.label }}
                        </button>
                      }
                    </div>
                  </div>
                  <label class="flex items-start gap-2 cursor-pointer" [class.opacity-50]="!fa.email">
                    <input type="checkbox" [ngModel]="finalizeCreateLogin()" (ngModelChange)="finalizeCreateLogin.set($event)"
                      [disabled]="!fa.email" class="mt-0.5 w-4 h-4 rounded border-slate-300 text-brand-600" />
                    <span class="text-sm">{{ fr() ? 'Créer le compte de connexion (e-mail requis)' : 'Create login account (e-mail required)' }}</span>
                  </label>
                  <div class="flex justify-end gap-2 pt-2">
                    <button type="button" (click)="finalizeApp.set(null)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold">{{ i18n.t('cancel') }}</button>
                    <button type="button" (click)="doFinalize()" [disabled]="finalizing()"
                      class="h-9 px-5 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">
                      {{ finalizing() ? '…' : (fr() ? 'Valider définitivement' : 'Final validation') }}
                    </button>
                  </div>
                </div>
              </div>
            }
          }
        }
      } @else if (mode() === 'import') {
        <bbc-card>
          <div class="flex items-center gap-3 pb-4 mb-4 border-b border-slate-100">
            <button type="button" (click)="closeImport()"
              class="w-9 h-9 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-ink">
              <bbc-icon name="chevronLeft" [s]="18" />
            </button>
            <div class="flex-1">
              <div class="text-[17px] font-bold text-ink font-display">{{ fr() ? 'Importer du personnel' : 'Import staff' }}</div>
              <div class="text-xs text-mute">{{ fr() ? 'Ajoutez plusieurs employés d’un coup via CSV ou Excel.' : 'Add many employees at once via CSV or Excel.' }}</div>
            </div>
          </div>

          @if (importResult(); as res) {
            <div class="max-w-2xl space-y-4">
              <div class="flex items-center gap-3 p-4 rounded-lg" [class]="res.failed ? 'bg-amber-50' : 'bg-emerald-50'">
                <div class="w-10 h-10 rounded-full flex items-center justify-center shrink-0"
                  [class]="res.failed ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'">
                  <bbc-icon name="check" [s]="20" />
                </div>
                <div>
                  <div class="font-semibold text-ink">{{ res.created }} {{ fr() ? 'employé(s) importé(s)' : 'employee(s) imported' }}</div>
                  @if (res.failed) { <div class="text-sm text-amber-700">{{ res.failed }} {{ fr() ? 'ligne(s) ignorée(s)' : 'row(s) skipped' }}</div> }
                </div>
              </div>
              @if (res.errors.length) {
                <div class="rounded-lg border border-slate-200 overflow-hidden">
                  <div class="px-3 py-2 bg-slate-50 text-xs font-semibold text-mute">{{ fr() ? 'Lignes ignorées' : 'Skipped rows' }}</div>
                  <table class="w-full text-sm">
                    <tbody>
                      @for (e of res.errors; track e.row) {
                        <tr class="border-t border-slate-100">
                          <td class="px-3 py-1.5 text-mute font-mono w-14">#{{ e.row }}</td>
                          <td class="px-3 py-1.5 font-medium text-ink">{{ e.name }}</td>
                          <td class="px-3 py-1.5 text-rose-600">{{ e.message }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
              <div class="flex items-center justify-end gap-2 pt-2">
                <button (click)="resetImport()" class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ fr() ? 'Importer d’autres' : 'Import more' }}</button>
                <button (click)="closeImport()" class="h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold">{{ fr() ? 'Terminer' : 'Done' }}</button>
              </div>
            </div>
          } @else {
            <div class="space-y-6 max-w-3xl">
              <section>
                <div class="flex items-center justify-between mb-2">
                  <div class="text-[11px] uppercase tracking-wider text-mute font-bold">{{ fr() ? 'Données' : 'Data' }}</div>
                  <div class="flex items-center gap-2">
                    <button type="button" (click)="downloadStaffTemplate()"
                      class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                      {{ fr() ? 'Modèle CSV' : 'CSV template' }}
                    </button>
                    <label class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 cursor-pointer">
                      <bbc-icon name="download" [s]="14" /> {{ fr() ? 'Fichier Excel / CSV' : 'Excel / CSV file' }}
                      <input type="file" accept=".csv,.xls,.xlsx,.xlsm,text/csv,text/plain,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" (change)="onImportFile($event)" class="hidden" />
                    </label>
                    <button type="button" (click)="loadImportSample()" class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">{{ fr() ? 'Exemple' : 'Sample' }}</button>
                  </div>
                </div>
                <textarea [ngModel]="importText()" (ngModelChange)="onImportText($event)" name="staffImportText" rows="7"
                  [placeholder]="fr() ? 'Collez le tableau ici — nom, sexe, type, email, téléphone, rôles, classe, département, salaire…'
                                      : 'Paste the table here — name, sex, type, email, phone, roles, class, department, salary…'"
                  class="w-full px-3 py-2 rounded-lg border border-slate-200 font-mono text-xs focus:outline-none focus:border-brand-400"></textarea>
                <div class="text-[11px] text-mute mt-1">
                  {{ fr() ? 'Colonnes : nom, sexe (M/F), type (Permanent/Vacataire), email, telephone, roles (teacher|form_teacher…), classe, departement, salaire_mensuel, taux_horaire. Alias acceptés : surveillant→prefect, caissier→econome.'
                          : 'Columns: name, sex (M/F), type (Permanent/Vacataire), email, phone, roles (teacher|form_teacher…), class, department, monthly_salary, hourly_rate. Aliases: surveillant→prefect, cashier→econome.' }}
                </div>
              </section>

              <section>
                <label class="flex items-start gap-2.5 cursor-pointer select-none p-3 rounded-lg border border-slate-200 bg-slate-50/60">
                  <input type="checkbox" [ngModel]="importCreateLogin()" (ngModelChange)="importCreateLogin.set($event)"
                    class="mt-0.5 w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                  <span>
                    <span class="text-sm font-semibold text-ink">{{ fr() ? 'Créer les comptes de connexion' : 'Create login accounts' }}</span>
                    <span class="text-[11px] text-mute block mt-0.5">
                      {{ fr() ? 'Pour chaque ligne avec e-mail : identifiants générés et envoyés (SMTP requis). Désactivé par défaut pour un import massif.'
                              : 'For each row with an e-mail: credentials are generated and sent (SMTP required). Off by default for bulk import.' }}
                    </span>
                  </span>
                </label>
              </section>

              @if (importRows().length) {
                <section>
                  <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-2">
                    {{ fr() ? 'Aperçu' : 'Preview' }} — {{ importValidCount() }} / {{ importRows().length }} {{ fr() ? 'valides' : 'valid' }}
                  </div>
                  <div class="rounded-lg border border-slate-200 overflow-auto max-h-80">
                    <table class="w-full text-sm">
                      <thead class="bg-slate-50 sticky top-0">
                        <tr class="text-[11px] uppercase text-mute text-left">
                          <th class="px-3 py-2 font-semibold w-8"></th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Nom' : 'Name' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Sexe' : 'Sex' }}</th>
                          <th class="px-3 py-2 font-semibold">Type</th>
                          <th class="px-3 py-2 font-semibold">Email</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Rôles' : 'Roles' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Dépt.' : 'Dept' }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (r of importRows(); track $index) {
                          <tr class="border-t border-slate-100" [class.bg-rose-50]="!importRowValid(r)">
                            <td class="px-3 py-1.5">
                              @if (importRowValid(r)) { <span class="text-emerald-600"><bbc-icon name="check" [s]="14" /></span> }
                              @else { <span class="text-rose-500"><bbc-icon name="x" [s]="14" /></span> }
                            </td>
                            <td class="px-3 py-1.5 font-medium text-ink">{{ r.name || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.sex || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.type || '—' }}</td>
                            <td class="px-3 py-1.5 text-xs truncate max-w-[10rem]">{{ r.email || '—' }}</td>
                            <td class="px-3 py-1.5 text-xs">{{ (r.roles || []).join(', ') || 'teacher' }}</td>
                            <td class="px-3 py-1.5 text-xs">{{ r.department || '—' }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </section>
              }

              @if (importError(); as e) { <div class="text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }

              <div class="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button (click)="closeImport()" class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                <button (click)="doImport()" [disabled]="!importValidCount() || importing()"
                  class="inline-flex items-center gap-1.5 h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                  <bbc-icon name="plus" [s]="16" />
                  {{ importing() ? (fr() ? 'Import…' : 'Importing…') : (fr() ? 'Importer ' + importValidCount() + ' employé(s)' : 'Import ' + importValidCount() + ' employee(s)') }}
                </button>
              </div>
            </div>
          }
        </bbc-card>
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
              <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Photo' : 'Photo' }}</div>
              <bbc-photo-capture [(photo)]="photoDraft"
                [label]="fr() ? 'Photo de l’employé' : 'Employee photo'" />
            </section>

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

              @if (!editId()) {
                <div class="mt-4 p-3 rounded-lg border border-slate-200 bg-slate-50/60">
                  <label class="flex items-start gap-2.5 cursor-pointer select-none"
                    [class.opacity-50]="!draft.email?.trim()">
                    <input type="checkbox" [ngModel]="createLogin()" (ngModelChange)="createLogin.set($event)"
                      [disabled]="!draft.email?.trim()"
                      class="mt-0.5 w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                    <span>
                      <span class="text-sm font-semibold text-ink flex items-center gap-1.5">
                        <bbc-icon name="send" [s]="14" />
                        {{ fr() ? 'Créer un compte de connexion' : 'Create a login account' }}
                      </span>
                      <span class="text-[11px] text-mute block mt-0.5">
                        {{ draft.email?.trim()
                            ? (fr() ? 'Les identifiants seront envoyés par e-mail à ' + draft.email + '. Le rôle du compte est le rôle principal ci-dessous.'
                                    : 'Credentials will be e-mailed to ' + draft.email + '. The account role is the primary role below.')
                            : (fr() ? 'Renseignez un e-mail ci-dessus pour activer cette option.'
                                    : 'Enter an e-mail above to enable this option.') }}
                      </span>
                    </span>
                  </label>
                </div>
              }
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
              @if (!roleCatalog().length) {
                <div class="text-[11px] text-mute mt-2">
                  {{ fr() ? 'Aucun rôle chargé — créez des rôles dans Paramètres → Rôles.' : 'No roles loaded — create roles in Settings → Roles.' }}
                </div>
              }
              @if (teachingRole()) {
                <label class="block mt-3 max-w-xs">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Section (cycle)' : 'Section (cycle)' }} *</span>
                  <select [(ngModel)]="draft.section" name="section"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option [ngValue]="null">{{ fr() ? '— À définir —' : '— To be set —' }}</option>
                    <option value="maternelle">{{ fr() ? 'Maternelle' : 'Kindergarten' }}</option>
                    <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                    <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
                  </select>
                  <span class="text-[11px] text-mute mt-1 block">
                    {{ fr()
                      ? 'Un enseignant n’exerce que dans une section : il ne verra que les classes de ce cycle qui lui sont assignées. Laissé vide, le cycle sera fixé par sa première affectation de classe.'
                      : 'A teacher works in one section only: they will only see the classes of that cycle assigned to them. Left empty, the cycle is set by their first class assignment.' }}
                  </span>
                </label>
              }
              @if (draftRoles().includes('form_teacher')) {
                <label class="block mt-3 max-w-xs">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Classe (Prof. Principal)' : 'Form class' }}</span>
                  <select [(ngModel)]="draft.formClass"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="">{{ fr() ? '— Aucune —' : '— None —' }}</option>
                    @for (c of setupClasses(); track c.id) {
                      <option [value]="c.name">{{ c.name }}</option>
                    }
                  </select>
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
  private photoApi = inject(PhotoApi);
  private api = inject(StaffApi);
  private hrApi = inject(HrApi);
  private settingsApi = inject(SettingsApi);
  private setupApi = inject(SetupApi);
  private auth = inject(AuthService);

  protected rows = signal<EmployeeView[]>([]);
  protected roleDefs = signal<RoleView[]>([]);
  protected setupClasses = signal<ClassView[]>([]);
  protected tab = signal<'directory' | 'payroll' | 'departments' | 'leave' | 'applications'>('directory');

  // Applications / portal
  protected applications = signal<StaffApplicationView[]>([]);
  protected appStatusFilter = signal<string | null>('pending');
  protected appErr = signal<string | null>(null);
  protected portal = signal<StaffPortalSettingsView | null>(null);
  protected portalBusy = signal(false);
  protected portalMsg = signal<{ ok: boolean; text: string } | null>(null);
  protected finalizeApp = signal<StaffApplicationView | null>(null);
  protected finalizeDraft: StaffApplicationFinalize = { type: 'Permanent', departmentId: null, monthlySalary: 350000, hourlyRate: 0, formClass: '', createLogin: false };
  protected finalizeRoles = signal<string[]>(['teacher']);
  protected finalizeCreateLogin = signal(false);
  protected finalizing = signal(false);

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
  protected mode = signal<'list' | 'edit' | 'import'>('list');
  protected editId = signal<string | null>(null);
  protected canWrite = this.auth.can('hr', 'write');
  protected draft: EmployeeUpsert = this.blank();
  protected draftRoles = signal<string[]>([]);
  /** Photo saisie au formulaire (data URL), envoyée après l'enregistrement. */
  protected photoDraft = signal<string | null>(null);
  private photoWasSet = false;
  /** Photo de l'employé sélectionné, chargée en blob (l'API exige le jeton). */
  protected selectedPhoto = signal<string | null>(null);
  /** Les rôles cloisonnés par section : eux seuls portent un cycle de rattachement. */
  protected teachingRole = computed(() =>
    this.draftRoles().some((r) => r === 'teacher' || r === 'form_teacher'));
  protected createLogin = signal(true);
  protected accountMsg = signal<{ text: string; ok: boolean } | null>(null);
  protected resetting = signal(false);
  protected trackId = (e: EmployeeView) => e.id;

  // Bulk import
  protected importText = signal('');
  protected importRows = signal<StaffImportRow[]>([]);
  protected importResult = signal<StaffImportResult | null>(null);
  protected importError = signal<string | null>(null);
  protected importing = signal(false);
  protected importCreateLogin = signal(false);

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;
  protected short = fmtShort;

  protected tabs = computed(() => [
    { id: 'directory', label: this.fr() ? 'Annuaire' : 'Directory' },
    { id: 'applications', label: this.fr() ? 'Candidatures' : 'Applications' },
    { id: 'departments', label: this.fr() ? 'Départements' : 'Departments' },
    { id: 'leave', label: this.fr() ? 'Congés' : 'Leave' },
    { id: 'payroll', label: this.fr() ? 'Masse salariale' : 'Payroll' },
  ]);

  protected appStatusOptions = computed(() => [
    { value: 'pending', label: this.fr() ? 'En attente' : 'Pending' },
    { value: 'accepted', label: this.fr() ? 'Acceptées' : 'Accepted' },
    { value: 'finalized', label: this.fr() ? 'Finalisées' : 'Finalized' },
    { value: 'rejected', label: this.fr() ? 'Refusées' : 'Rejected' },
  ]);

  protected leaveTypes = computed(() => [
    { value: 'annual', label: this.fr() ? 'Congé annuel' : 'Annual leave' },
    { value: 'sick', label: this.fr() ? 'Maladie' : 'Sick leave' },
    { value: 'maternity', label: this.fr() ? 'Maternité' : 'Maternity' },
    { value: 'unpaid', label: this.fr() ? 'Sans solde' : 'Unpaid' },
    { value: 'other', label: this.fr() ? 'Autre' : 'Other' },
  ]);

  /** Staff-assignable roles from the DB catalogue (excludes parent portal role). */
  protected roleCatalog = computed(() =>
    this.roleDefs()
      .filter((r) => r.code !== 'parent')
      .map((r) => ({ value: r.code, label: this.fr() ? r.labelFr : (r.labelEn || r.labelFr) })),
  );

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

  /** Charge (et libère) la photo de la fiche ouverte. */
  private readonly photoLoader = effect(() => {
    const emp = this.selected();
    const previous = this.selectedPhoto();
    if (previous?.startsWith('blob:')) URL.revokeObjectURL(previous);
    this.selectedPhoto.set(null);
    if (!emp) return;
    this.photoApi.load('staff', emp.id).subscribe((url) => this.selectedPhoto.set(url));
  }, { allowSignalWrites: true });

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
    this.loadRoles();
    this.loadDepartments();
    this.loadLeaves();
    this.loadPortal();
    this.loadApplications();
    this.setupApi.listClasses().subscribe({ next: (c) => this.setupClasses.set(c), error: () => {} });
  }

  private reload(): void {
    this.api.list().subscribe((r) => {
      this.rows.set(r);
      if (!this.selectedId() && r.length) this.selectedId.set(r[0].id);
    });
  }

  private loadRoles(): void {
    this.settingsApi.listRoles().subscribe({
      next: (roles) => this.roleDefs.set(roles),
      error: () => this.roleDefs.set([]),
    });
  }

  private loadPortal(): void {
    this.api.getPortalSettings().subscribe({
      next: (p) => this.portal.set(p),
      error: () => this.portal.set(null),
    });
  }

  private loadApplications(): void {
    this.appErr.set(null);
    this.api.listApplications(this.appStatusFilter()).subscribe({
      next: (rows) => this.applications.set(rows),
      error: (e) => this.appErr.set(e?.error?.message ?? (this.fr() ? 'Chargement impossible.' : 'Could not load.')),
    });
  }

  private loadDepartments(): void { this.hrApi.listDepartments().subscribe((d) => this.departments.set(d)); }
  private loadLeaves(): void { this.hrApi.listLeaves().subscribe((l) => this.leaves.set(l)); }

  protected setTab(id: string): void {
    const allowed = ['directory', 'payroll', 'departments', 'leave', 'applications'] as const;
    this.tab.set((allowed as readonly string[]).includes(id) ? (id as typeof allowed[number]) : 'directory');
    this.deptForm.set(false);
    this.leaveForm.set(false);
    this.hrErr.set(null);
    if (id === 'applications') {
      this.loadPortal();
      this.loadApplications();
    }
  }

  protected onAppStatusFilter(v: string | null): void {
    this.appStatusFilter.set(v);
    this.loadApplications();
  }

  protected portalAbsoluteUrl(): string {
    const path = this.portal()?.publicPath;
    if (!path) return '';
    return `${window.location.origin}${path}`;
  }

  protected setPortalEnabled(enabled: boolean): void {
    if (!this.canWrite) return;
    this.portalBusy.set(true);
    this.portalMsg.set(null);
    this.api.updatePortalSettings(enabled).subscribe({
      next: (p) => {
        this.portal.set(p);
        this.portalBusy.set(false);
        this.portalMsg.set({
          ok: true,
          text: enabled
            ? (this.fr() ? 'Portail activé.' : 'Portal enabled.')
            : (this.fr() ? 'Portail désactivé.' : 'Portal disabled.'),
        });
      },
      error: (e) => {
        this.portalBusy.set(false);
        this.portalMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec.' : 'Failed.') });
      },
    });
  }

  protected regeneratePortalLink(): void {
    if (!this.canWrite) return;
    if (!confirm(this.fr()
      ? 'Régénérer le lien ? L’ancien lien ne fonctionnera plus.'
      : 'Regenerate the link? The old link will stop working.')) return;
    this.portalBusy.set(true);
    this.api.regeneratePortalToken().subscribe({
      next: (p) => {
        this.portal.set(p);
        this.portalBusy.set(false);
        this.portalMsg.set({ ok: true, text: this.fr() ? 'Nouveau lien généré.' : 'New link generated.' });
      },
      error: (e) => {
        this.portalBusy.set(false);
        this.portalMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec.' : 'Failed.') });
      },
    });
  }

  protected copyPortalLink(): void {
    const url = this.portalAbsoluteUrl();
    if (!url) return;
    navigator.clipboard.writeText(url).then(() => {
      this.portalMsg.set({ ok: true, text: this.fr() ? 'Lien copié.' : 'Link copied.' });
    }).catch(() => {
      this.portalMsg.set({ ok: false, text: this.fr() ? 'Copie impossible.' : 'Could not copy.' });
    });
  }

  protected appStatusLabel(s: string): string {
    return this.appStatusOptions().find((o) => o.value === s)?.label ?? s;
  }

  protected appStatusClass(s: string): string {
    if (s === 'pending') return 'bg-amber-100 text-amber-800';
    if (s === 'accepted') return 'bg-sky-100 text-sky-800';
    if (s === 'finalized') return 'bg-emerald-100 text-emerald-800';
    if (s === 'rejected') return 'bg-rose-100 text-rose-700';
    return 'bg-slate-100 text-slate-600';
  }

  protected acceptApp(a: StaffApplicationView): void {
    if (!confirm(this.fr()
      ? `Accepter « ${a.name} » et créer la fiche (brouillon) ?`
      : `Accept “${a.name}” and create a draft employee record?`)) return;
    this.appErr.set(null);
    this.api.acceptApplication(a.id).subscribe({
      next: () => { this.loadApplications(); this.reload(); },
      error: (e) => this.appErr.set(e?.error?.message ?? (this.fr() ? 'Acceptation impossible.' : 'Accept failed.')),
    });
  }

  protected rejectApp(a: StaffApplicationView): void {
    const reason = prompt(this.fr() ? 'Motif du refus :' : 'Rejection reason:');
    if (reason == null) return;
    if (!reason.trim()) {
      alert(this.fr() ? 'Motif obligatoire.' : 'Reason required.');
      return;
    }
    this.appErr.set(null);
    this.api.rejectApplication(a.id, reason.trim()).subscribe({
      next: () => this.loadApplications(),
      error: (e) => this.appErr.set(e?.error?.message ?? (this.fr() ? 'Refus impossible.' : 'Reject failed.')),
    });
  }

  protected openFinalize(a: StaffApplicationView): void {
    this.finalizeApp.set(a);
    this.finalizeDraft = {
      type: a.type || 'Permanent',
      departmentId: null,
      monthlySalary: 350000,
      hourlyRate: 0,
      formClass: a.formClass || '',
      createLogin: false,
    };
    this.finalizeRoles.set(['teacher']);
    this.finalizeCreateLogin.set(!!a.email);
  }

  protected toggleFinalizeRole(role: string): void {
    this.finalizeRoles.update((rs) => (rs.includes(role) ? rs.filter((r) => r !== role) : [...rs, role]));
  }

  protected doFinalize(): void {
    const a = this.finalizeApp();
    if (!a) return;
    this.finalizing.set(true);
    this.appErr.set(null);
    const body: StaffApplicationFinalize = {
      ...this.finalizeDraft,
      roles: this.finalizeRoles(),
      createLogin: !!a.email && this.finalizeCreateLogin(),
    };
    this.api.finalizeApplication(a.id, body).subscribe({
      next: () => {
        this.finalizing.set(false);
        this.finalizeApp.set(null);
        this.loadApplications();
        this.reload();
      },
      error: (e) => {
        this.finalizing.set(false);
        this.appErr.set(e?.error?.message ?? (this.fr() ? 'Finalisation impossible.' : 'Finalize failed.'));
      },
    });
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
    this.accountMsg.set(null);
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
    this.createLogin.set(true);
    this.editId.set(null);
    this.photoDraft.set(null);
    this.photoWasSet = false;
    this.mode.set('edit');
  }

  /**
   * Envoie la photo après l'enregistrement de la fiche (une création n'a pas
   * encore d'identifiant). Une URL blob: est la photo déjà stockée, rechargée à
   * l'ouverture : il n'y a rien à renvoyer.
   */
  private savePhoto(employeeId: string): void {
    const photo = this.photoDraft();
    if (photo && photo.startsWith('data:')) {
      this.photoApi.save('staff', employeeId, photo).subscribe({ error: () => {} });
    } else if (!photo && this.photoWasSet) {
      this.photoApi.remove('staff', employeeId).subscribe({ error: () => {} });
    }
  }

  protected resetCredentials(e: EmployeeView): void {
    const action = e.hasLogin
      ? (this.fr() ? `réinitialiser les identifiants de « ${e.name} » ?` : `reset credentials for "${e.name}"?`)
      : (this.fr() ? `créer le compte de connexion de « ${e.name} » ?` : `create a login account for "${e.name}"?`);
    if (!confirm((this.fr() ? 'Voulez-vous ' : 'Do you want to ') + action)) return;
    this.resetting.set(true);
    this.accountMsg.set(null);
    this.api.resetCredentials(e.id).subscribe({
      next: (r: AccountResult) => {
        this.resetting.set(false);
        this.accountMsg.set({ text: r.message, ok: r.emailSent });
        this.reload();
      },
      error: (err) => {
        this.resetting.set(false);
        this.accountMsg.set({ text: err?.error?.message ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'), ok: false });
      },
    });
  }

  protected openEdit(e: EmployeeView): void {
    this.photoDraft.set(null);
    this.photoWasSet = false;
    this.photoApi.load('staff', e.id).subscribe((url) => {
      if (url) { this.photoDraft.set(url); this.photoWasSet = true; }
    });
    this.draft = {
      name: e.name,
      sex: e.sex,
      type: e.type,
      email: e.email,
      phone: e.phone,
      formClass: e.formClass,
      section: e.section,
      departmentId: e.departmentId,
      monthlySalary: e.monthlySalary,
      hourlyRate: e.hourlyRate,
      roles: [...e.roles],
    };
    this.draftRoles.set([...e.roles]);
    this.editId.set(e.id);
    this.mode.set('edit');
  }

  protected toggleRole(role: string): void {
    this.draftRoles.update((rs) => (rs.includes(role) ? rs.filter((r) => r !== role) : [...rs, role]));
  }

  protected closeEditor(): void {
    this.mode.set('list');
    this.photoDraft.set(null);
    this.photoWasSet = false;
  }

  save(): void {
    if (!this.draft.name?.trim()) return;
    const email = this.draft.email?.trim() || '';
    const phone = this.draft.phone?.trim() || '';
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      alert(this.fr() ? 'Adresse e-mail invalide.' : 'Invalid e-mail address.');
      return;
    }
    if (phone && !/^[+0-9][0-9\s().-]{5,24}$/.test(phone)) {
      alert(this.fr() ? 'Numéro de téléphone invalide.' : 'Invalid phone number.');
      return;
    }
    const isNew = !this.editId();
    const wantsLogin = isNew && !!email && this.createLogin();
    const body: EmployeeUpsert = {
      ...this.draft,
      email: email || null as any,
      phone: phone || null as any,
      roles: this.draftRoles(),
      createLogin: wantsLogin,
    };
    const id = this.editId();
    const req = id ? this.api.update(id, body) : this.api.create(body);
    req.subscribe({
      next: (res) => {
        this.mode.set('list');
        this.selectedId.set(res?.id ?? id);
        this.accountMsg.set(null);
        if (res?.id) this.savePhoto(res.id);
        if (wantsLogin && res?.id) {
          this.api.resetCredentials(res.id).subscribe({
            next: (r: AccountResult) => { this.accountMsg.set({ text: r.message, ok: r.emailSent }); this.reload(); },
            error: (err) => { this.accountMsg.set({ text: err?.error?.message ?? (this.fr() ? 'Compte non créé.' : 'Account not created.'), ok: false }); this.reload(); },
          });
        } else {
          this.reload();
        }
      },
      error: (err) => {
        const msg = err?.error?.message;
        const text = msg && typeof msg === 'object'
          ? Object.values(msg).join(' · ')
          : (msg ?? (this.fr() ? 'Enregistrement impossible.' : 'Save failed.'));
        alert(text);
      },
    });
  }

  remove(e: EmployeeView): void {
    this.api.remove(e.id).subscribe(() => {
      if (this.selectedId() === e.id) this.selectedId.set(null);
      this.reload();
    });
  }

  protected exportList(): void {
    const rows = this.filtered().map((e) => [
      e.code, e.name, e.sex, e.type, e.email, e.phone,
      (e.roles || []).join('|'), e.formClass, e.section ?? '', e.departmentName,
      e.monthlySalary, e.hourlyRate,
    ]);
    downloadCsv(stampedName('personnel'),
      ['code', 'nom', 'sexe', 'type', 'email', 'telephone', 'roles', 'classe', 'section', 'departement', 'salaire_mensuel', 'taux_horaire'],
      rows);
  }

  protected downloadStaffTemplate(): void {
    downloadCsv('modele-personnel.csv',
      ['nom', 'sexe', 'type', 'email', 'telephone', 'roles', 'classe', 'section', 'departement', 'salaire_mensuel', 'taux_horaire'],
      [[
        'NGONO Jean Paul', 'M', 'Permanent', 'j.ngono@bbc.cm', '+237 6XX XX XX XX',
        'teacher|form_teacher', '6ème A', 'Secondaire', 'Sciences', '350000', '',
      ], [
        'MBAH Alice', 'F', 'Vacataire', 'a.mbah@bbc.cm', '+237 6YY YY YY YY',
        'teacher', '', 'Primaire', '', '', '5000',
      ]]);
  }

  // ---- Bulk import ---------------------------------------------------------
  protected importValidCount = computed(() => this.importRows().filter((r) => this.importRowValid(r)).length);

  protected importRowValid(r: StaffImportRow): boolean {
    return !!r.name?.trim();
  }

  protected openImport(): void {
    this.resetImport();
    this.mode.set('import');
  }

  protected closeImport(): void {
    this.mode.set('list');
    this.resetImport();
  }

  protected resetImport(): void {
    this.importText.set('');
    this.importRows.set([]);
    this.importResult.set(null);
    this.importError.set(null);
    this.importing.set(false);
    this.importCreateLogin.set(false);
  }

  protected onImportText(text: string): void {
    this.importText.set(text);
    this.importRows.set(this.parseImportRows(text));
  }

  protected onImportFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.importError.set(null);
    const isExcel = /\.(xlsx?|xlsm)$/i.test(file.name);
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        if (isExcel) {
          const XLSX = await import('xlsx');
          const wb = XLSX.read(reader.result, { type: 'array' });
          const sheet = wb.Sheets[wb.SheetNames[0]];
          this.onImportText(XLSX.utils.sheet_to_csv(sheet));
        } else {
          this.onImportText(String(reader.result ?? ''));
        }
      } catch {
        this.importError.set(this.fr() ? 'Fichier illisible — vérifiez le format.' : 'Unreadable file — check the format.');
      }
      input.value = '';
    };
    if (isExcel) reader.readAsArrayBuffer(file); else reader.readAsText(file);
  }

  protected loadImportSample(): void {
    const sample =
      'nom,sexe,type,email,telephone,roles,classe,departement,salaire_mensuel,taux_horaire\n' +
      'NGONO Jean Paul,M,Permanent,j.ngono@bbc.cm,+237 670000001,teacher|form_teacher,6ème A,Sciences,350000,\n' +
      'MBAH Alice,F,Vacataire,a.mbah@bbc.cm,+237 670000002,teacher,,, ,5000\n' +
      'TCHATCHE Paul,M,Permanent,p.tchatche@bbc.cm,+237 670000003,prefect,,,280000,';
    this.onImportText(sample);
  }

  protected doImport(): void {
    const rows = this.importRows().filter((r) => this.importRowValid(r));
    if (!rows.length) return;
    this.importing.set(true);
    this.importError.set(null);
    this.api.importStaff({ createLogin: this.importCreateLogin(), rows }).subscribe({
      next: (res) => { this.importing.set(false); this.importResult.set(res); this.reload(); },
      error: (e) => { this.importing.set(false); this.importError.set(this.importErrorMessage(e)); },
    });
  }

  private importErrorMessage(e: unknown): string {
    const fr = this.fr();
    if (e instanceof HttpErrorResponse) {
      if (e.status === 0) return fr ? 'Connexion interrompue (réseau ou délai dépassé) — réessayez.' : 'Connection lost (network or timeout) — please retry.';
      if (e.status === 401) return fr ? 'Session expirée — reconnectez-vous puis relancez l\'import.' : 'Session expired — sign in again then retry the import.';
      if (e.status === 403) return fr ? 'Vous n\'avez pas la permission d\'importer le personnel.' : 'You do not have permission to import staff.';
      if (e.status === 413) return fr ? 'Fichier trop volumineux — importez par lots plus petits.' : 'File too large — import in smaller batches.';
      const msg = e.error?.message;
      if (msg && typeof msg === 'object') {
        const parts = Object.entries(msg as Record<string, string>).map(([k, v]) => `${k}: ${v}`);
        if (parts.length) return parts.join(' · ');
      }
      if (typeof msg === 'string' && msg) return msg;
      return (fr ? 'Import impossible' : 'Import failed') + ` (HTTP ${e.status}).`;
    }
    return fr ? 'Import impossible.' : 'Import failed.';
  }

  private parseImportRows(text: string): StaffImportRow[] {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length);
    if (!lines.length) return [];
    const delim = this.detectDelim(lines[0]);
    const cells = lines.map((l) => this.splitLine(l, delim));
    const map = this.mapStaffHeader(cells[0]);
    const dataRows = map ? cells.slice(1) : cells;
    const idx = map ?? {
      name: 0, sex: 1, type: 2, email: 3, phone: 4, roles: 5,
      formClass: 6, section: 7, department: 8, monthlySalary: 9, hourlyRate: 10,
    };
    const at = (r: string[], i: number) => (i >= 0 ? (r[i] ?? '').trim() : '');
    return dataRows.map((r) => ({
      name: at(r, idx.name),
      sex: this.normSex(at(r, idx.sex)),
      type: this.normType(at(r, idx.type)),
      email: at(r, idx.email) || undefined,
      phone: at(r, idx.phone) || undefined,
      roles: this.normRoles(at(r, idx.roles)),
      formClass: at(r, idx.formClass) || undefined,
      section: at(r, idx.section) || undefined,
      department: at(r, idx.department) || undefined,
      monthlySalary: this.normNum(at(r, idx.monthlySalary)),
      hourlyRate: this.normNum(at(r, idx.hourlyRate)),
    }));
  }

  private mapStaffHeader(cells: string[]): {
    name: number; sex: number; type: number; email: number; phone: number;
    roles: number; formClass: number; section: number; department: number;
    monthlySalary: number; hourlyRate: number;
  } | null {
    const idx: Record<string, number> = {};
    cells.forEach((raw, i) => {
      const c = raw.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
      if (idx['name'] === undefined && /^(nom|name|full.?name|employe)/.test(c)) idx['name'] = i;
      else if (idx['sex'] === undefined && /(sexe|sex|genre|gender)/.test(c)) idx['sex'] = i;
      else if (idx['type'] === undefined && /(type|contrat|contract|statut)/.test(c)) idx['type'] = i;
      else if (idx['email'] === undefined && /(e-?mail|courriel)/.test(c)) idx['email'] = i;
      else if (idx['phone'] === undefined && /(tel|phone|contact|numero)/.test(c)) idx['phone'] = i;
      else if (idx['roles'] === undefined && /(role|roles|fonction)/.test(c)) idx['roles'] = i;
      else if (idx['section'] === undefined && /(section|cycle)/.test(c)) idx['section'] = i;
      else if (idx['formClass'] === undefined && /(classe|form.?class|pp)/.test(c) && !/salaire/.test(c)) idx['formClass'] = i;
      else if (idx['department'] === undefined && /(depart|dept|service)/.test(c)) idx['department'] = i;
      else if (idx['monthlySalary'] === undefined && /(salaire|salary|mensuel|monthly)/.test(c)) idx['monthlySalary'] = i;
      else if (idx['hourlyRate'] === undefined && /(taux|horaire|hourly|rate)/.test(c)) idx['hourlyRate'] = i;
    });
    if (idx['name'] === undefined) return null;
    return {
      name: idx['name'] ?? -1,
      sex: idx['sex'] ?? -1,
      type: idx['type'] ?? -1,
      email: idx['email'] ?? -1,
      phone: idx['phone'] ?? -1,
      roles: idx['roles'] ?? -1,
      formClass: idx['formClass'] ?? -1,
      section: idx['section'] ?? -1,
      department: idx['department'] ?? -1,
      monthlySalary: idx['monthlySalary'] ?? -1,
      hourlyRate: idx['hourlyRate'] ?? -1,
    };
  }

  private detectDelim(line: string): string {
    const counts: Record<string, number> = { ';': 0, '\t': 0, ',': 0 };
    for (const ch of line) if (ch in counts) counts[ch]!++;
    if (counts['\t']! >= counts[';']! && counts['\t']! >= counts[',']!) return '\t';
    if (counts[';']! >= counts[',']!) return ';';
    return ',';
  }

  private splitLine(line: string, delim: string): string[] {
    return line.split(delim).map((c) => c.replace(/^"|"$/g, '').trim());
  }

  private normSex(v: string): string {
    const c = (v ?? '').trim().toLowerCase();
    if (!c) return '';
    if (/^(m|masculin|male|g|gar)/.test(c)) return 'M';
    if (/^(f|feminin|female|fille)/.test(c)) return 'F';
    return '';
  }

  private normType(v: string): string {
    const c = (v ?? '').trim().toLowerCase();
    if (!c) return '';
    if (/^vac|contract|horaire/.test(c)) return 'Vacataire';
    if (/^perm|titulaire|full/.test(c)) return 'Permanent';
    return v.trim();
  }

  private normRoles(v: string): string[] {
    if (!v?.trim()) return [];
    return v.split(/[|;,/]+/).map((r) => r.trim()).filter(Boolean);
  }

  private normNum(v: string): number | null {
    if (!v?.trim()) return null;
    const n = Number(String(v).replace(/\s/g, '').replace(',', '.'));
    return Number.isFinite(n) ? Math.round(n) : null;
  }

  private blank(): EmployeeUpsert {
    return { name: '', sex: 'M', type: 'Permanent', email: '', phone: '', formClass: '', section: null,
             departmentId: null, monthlySalary: 350000, hourlyRate: 0, roles: [] };
  }
}
