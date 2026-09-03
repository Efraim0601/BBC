import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AcademicSessionView, FoundationApi } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';
import {
  AccountUpsert, AccountView, AccountingApiError, ClosePreview, FinanceAccountingApi,
  GeneralLedgerView, JournalUpsert, JournalView, PeriodView, PostingRuleUpsert, PostingRuleView,
  ReadinessView, ReconciliationView, TrialBalanceView,
} from './accounting.api';

type AccountingTab = 'overview' | 'accounts' | 'mappings' | 'periods' | 'journals' | 'trial-balance' | 'general-ledger' | 'reconciliation';
type ActionDialog = { kind: 'close' | 'reopen'; period: PeriodView } | null;

const today = () => new Date().toISOString().slice(0, 10);
const monthStart = () => `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01`;
const monthEnd = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10);
};

@Component({
  selector: 'bbc-finance-accounting',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fade-in mx-auto max-w-7xl space-y-5">
      <header class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div class="mb-2 flex flex-wrap items-center gap-2 text-xs font-bold uppercase tracking-[0.16em] text-brand-600">
            <span>Finance</span><span class="text-slate-300">/</span><span>Accounting foundation</span>
            <span class="rounded-full border border-gold-200 bg-gold-50 px-2 py-0.5 text-[10px] text-gold-700">Wave 1 · XAF</span>
          </div>
          <h1 class="text-2xl font-extrabold text-ink">{{ fr() ? 'Comptabilité' : 'Accounting' }}</h1>
          <p class="mt-1 max-w-2xl text-sm text-slate-500">
            {{ fr() ? 'Comptes, règles de comptabilisation, périodes et journaux équilibrés.' : 'Accounts, posting mappings, periods and balanced journals.' }}
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="inline-flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700">
            <span class="h-2 w-2 rounded-full bg-emerald-500"></span> {{ fr() ? 'Données legacy intactes' : 'Legacy data untouched' }}
          </span>
          <a routerLink="/finance" class="btn-secondary">{{ fr() ? 'Finance historique' : 'Legacy finance' }}</a>
        </div>
      </header>

      <nav aria-label="Accounting sections" class="scroll-y flex gap-2 overflow-x-auto rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        @for (item of tabs; track item.key) {
          <button type="button" (click)="setTab(item.key)"
            class="min-h-10 shrink-0 rounded-lg border px-3 text-sm font-bold transition"
            [class.bg-brand-700]="tab() === item.key" [class.text-white]="tab() === item.key"
            [class.border-brand-700]="tab() === item.key" [class.border-slate-200]="tab() !== item.key"
            [class.text-slate-600]="tab() !== item.key" [attr.aria-current]="tab() === item.key ? 'page' : null">
            {{ fr() ? item.fr : item.en }}
          </button>
        }
      </nav>

      @if (error()) {
        <div role="alert" class="flex flex-col gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 sm:flex-row sm:items-center sm:justify-between">
          <span>{{ error() }}</span>
          <button type="button" class="font-bold underline" (click)="reload()">{{ fr() ? 'Réessayer' : 'Retry' }}</button>
        </div>
      }
      @if (success()) {
        <div role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">{{ success() }}</div>
      }
      @if (loading()) {
        <div class="grid gap-4 md:grid-cols-3" aria-label="Loading">
          <div class="h-24 animate-pulse rounded-xl bg-slate-100"></div><div class="h-24 animate-pulse rounded-xl bg-slate-100"></div><div class="h-24 animate-pulse rounded-xl bg-slate-100"></div>
        </div>
      }

      @switch (tab()) {
        @case ('overview') {
          <section class="grid gap-5 lg:grid-cols-[1.25fr_.75fr]">
            <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'État de préparation' : 'Readiness' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Chaque blocage explique son action corrective.' : 'Every blocker explains the corrective action.' }}</p></div>
                @if (readiness(); as ready) { <span class="rounded-full px-3 py-1 text-xs font-extrabold" [class]="ready.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-800'">{{ ready.ready ? (fr() ? 'Prêt à poster' : 'Ready to post') : (fr() ? 'Action requise' : 'Action required') }}</span> }
              </div>
              @if (!readiness()) { <div class="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">{{ fr() ? 'Chargement de la checklist…' : 'Loading checklist…' }}</div> }
              @if (readiness(); as ready) {
                <div class="mt-5 space-y-3">
                  @for (check of ready.checks; track check.key) {
                    <div class="rounded-xl border p-4" [class]="check.status === 'READY' ? 'border-emerald-200 bg-emerald-50/60' : 'border-amber-200 bg-amber-50/60'">
                      <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div><div class="flex items-center gap-2 text-sm font-extrabold text-ink"><span class="flex h-6 w-6 items-center justify-center rounded-full text-xs" [class]="check.status === 'READY' ? 'bg-emerald-600 text-white' : 'bg-amber-500 text-white'">{{ check.status === 'READY' ? '✓' : '!' }}</span>{{ check.label }}</div><p class="mt-1 pl-8 text-xs text-slate-600">{{ check.detail }}</p></div>
                        @if (check.status !== 'READY') { <button type="button" class="shrink-0 text-xs font-extrabold text-brand-700 underline" (click)="fix(check.action)">{{ fr() ? 'Corriger maintenant' : 'Fix now' }}</button> }
                      </div>
                      @if (check.blockers.length) { <div class="mt-2 pl-8 text-xs font-semibold text-amber-800">{{ check.blockers[0].label }}</div> }
                    </div>
                  }
                </div>
              }
            </div>
            <div class="space-y-5">
              <div class="rounded-2xl border border-brand-200 bg-brand-700 p-5 text-white shadow-sm"><div class="text-xs font-bold uppercase tracking-[0.16em] text-brand-100">{{ fr() ? 'Raccourcis' : 'Quick actions' }}</div><h2 class="mt-2 text-xl font-extrabold">{{ fr() ? 'Construire le socle' : 'Build the foundation' }}</h2><p class="mt-2 text-sm text-brand-100">{{ fr() ? 'Les écritures sont en XAF mineures entières et deviennent immuables après postage.' : 'Entries use integer XAF minor units and become immutable after posting.' }}</p><div class="mt-5 grid gap-2"><button type="button" class="rounded-lg bg-white px-3 py-2 text-left text-sm font-extrabold text-brand-700" (click)="setTab('accounts')">1. {{ fr() ? 'Vérifier les comptes' : 'Review accounts' }}</button><button type="button" class="rounded-lg bg-white/10 px-3 py-2 text-left text-sm font-extrabold text-white ring-1 ring-white/25" (click)="setTab('mappings')">2. {{ fr() ? 'Tester les mappings' : 'Test mappings' }}</button><button type="button" class="rounded-lg bg-white/10 px-3 py-2 text-left text-sm font-extrabold text-white ring-1 ring-white/25" (click)="setTab('journals')">3. {{ fr() ? 'Poster un journal test' : 'Post a test journal' }}</button></div></div>
              <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex items-center justify-between"><h2 class="font-extrabold text-ink">{{ fr() ? 'Contrôles actifs' : 'Controls active' }}</h2><span class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-600">V59</span></div><ul class="mt-4 space-y-3 text-sm text-slate-600"><li class="flex gap-2"><span class="text-emerald-600">✓</span>{{ fr() ? 'Débit = crédit obligatoire' : 'Debit must equal credit' }}</li><li class="flex gap-2"><span class="text-emerald-600">✓</span>{{ fr() ? 'Périodes fermées bloquées' : 'Closed periods blocked' }}</li><li class="flex gap-2"><span class="text-emerald-600">✓</span>{{ fr() ? 'Journaux postés immuables' : 'Posted journals immutable' }}</li><li class="flex gap-2"><span class="text-emerald-600">✓</span>{{ fr() ? 'Clé source idempotente' : 'Unique source-event key' }}</li></ul></div>
            </div>
          </section>
        }

        @case ('accounts') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Catalogue de comptes' : 'Chart of accounts' }}</h2><p class="text-sm text-slate-500">{{ accounts().length }} {{ fr() ? 'comptes dans cet établissement' : 'accounts in this school' }}</p></div><div class="flex flex-wrap gap-2"><input class="field min-w-48" [ngModel]="accountQuery()" (ngModelChange)="accountQuery.set($event); loadAccounts()" placeholder="{{ fr() ? 'Rechercher code ou nom' : 'Search code or name' }}" aria-label="Account search"><button type="button" class="btn-primary" [disabled]="!canAction('ACCOUNT_MANAGE')" (click)="openAccount()">+ {{ fr() ? 'Nouveau compte' : 'New account' }}</button></div></div>
            @if (!accounts().length) { <div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucun compte trouvé. Créez le premier compte de mouvement.' : 'No accounts found. Create the first posting account.' }}</div> }
            @else { <div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[760px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">{{ fr() ? 'Code' : 'Code' }}</th><th class="px-4 py-3">{{ fr() ? 'Compte' : 'Account' }}</th><th class="px-4 py-3">{{ fr() ? 'Nature' : 'Type' }}</th><th class="px-4 py-3">{{ fr() ? 'Mouvement' : 'Posting' }}</th><th class="px-4 py-3">{{ fr() ? 'Usage posté' : 'Posted usage' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Action' : 'Action' }}</th></tr></thead><tbody>@for (account of accounts(); track account.id) {<tr class="border-t border-slate-100 hover:bg-slate-50"><td class="px-4 py-3 font-mono font-bold text-brand-700">{{ account.code }}</td><td class="px-4 py-3"><div class="font-bold text-ink">{{ fr() ? account.nameFr : account.nameEn }}</div><div class="text-xs text-slate-500">{{ account.nameFr }} · {{ account.currency || 'XAF' }}</div></td><td class="px-4 py-3"><span class="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">{{ account.accountType }}</span></td><td class="px-4 py-3"><span class="rounded-full px-2 py-1 text-xs font-bold" [class]="account.postingAllowed && account.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ account.postingAllowed && account.active ? (fr() ? 'Mouvement' : 'Posting') : (fr() ? 'Lecture seule' : 'Read only') }}</span></td><td class="px-4 py-3 text-slate-600">{{ account.postedUsageCount }}</td><td class="px-4 py-3 text-right"><button type="button" class="text-xs font-extrabold text-brand-700 underline" [disabled]="!canAction('ACCOUNT_MANAGE')" (click)="editAccount(account)">{{ fr() ? 'Modifier' : 'Edit' }}</button></td></tr>}</tbody></table></div> }
          </section>
          @if (accountFormOpen()) { <section class="rounded-2xl border border-brand-200 bg-brand-50/40 p-5 shadow-sm"><div class="flex items-center justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ accountDraft.id ? (fr() ? 'Modifier le compte' : 'Edit account') : (fr() ? 'Créer un compte' : 'Create account') }}</h2><p class="text-xs text-slate-600">* {{ fr() ? 'Champ obligatoire' : 'Required field' }}</p></div><button type="button" class="btn-secondary" (click)="closeAccount()">×</button></div><div class="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-4"><label class="field-label">Code *<input class="field" [class.input-error]="accountFieldErrors['code']" [(ngModel)]="accountDraft.code" required placeholder="1100"><span class="field-error" *ngIf="accountFieldErrors['code']">{{ accountFieldErrors['code'] }}</span></label><label class="field-label">{{ fr() ? 'Nom français' : 'French name' }} *<input class="field" [class.input-error]="accountFieldErrors['nameFr']" [(ngModel)]="accountDraft.nameFr" required><span class="field-error" *ngIf="accountFieldErrors['nameFr']">{{ accountFieldErrors['nameFr'] }}</span></label><label class="field-label">English name *<input class="field" [class.input-error]="accountFieldErrors['nameEn']" [(ngModel)]="accountDraft.nameEn" required><span class="field-error" *ngIf="accountFieldErrors['nameEn']">{{ accountFieldErrors['nameEn'] }}</span></label><label class="field-label">{{ fr() ? 'Type' : 'Type' }} *<select class="field" [class.input-error]="accountFieldErrors['accountType']" [(ngModel)]="accountDraft.accountType"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option><option value="ASSET">ASSET</option><option value="LIABILITY">LIABILITY</option><option value="EQUITY">EQUITY</option><option value="REVENUE">REVENUE</option><option value="EXPENSE">EXPENSE</option></select><span class="field-error" *ngIf="accountFieldErrors['accountType']">{{ accountFieldErrors['accountType'] }}</span></label><label class="field-label">{{ fr() ? 'Sens normal' : 'Normal side' }} *<select class="field" [(ngModel)]="accountDraft.normalSide"><option value="DEBIT">DEBIT</option><option value="CREDIT">CREDIT</option></select></label><label class="field-label">{{ fr() ? 'Devise' : 'Currency' }}<input class="field" maxlength="3" [(ngModel)]="accountDraft.currency"></label><label class="field-label">{{ fr() ? 'Date de début' : 'Effective from' }}<input type="date" class="field" [(ngModel)]="accountDraft.effectiveFrom"></label><label class="field-label">{{ fr() ? 'Date de fin' : 'Effective to' }}<input type="date" class="field" [(ngModel)]="accountDraft.effectiveTo"></label></div><div class="mt-4 flex flex-wrap gap-5 text-sm"><label class="inline-flex items-center gap-2 font-semibold"><input type="checkbox" [(ngModel)]="accountDraft.postingAllowed"> {{ fr() ? 'Compte de mouvement' : 'Posting account' }}</label><label class="inline-flex items-center gap-2 font-semibold"><input type="checkbox" [(ngModel)]="accountDraft.active"> {{ fr() ? 'Actif' : 'Active' }}</label></div>@if (accountError()) {<div class="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ accountError() }}</div>}<div class="mt-5 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="closeAccount()">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="saving()" (click)="saveAccount()">{{ saving() ? '…' : (fr() ? 'Enregistrer' : 'Save') }}</button></div></section> }
        }

        @case ('mappings') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Mappings de comptabilisation' : 'Posting mappings' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Les règles les plus spécifiques sont choisies en priorité.' : 'The most specific active rule wins.' }}</p></div><button type="button" class="btn-primary" [disabled]="!canAction('POSTING_RULE_MANAGE')" (click)="ruleFormOpen.set(!ruleFormOpen())">+ {{ fr() ? 'Ajouter une règle' : 'Add rule' }}</button></div>@if (ruleFormOpen()) {<div class="mt-5 rounded-xl border border-brand-200 bg-brand-50/40 p-4"><div class="grid gap-4 md:grid-cols-4"><label class="field-label">Event type *<input class="field" [(ngModel)]="ruleDraft.eventType" placeholder="EXPENSE_POST"></label><label class="field-label">Side *<select class="field" [(ngModel)]="ruleDraft.side"><option value="DEBIT">DEBIT</option><option value="CREDIT">CREDIT</option></select></label><label class="field-label">{{ fr() ? 'Compte cible' : 'Target account' }} *<select class="field" [(ngModel)]="ruleDraft.targetAccountId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of accounts(); track account.id) {<option [value]="account.id">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }}</option>}</select></label><label class="field-label">{{ fr() ? 'Priorité' : 'Priority' }}<input class="field" type="number" min="0" [(ngModel)]="ruleDraft.priority"></label></div><div class="mt-3 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="ruleFormOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" (click)="saveRule()">{{ fr() ? 'Enregistrer' : 'Save mapping' }}</button></div></div>}@if (!rules().length) {<div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucun mapping configuré.' : 'No mappings configured.' }}</div>}@else {<div class="mt-5 grid gap-3 lg:grid-cols-2">@for (rule of rules(); track rule.id) {<article class="rounded-xl border border-slate-200 bg-slate-50/60 p-4"><div class="flex items-start justify-between gap-3"><div><div class="font-mono text-sm font-extrabold text-brand-700">{{ rule.eventType }} · {{ rule.side }}</div><div class="mt-1 text-sm font-bold text-ink">{{ rule.targetAccountCode }}</div><div class="text-xs text-slate-500">{{ fr() ? 'Priorité' : 'Priority' }} {{ rule.priority }} · {{ rule.scopeCode || (fr() ? 'École' : 'School default') }}</div></div><span class="rounded-full px-2 py-1 text-[10px] font-extrabold" [class]="rule.enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'">{{ rule.enabled ? 'ACTIVE' : 'OFF' }}</span></div></article>}</div>}</section>
        }

        @case ('periods') {
          <div class="mb-4 rounded-xl border border-brand-200 bg-brand-50/40 p-4"><label class="field-label max-w-md">Academic session for periods *<select class="field" [(ngModel)]="periodSessionId" aria-label="Academic session"><option value="">Choose a session</option>@for (session of academicSessions(); track session.id) {<option [value]="session.id">{{ session.code }} · {{ session.label }}</option>}</select></label><p class="mt-2 text-xs text-slate-600">Student-finance and payroll postings will be linked to this session.</p></div>
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Périodes comptables' : 'Accounting periods' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Une période ouverte ne peut pas chevaucher une autre période ouverte.' : 'Open periods cannot overlap.' }}</p></div><div class="flex flex-wrap gap-2"><input type="date" class="field" [(ngModel)]="periodStart" aria-label="Start date"><input type="date" class="field" [(ngModel)]="periodEnd" aria-label="End date"><button type="button" class="btn-primary" [disabled]="!canAction('LEDGER_CLOSE')" (click)="generatePeriods()">{{ fr() ? 'Générer les mois' : 'Generate months' }}</button></div></div>@if (closePreview(); as preview) {<div class="mt-4 rounded-xl border p-4" [class]="preview.ready ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'"><div class="font-bold">{{ preview.ready ? (fr() ? 'Période prête à fermer' : 'Period ready to close') : (fr() ? 'Blocages avant clôture' : 'Close blockers') }} · {{ preview.periodCode }}</div>@if (preview.blockers.length) {<ul class="mt-2 list-disc pl-5 text-sm">@for (blocker of preview.blockers; track blocker.label) {<li>{{ blocker.label }}</li>}</ul>}</div>}@if (!periods().length) {<div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucune période. Générez le calendrier comptable.' : 'No periods. Generate an accounting calendar.' }}</div>}@else {<div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[760px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">Code</th><th class="px-4 py-3">{{ fr() ? 'Dates' : 'Dates' }}</th><th class="px-4 py-3">{{ fr() ? 'Statut' : 'Status' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Actions' : 'Actions' }}</th></tr></thead><tbody>@for (period of periods(); track period.id) {<tr class="border-t border-slate-100"><td class="px-4 py-3 font-mono font-bold text-brand-700">{{ period.code }}</td><td class="px-4 py-3 text-slate-600">{{ period.startDate }} → {{ period.endDate }}</td><td class="px-4 py-3"><span class="rounded-full px-2 py-1 text-xs font-extrabold" [class]="period.status === 'OPEN' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600'">{{ period.status }}</span></td><td class="px-4 py-3 text-right"><div class="flex justify-end gap-2">@if (period.status === 'OPEN') {<button type="button" class="text-xs font-extrabold text-brand-700 underline" [disabled]="!canAction('LEDGER_CLOSE')" (click)="previewPeriod(period)">{{ fr() ? 'Prévisualiser clôture' : 'Preview close' }}</button>} @else {<button type="button" class="text-xs font-extrabold text-brand-700 underline" [disabled]="!canAction('LEDGER_REOPEN')" (click)="openPeriodAction('reopen', period)">{{ fr() ? 'Rouvrir' : 'Reopen' }}</button>}</div></td></tr>}</tbody></table></div>}</section>
          @if (actionDialog(); as dialog) {<section class="rounded-2xl border border-amber-300 bg-amber-50 p-5 shadow-sm"><h2 class="font-extrabold text-ink">{{ dialog.kind === 'close' ? (fr() ? 'Confirmer la clôture' : 'Confirm close') : (fr() ? 'Confirmer la réouverture' : 'Confirm reopen') }} · {{ dialog.period.code }}</h2><p class="mt-1 text-sm text-slate-700">{{ dialog.kind === 'close' ? (fr() ? 'Les nouvelles écritures à cette date seront bloquées.' : 'New entries for this date will be blocked.') : (fr() ? 'La période redeviendra disponible pour le postage.' : 'The period will become available for posting again.') }}</p><label class="field-label mt-4">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} *<textarea class="field" [(ngModel)]="actionReason" rows="3"></textarea></label><div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="actionDialog.set(null)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" (click)="confirmPeriodAction()">{{ fr() ? 'Confirmer' : 'Confirm' }}</button></div></section>}
        }

        @case ('journals') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Journaux' : 'Journals' }}</h2><p class="text-sm text-slate-500">{{ journals().length }} {{ fr() ? 'écritures chargées' : 'entries loaded' }}</p></div><button type="button" class="btn-primary" [disabled]="!canAction('LEDGER_POST')" (click)="journalFormOpen.set(!journalFormOpen())">+ {{ fr() ? 'Nouveau journal' : 'New journal' }}</button></div>@if (journalFormOpen()) {<div class="mt-5 rounded-xl border border-brand-200 bg-brand-50/40 p-4"><div class="mb-3 text-xs font-bold text-slate-600">* {{ fr() ? 'Champs obligatoires. Les montants sont des entiers XAF.' : 'Required fields. Amounts are integer XAF minor units.' }}</div><div class="grid gap-4 md:grid-cols-2 lg:grid-cols-4"><label class="field-label">{{ fr() ? 'Période' : 'Period' }} *<select class="field" [(ngModel)]="journalDraft.accountingPeriodId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (period of openPeriods(); track period.id) {<option [value]="period.id">{{ period.code }} · {{ period.startDate }}—{{ period.endDate }}</option>}</select></label><label class="field-label">{{ fr() ? 'Date' : 'Date' }} *<input type="date" class="field" [(ngModel)]="journalDraft.entryDate"></label><label class="field-label">{{ fr() ? 'Libellé' : 'Description' }} *<input class="field" [(ngModel)]="journalDraft.description" placeholder="Expense test"></label><label class="field-label">{{ fr() ? 'Montant XAF' : 'XAF amount' }} *<input class="field" type="number" min="1" step="1" [(ngModel)]="journalAmount"></label><label class="field-label">{{ fr() ? 'Compte débit' : 'Debit account' }} *<select class="field" [(ngModel)]="journalDebitAccount"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of postingAccounts(); track account.id) {<option [value]="account.id">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }}</option>}</select></label><label class="field-label">{{ fr() ? 'Compte crédit' : 'Credit account' }} *<select class="field" [(ngModel)]="journalCreditAccount"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of postingAccounts(); track account.id) {<option [value]="account.id">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }}</option>}</select></label></div>@if (journalError()) {<div class="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ journalError() }}</div>}<div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="journalFormOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="saving()" (click)="saveJournal()">{{ saving() ? '…' : (fr() ? 'Créer le brouillon' : 'Create draft') }}</button></div></div>}@if (!journals().length) {<div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucun journal. Créez un brouillon équilibré pour commencer.' : 'No journals. Create a balanced draft to begin.' }}</div>}@else {<div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[920px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">{{ fr() ? 'Numéro' : 'Number' }}</th><th class="px-4 py-3">{{ fr() ? 'Date' : 'Date' }}</th><th class="px-4 py-3">{{ fr() ? 'Libellé' : 'Description' }}</th><th class="px-4 py-3">{{ fr() ? 'Statut' : 'Status' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Total' : 'Total' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Actions' : 'Actions' }}</th></tr></thead><tbody>@for (journal of journals(); track journal.id) {<tr class="border-t border-slate-100"><td class="px-4 py-3 font-mono font-bold text-brand-700">{{ journal.number }}</td><td class="px-4 py-3">{{ journal.entryDate }}</td><td class="px-4 py-3"><div class="font-bold text-ink">{{ journal.description }}</div><div class="text-xs text-slate-500">{{ journal.sourceType || 'MANUAL' }}</div></td><td class="px-4 py-3"><span class="rounded-full px-2 py-1 text-xs font-extrabold" [class]="journal.status === 'POSTED' ? 'bg-emerald-100 text-emerald-700' : journal.status === 'DRAFT' ? 'bg-amber-100 text-amber-800' : 'bg-slate-200 text-slate-600'">{{ journal.status }}</span></td><td class="px-4 py-3 text-right font-bold">{{ money(journal.totalDebitMinor) }}</td><td class="px-4 py-3 text-right"><div class="flex justify-end gap-3">@if (journal.status === 'DRAFT') {<button type="button" class="text-xs font-extrabold text-brand-700 underline" [disabled]="!canAction('LEDGER_POST')" (click)="postJournal(journal)">{{ fr() ? 'Poster' : 'Post' }}</button>} @if (journal.status === 'POSTED') {<button type="button" class="text-xs font-extrabold text-rose-700 underline" [disabled]="!canAction('LEDGER_REVERSE')" (click)="openReverse(journal)">{{ fr() ? 'Renverser' : 'Reverse' }}</button>}<button type="button" class="text-xs font-extrabold text-slate-600 underline" (click)="selectJournal(journal)">{{ fr() ? 'Détail' : 'Details' }}</button></div></td></tr>}</tbody></table></div>}</section>
          @if (selectedJournal(); as detail) {<section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex items-start justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ detail.number }}</h2><p class="text-sm text-slate-500">{{ detail.description }} · {{ detail.entryDate }}</p></div><button type="button" class="btn-secondary" (click)="selectedJournal.set(null)">×</button></div><div class="mt-4 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[680px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-2">#</th><th class="px-4 py-2">{{ fr() ? 'Compte' : 'Account' }}</th><th class="px-4 py-2 text-right">{{ fr() ? 'Débit' : 'Debit' }}</th><th class="px-4 py-2 text-right">{{ fr() ? 'Crédit' : 'Credit' }}</th></tr></thead><tbody>@for (line of detail.lines; track line.id) {<tr class="border-t border-slate-100"><td class="px-4 py-2">{{ line.lineNumber }}</td><td class="px-4 py-2 font-semibold">{{ line.accountCode }} · {{ line.accountName }}</td><td class="px-4 py-2 text-right">{{ money(line.debitMinor) }}</td><td class="px-4 py-2 text-right">{{ money(line.creditMinor) }}</td></tr>}</tbody><tfoot class="border-t-2 border-slate-300 font-extrabold"><tr><td colspan="2" class="px-4 py-2 text-right">{{ fr() ? 'Totaux' : 'Totals' }}</td><td class="px-4 py-2 text-right">{{ money(detail.totalDebitMinor) }}</td><td class="px-4 py-2 text-right">{{ money(detail.totalCreditMinor) }}</td></tr></tfoot></table></div></section>}
          @if (reverseTarget(); as reverse) {<section class="rounded-2xl border border-rose-300 bg-rose-50 p-5 shadow-sm"><h2 class="font-extrabold text-ink">{{ fr() ? 'Renverser ' : 'Reverse ' }}{{ reverse.number }}</h2><p class="mt-1 text-sm text-slate-700">{{ fr() ? 'Le journal original reste conservé. Une écriture inverse sera postée dans une période ouverte.' : 'The original remains preserved. An opposite entry will post in an open period.' }}</p><label class="field-label mt-4">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} *<textarea class="field" [(ngModel)]="reverseReason" rows="3"></textarea></label><div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="reverseTarget.set(null)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" (click)="confirmReverse()">{{ fr() ? 'Créer le renversement' : 'Create reversal' }}</button></div></section>}
        }

        @case ('trial-balance') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Balance générale' : 'Trial balance' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Les totaux débit et crédit doivent rester égaux.' : 'Debit and credit totals must remain equal.' }}</p></div><div class="flex flex-wrap gap-2"><input type="date" class="field" [(ngModel)]="trialDate"><label class="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-xs font-bold"><input type="checkbox" [(ngModel)]="trialIncludeZero"> {{ fr() ? 'Afficher les zéros' : 'Show zero' }}</label><button type="button" class="btn-primary" (click)="loadTrialBalance()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div></div>@if (trialBalance(); as trial) {<div class="mt-5 grid gap-3 sm:grid-cols-3"><div class="rounded-xl border border-slate-200 bg-slate-50 p-4"><div class="text-xs font-bold uppercase text-slate-500">{{ fr() ? 'Débit' : 'Debit' }}</div><div class="mt-1 text-xl font-extrabold text-ink">{{ money(trial.totalDebitMinor) }}</div></div><div class="rounded-xl border border-slate-200 bg-slate-50 p-4"><div class="text-xs font-bold uppercase text-slate-500">{{ fr() ? 'Crédit' : 'Credit' }}</div><div class="mt-1 text-xl font-extrabold text-ink">{{ money(trial.totalCreditMinor) }}</div></div><div class="rounded-xl border p-4" [class]="trial.balanced ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'"><div class="text-xs font-bold uppercase text-slate-500">{{ fr() ? 'Contrôle' : 'Control' }}</div><div class="mt-1 text-xl font-extrabold" [class]="trial.balanced ? 'text-emerald-700' : 'text-rose-700'">{{ trial.balanced ? '✓ OK' : '!' }}</div></div></div>@if (!trial.rows.length) {<div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucune ligne postée à cette date.' : 'No posted lines at this date.' }}</div>}@else {<div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[720px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">Code</th><th class="px-4 py-3">{{ fr() ? 'Compte' : 'Account' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Débit' : 'Debit' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Crédit' : 'Credit' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Solde' : 'Balance' }}</th></tr></thead><tbody>@for (row of trial.rows; track row.accountId) {<tr class="border-t border-slate-100"><td class="px-4 py-3 font-mono font-bold text-brand-700">{{ row.accountCode }}</td><td class="px-4 py-3 font-semibold">{{ row.accountName }}</td><td class="px-4 py-3 text-right">{{ money(row.debitMinor) }}</td><td class="px-4 py-3 text-right">{{ money(row.creditMinor) }}</td><td class="px-4 py-3 text-right font-bold">{{ money(row.balanceMinor) }}</td></tr>}</tbody></table></div>}}</section>}
        @case ('general-ledger') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Grand livre' : 'General ledger' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Choisissez un compte pour voir ses écritures postées.' : 'Choose an account to inspect posted entries.' }}</p></div><div class="flex flex-wrap gap-2"><select class="field w-full sm:w-auto sm:min-w-64" [(ngModel)]="ledgerAccountId"><option value="">{{ fr() ? 'Choisir un compte' : 'Choose an account' }}</option>@for (account of postingAccounts(); track account.id) {<option [value]="account.id">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }}</option>}</select><input type="date" class="field" [(ngModel)]="ledgerFrom"><input type="date" class="field" [(ngModel)]="ledgerTo"><button type="button" class="btn-primary" [disabled]="!ledgerAccountId" (click)="loadLedger()">{{ fr() ? 'Charger' : 'Load' }}</button></div></div>@if (ledger(); as book) {<div class="mt-5 flex flex-wrap gap-2"><span class="rounded-full bg-brand-50 px-3 py-1 text-xs font-extrabold text-brand-700">{{ book.accountCode }} · {{ book.accountName }}</span><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{{ book.fromDate }} → {{ book.toDate }}</span></div>@if (!book.lines.length) {<div class="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">{{ fr() ? 'Aucune écriture dans cette période.' : 'No entries in this period.' }}</div>}@else {<div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[860px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">{{ fr() ? 'Journal' : 'Journal' }}</th><th class="px-4 py-3">{{ fr() ? 'Date / libellé' : 'Date / description' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Débit' : 'Debit' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Crédit' : 'Credit' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Solde courant' : 'Running balance' }}</th></tr></thead><tbody>@for (line of book.lines; track line.journalId + line.entryDate) {<tr class="border-t border-slate-100"><td class="px-4 py-3 font-mono font-bold text-brand-700">{{ line.journalNumber }}</td><td class="px-4 py-3"><div class="font-semibold">{{ line.description }}</div><div class="text-xs text-slate-500">{{ line.entryDate }}</div></td><td class="px-4 py-3 text-right">{{ money(line.debitMinor) }}</td><td class="px-4 py-3 text-right">{{ money(line.creditMinor) }}</td><td class="px-4 py-3 text-right font-bold">{{ money(line.runningBalanceMinor) }}</td></tr>}</tbody></table></div>}}</section>}
        @case ('reconciliation') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Rapprochement & exceptions' : 'Reconciliation & exceptions' }}</h2><p class="text-sm text-slate-500">{{ fr() ? 'Chaque exception indique une action de réparation.' : 'Every exception includes a repair action.' }}</p></div><button type="button" class="btn-secondary" (click)="loadReconciliation()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div>@if (!reconciliation().length) {<div class="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-8 text-center text-sm font-semibold text-emerald-800">{{ fr() ? 'Aucune exception en attente.' : 'No pending exceptions.' }}</div>}@else {<div class="mt-5 space-y-3">@for (item of reconciliation(); track item.id) {<article class="rounded-xl border p-4" [class]="item.state === 'MISSING' || item.state === 'MISMATCH' ? 'border-amber-200 bg-amber-50/60' : 'border-slate-200 bg-slate-50'"><div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><div class="flex flex-wrap items-center gap-2"><span class="rounded-full bg-white px-2 py-1 text-[10px] font-extrabold text-slate-700">{{ item.state }}</span><span class="font-mono text-xs text-brand-700">{{ item.sourceType }} {{ item.sourceId || '' }}</span></div><p class="mt-2 text-sm font-bold text-ink">{{ item.reason }}</p><p class="mt-1 text-xs text-slate-600">{{ fr() ? 'Attendu' : 'Expected' }} {{ money(item.expectedAmount) }} · {{ fr() ? 'Posté' : 'Posted' }} {{ money(item.postedAmount) }}</p></div>@if (item.state === 'MISSING' || item.state === 'MISMATCH') {<button type="button" class="btn-secondary" [disabled]="!canAction('LEDGER_POST')" (click)="openReconciliation(item)">{{ fr() ? 'Résoudre' : 'Resolve' }}</button>}</div></article>}</div>}</section>
          @if (reconciliationTarget(); as item) {<section class="rounded-2xl border border-brand-200 bg-brand-50/40 p-5 shadow-sm"><h2 class="font-extrabold text-ink">{{ fr() ? 'Résoudre l’exception' : 'Resolve exception' }}</h2><p class="mt-1 text-sm text-slate-600">{{ item.reason }}</p><label class="field-label mt-4">{{ fr() ? 'Décision / motif' : 'Decision / reason' }} *<textarea class="field" [(ngModel)]="reconciliationReason" rows="3"></textarea></label><div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="reconciliationTarget.set(null)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" (click)="resolveReconciliation()">{{ fr() ? 'Marquer résolu' : 'Mark resolved' }}</button></div></section>}
        }
      }
    </div>
  `,
  styleUrl: './finance-accounting.scss',
})
export class FinanceAccountingComponent {
  protected api = inject(FinanceAccountingApi);
  protected auth = inject(AuthService);
  protected foundation = inject(FoundationApi);
  protected i18n = inject(I18nService);

  protected fr = () => this.i18n.lang() === 'fr';
  protected tabs: { key: AccountingTab; fr: string; en: string }[] = [
    { key: 'overview', fr: 'Préparation', en: 'Readiness' }, { key: 'accounts', fr: 'Comptes', en: 'Accounts' },
    { key: 'mappings', fr: 'Mappings', en: 'Mappings' }, { key: 'periods', fr: 'Périodes', en: 'Periods' },
    { key: 'journals', fr: 'Journaux', en: 'Journals' }, { key: 'trial-balance', fr: 'Balance', en: 'Trial balance' },
    { key: 'general-ledger', fr: 'Grand livre', en: 'General ledger' }, { key: 'reconciliation', fr: 'Rapprochement', en: 'Reconciliation' },
  ];
  protected tab = signal<AccountingTab>('overview');
  protected loading = signal(false);
  protected saving = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected actions = signal<Record<string, boolean>>({});
  protected readiness = signal<ReadinessView | null>(null);
  protected accounts = signal<AccountView[]>([]);
  protected rules = signal<PostingRuleView[]>([]);
  protected periods = signal<PeriodView[]>([]);
  protected academicSessions = signal<AcademicSessionView[]>([]);
  protected journals = signal<JournalView[]>([]);
  protected reconciliation = signal<ReconciliationView[]>([]);
  protected trialBalance = signal<TrialBalanceView | null>(null);
  protected ledger = signal<GeneralLedgerView | null>(null);
  protected selectedJournal = signal<JournalView | null>(null);
  protected closePreview = signal<ClosePreview | null>(null);
  protected actionDialog = signal<ActionDialog>(null);
  protected reverseTarget = signal<JournalView | null>(null);
  protected reconciliationTarget = signal<ReconciliationView | null>(null);

  protected accountQuery = signal('');
  protected accountFormOpen = signal(false);
  protected accountError = signal<string | null>(null);
  protected accountFieldErrors: Record<string, string> = {};
  protected accountDraft: AccountUpsert & { id?: string } = this.blankAccount();
  protected ruleFormOpen = signal(false);
  protected ruleDraft: PostingRuleUpsert = this.blankRule();
  protected journalFormOpen = signal(false);
  protected journalError = signal<string | null>(null);
  protected journalDraft: JournalUpsert = this.blankJournal();
  protected journalAmount = 0;
  protected journalDebitAccount = '';
  protected journalCreditAccount = '';
  protected periodStart = monthStart();
  protected periodEnd = monthEnd();
  protected periodSessionId = '';
  protected actionReason = '';
  protected reverseReason = '';
  protected reconciliationReason = '';
  protected trialDate = today();
  protected trialIncludeZero = false;
  protected ledgerAccountId = '';
  protected ledgerFrom = '';
  protected ledgerTo = today();

  constructor() { this.reload(); }

  protected canAction(action: string): boolean { return this.actions()[action] === true; }
  protected canRead(): boolean { return this.actions()['FINANCE_OVERVIEW_VIEW'] === true; }
  protected money(amount: number): string { return `${Math.round(Number(amount) || 0).toLocaleString('fr-FR')} XAF`; }
  protected openPeriods(): PeriodView[] { return this.periods().filter(p => p.status === 'OPEN'); }
  protected postingAccounts(): AccountView[] { return this.accounts().filter(a => a.active && a.postingAllowed); }

  protected setTab(tab: AccountingTab): void {
    this.tab.set(tab); this.error.set(null); this.success.set(null);
    if (tab === 'trial-balance' && !this.trialBalance()) this.loadTrialBalance();
    if (tab === 'reconciliation') this.loadReconciliation();
  }

  protected reload(): void {
    this.loading.set(true); this.error.set(null);
    forkJoin({ readiness: this.api.readiness(), accounts: this.api.accounts(), rules: this.api.postingRules(), periods: this.api.periods(), journals: this.api.journals(), actions: this.foundation.actionPermissions(), academicSessions: this.foundation.listSessions() }).subscribe({
      next: value => { this.readiness.set(value.readiness); this.accounts.set(value.accounts); this.rules.set(value.rules); this.periods.set(value.periods); this.journals.set(value.journals.items); this.actions.set(value.actions); this.academicSessions.set(value.academicSessions); if (!this.periodSessionId) { this.periodSessionId = value.academicSessions.find(session => session.current)?.id || value.academicSessions[0]?.id || ''; } this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(this.message(err)); },
    });
  }
  protected loadAccounts(): void { this.api.accounts(this.accountQuery()).subscribe({ next: value => this.accounts.set(value), error: err => this.error.set(this.message(err)) }); }
  protected loadReconciliation(): void { this.api.reconciliation().subscribe({ next: value => this.reconciliation.set(value), error: err => this.error.set(this.message(err)) }); }
  protected loadTrialBalance(): void { this.api.trialBalance(this.trialDate, this.trialIncludeZero).subscribe({ next: value => this.trialBalance.set(value), error: err => this.error.set(this.message(err)) }); }
  protected loadLedger(): void { if (!this.ledgerAccountId) return; this.api.generalLedger(this.ledgerAccountId, this.ledgerFrom || undefined, this.ledgerTo || undefined).subscribe({ next: value => this.ledger.set(value), error: err => this.error.set(this.message(err)) }); }

  protected fix(action: string): void {
    if (action.includes('ACCOUNT')) this.setTab('accounts');
    else if (action.includes('MAPPING')) this.setTab('mappings');
    else if (action.includes('PERIOD')) this.setTab('periods');
    else if (action.includes('RECONCILIATION')) this.setTab('reconciliation');
  }

  protected openAccount(): void { this.accountDraft = this.blankAccount(); this.accountFieldErrors = {}; this.accountError.set(null); this.accountFormOpen.set(true); }
  protected editAccount(account: AccountView): void {
    this.accountDraft = { id: account.id, code: account.code, nameFr: account.nameFr, nameEn: account.nameEn, accountType: account.accountType, normalSide: account.normalSide, currency: account.currency || 'XAF', parentId: account.parentId, postingAllowed: account.postingAllowed, active: account.active, effectiveFrom: account.effectiveFrom, effectiveTo: account.effectiveTo, version: account.version };
    this.accountFieldErrors = {}; this.accountError.set(null); this.accountFormOpen.set(true);
  }
  protected closeAccount(): void { this.accountFormOpen.set(false); this.accountError.set(null); }
  protected saveAccount(): void {
    this.accountFieldErrors = {};
    if (!this.accountDraft.code.trim()) this.accountFieldErrors['code'] = this.fr() ? 'Le code est obligatoire.' : 'Code is required.';
    if (!this.accountDraft.nameFr.trim()) this.accountFieldErrors['nameFr'] = this.fr() ? 'Le nom français est obligatoire.' : 'French name is required.';
    if (!this.accountDraft.nameEn.trim()) this.accountFieldErrors['nameEn'] = this.fr() ? 'Le nom anglais est obligatoire.' : 'English name is required.';
    if (!this.accountDraft.accountType) this.accountFieldErrors['accountType'] = this.fr() ? 'Choisissez un type.' : 'Choose a type.';
    if (Object.keys(this.accountFieldErrors).length) { this.accountError.set(this.fr() ? 'Corrigez les champs signalés.' : 'Correct the highlighted fields.'); return; }
    this.saving.set(true); const request: AccountUpsert = { code: this.accountDraft.code.trim(), nameFr: this.accountDraft.nameFr.trim(), nameEn: this.accountDraft.nameEn.trim(), accountType: this.accountDraft.accountType, normalSide: this.accountDraft.normalSide, currency: (this.accountDraft.currency || 'XAF').toUpperCase(), parentId: this.accountDraft.parentId, postingAllowed: this.accountDraft.postingAllowed, active: this.accountDraft.active, effectiveFrom: this.accountDraft.effectiveFrom, effectiveTo: this.accountDraft.effectiveTo, version: this.accountDraft.version };
    const call = this.accountDraft.id ? this.api.updateAccount(this.accountDraft.id, request) : this.api.createAccount(request);
    call.subscribe({ next: () => { this.saving.set(false); this.closeAccount(); this.success.set(this.fr() ? 'Compte enregistré.' : 'Account saved.'); this.loadAccounts(); this.api.readiness().subscribe({ next: value => this.readiness.set(value) }); }, error: err => { this.saving.set(false); this.applyError(err, this.accountFieldErrors, this.accountError); } });
  }

  protected saveRule(): void {
    if (!this.ruleDraft.eventType.trim() || !this.ruleDraft.targetAccountId) { this.error.set(this.fr() ? 'Le type et le compte cible sont obligatoires.' : 'Event type and target account are required.'); return; }
    this.saving.set(true); this.api.createPostingRule({ ...this.ruleDraft, eventType: this.ruleDraft.eventType.trim().toUpperCase() }).subscribe({ next: value => { this.saving.set(false); this.rules.update(rows => [value, ...rows]); this.ruleFormOpen.set(false); this.success.set(this.fr() ? 'Mapping enregistré.' : 'Mapping saved.'); }, error: err => { this.saving.set(false); this.error.set(this.message(err)); } });
  }

  protected generatePeriods(): void {
    if (!this.periodSessionId) { this.error.set(this.fr() ? 'Choisissez une session académique avant de générer les périodes.' : 'Choose an academic session before generating periods.'); return; }
    if (!this.periodStart || !this.periodEnd || this.periodEnd < this.periodStart) { this.error.set(this.fr() ? 'Vérifiez les dates de génération.' : 'Check the generation dates.'); return; }
    this.saving.set(true); this.api.generatePeriods({ startDate: this.periodStart, endDate: this.periodEnd, academicSessionId: this.periodSessionId }).subscribe({ next: value => { this.saving.set(false); this.periods.set(value); this.success.set(this.fr() ? 'Périodes générées et liées à la session.' : 'Periods generated and linked to the session.'); this.api.readiness().subscribe({ next: v => this.readiness.set(v) }); }, error: err => { this.saving.set(false); this.error.set(this.message(err)); } });
  }
  protected previewPeriod(period: PeriodView): void { this.api.closePreview(period.id).subscribe({ next: value => { this.closePreview.set(value); if (value.ready) this.openPeriodAction('close', period); }, error: err => this.error.set(this.message(err)) }); }
  protected openPeriodAction(kind: 'close' | 'reopen', period: PeriodView): void { this.actionReason = ''; this.actionDialog.set({ kind, period }); }
  protected confirmPeriodAction(): void {
    const dialog = this.actionDialog(); if (!dialog || !this.actionReason.trim()) { this.error.set(this.fr() ? 'Un motif est obligatoire.' : 'A reason is required.'); return; }
    this.saving.set(true); const call = dialog.kind === 'close' ? this.api.closePeriod(dialog.period.id, { version: dialog.period.version, reason: this.actionReason.trim() }) : this.api.reopenPeriod(dialog.period.id, { version: dialog.period.version, reason: this.actionReason.trim() });
    call.subscribe({ next: () => { this.saving.set(false); this.actionDialog.set(null); this.closePreview.set(null); this.success.set(dialog.kind === 'close' ? (this.fr() ? 'Période fermée.' : 'Period closed.') : (this.fr() ? 'Période rouverte.' : 'Period reopened.')); this.api.periods().subscribe({ next: v => this.periods.set(v) }); }, error: err => { this.saving.set(false); this.error.set(this.message(err)); } });
  }

  protected saveJournal(): void {
    this.journalError.set(null);
    if (!this.journalDraft.accountingPeriodId || !this.journalDraft.entryDate || !this.journalDraft.description.trim() || !this.journalDebitAccount || !this.journalCreditAccount || this.journalAmount <= 0) { this.journalError.set(this.fr() ? 'Période, date, libellé, comptes et montant sont obligatoires.' : 'Period, date, description, accounts and amount are required.'); return; }
    if (this.journalDebitAccount === this.journalCreditAccount) { this.journalError.set(this.fr() ? 'Le débit et le crédit doivent utiliser des comptes différents.' : 'Debit and credit must use different accounts.'); return; }
    this.saving.set(true); const request: JournalUpsert = { ...this.journalDraft, currency: 'XAF', lines: [{ accountId: this.journalDebitAccount, debitMinor: Number(this.journalAmount), creditMinor: 0, description: this.journalDraft.description }, { accountId: this.journalCreditAccount, debitMinor: 0, creditMinor: Number(this.journalAmount), description: this.journalDraft.description }] };
    this.api.createJournal(request).subscribe({ next: value => { this.saving.set(false); this.journalFormOpen.set(false); this.journals.update(rows => [value, ...rows]); this.success.set(this.fr() ? `Brouillon ${value.number} créé. Vérifiez puis postez-le.` : `Draft ${value.number} created. Review and post it.`); }, error: err => { this.saving.set(false); this.applyError(err, {}, this.journalError); } });
  }
  protected postJournal(journal: JournalView): void { this.api.postJournal(journal.id).subscribe({ next: value => { this.journals.update(rows => rows.map(row => row.id === value.id ? value : row)); this.success.set(this.fr() ? `${value.number} est posté et immuable.` : `${value.number} is posted and immutable.`); this.loadTrialBalance(); }, error: err => this.error.set(this.message(err)) }); }
  protected selectJournal(journal: JournalView): void { this.api.journal(journal.id).subscribe({ next: value => this.selectedJournal.set(value), error: err => this.error.set(this.message(err)) }); }
  protected openReverse(journal: JournalView): void { this.reverseReason = ''; this.reverseTarget.set(journal); }
  protected confirmReverse(): void { const journal = this.reverseTarget(); if (!journal || !this.reverseReason.trim()) { this.error.set(this.fr() ? 'Un motif est obligatoire.' : 'A reason is required.'); return; } this.api.reverseJournal(journal.id, { entryDate: today(), reason: this.reverseReason.trim(), version: journal.version }).subscribe({ next: value => { this.reverseTarget.set(null); this.journals.update(rows => [value, ...rows.map(row => row.id === journal.id ? { ...row, status: 'REVERSED' as const } : row)]); this.success.set(this.fr() ? `Renversement ${value.number} posté.` : `Reversal ${value.number} posted.`); }, error: err => this.error.set(this.message(err)) }); }

  protected openReconciliation(item: ReconciliationView): void { this.reconciliationReason = ''; this.reconciliationTarget.set(item); }
  protected resolveReconciliation(): void { const item = this.reconciliationTarget(); if (!item || !this.reconciliationReason.trim()) { this.error.set(this.fr() ? 'Un motif est obligatoire.' : 'A reason is required.'); return; } this.api.resolveReconciliation(item.id, { state: 'MATCHED', reason: this.reconciliationReason.trim(), version: item.version }).subscribe({ next: () => { this.reconciliationTarget.set(null); this.loadReconciliation(); this.success.set(this.fr() ? 'Exception résolue.' : 'Exception resolved.'); }, error: err => this.error.set(this.message(err)) }); }

  private blankAccount(): AccountUpsert & { id?: string } { return { code: '', nameFr: '', nameEn: '', accountType: 'ASSET', normalSide: 'DEBIT', currency: 'XAF', parentId: null, postingAllowed: true, active: true, effectiveFrom: null, effectiveTo: null }; }
  private blankRule(): PostingRuleUpsert { return { eventType: '', side: 'DEBIT', scopeCode: null, feeTypeCode: null, paymentChannelCode: null, componentCode: null, targetAccountId: '', priority: 0, effectiveFrom: null, effectiveTo: null, enabled: true }; }
  private blankJournal(): JournalUpsert { return { entryDate: today(), description: '', currency: 'XAF', accountingPeriodId: '', lines: [] }; }
  private applyError(err: unknown, fields: Record<string, string>, message: { set(value: string | null): void }): void { const body = (err as { error?: AccountingApiError })?.error; if (body?.fieldErrors) Object.assign(fields, body.fieldErrors); message.set(this.message(err)); }
  private message(err: unknown): string { const body = (err as { error?: AccountingApiError })?.error; return body?.message || (err as { message?: string })?.message || (this.fr() ? 'Opération impossible.' : 'Operation failed.'); }
}
