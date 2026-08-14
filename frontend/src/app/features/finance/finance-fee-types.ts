import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AccountView, FinanceAccountingApi } from './accounting.api';
import {
  FeeTypeActionRequest, FeeTypeComparison, FeeTypeCreateRequest, FeeTypeRevisionInput,
  FeeTypeRevisionView, FeeTypeUsageView, FeeTypeView, FeeTypesApi, LegacyFeeCandidate,
  LegacyMappingRequest, LegacyMappingRow, LegacyPreviewView,
} from './fee-types.api';

type FeeTab = 'catalogue' | 'legacy';
type FormMode = 'create' | 'edit' | 'revision';
type AccountKind = 'receivable' | 'revenue';

const blankRevision = (): FeeTypeRevisionInput => ({
  nameFr: '', nameEn: '', descriptionFr: null, descriptionEn: null,
  category: 'TUITION', defaultAmountMinor: 0, defaultCurrency: 'XAF', frequency: 'ONCE',
  mandatory: true, refundable: false, taxable: false, taxBasisPoints: 0,
  receivableAccountId: null, revenueAccountId: null, effectiveFrom: '', effectiveTo: null,
});

const blankForm = () => ({ code: '', typeId: null as string | null, typeVersion: 0, revision: blankRevision() });

@Component({
  selector: 'bbc-finance-fee-types',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fee-shell fade-in mx-auto max-w-7xl space-y-5">
      <header class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div class="mb-2 flex flex-wrap items-center gap-2 text-xs font-bold uppercase tracking-[0.16em] text-brand-600">
            <span>Finance</span><span class="text-slate-300">/</span><span>{{ fr() ? 'Catalogue des frais' : 'Fee type catalogue' }}</span>
            <span class="rounded-full border border-gold-200 bg-gold-50 px-2 py-0.5 text-[10px] text-gold-700">BAY-44 · V60</span>
          </div>
          <h1 class="text-2xl font-extrabold text-ink">{{ fr() ? 'Types de frais' : 'Fee types' }}</h1>
          <p class="mt-1 max-w-2xl text-sm text-slate-500">
            {{ fr() ? 'Un catalogue réutilisable, versionné et relié aux comptes comptables.' : 'A reusable, versioned catalogue connected to accounting accounts.' }}
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <a routerLink="/finance/accounting" class="btn-secondary">{{ fr() ? 'Comptes comptables' : 'Accounting accounts' }}</a>
          <button type="button" class="btn-primary" [disabled]="!canManage()" (click)="openCreate()">+ {{ fr() ? 'Nouveau type' : 'New fee type' }}</button>
        </div>
      </header>

      <nav class="fee-tabs flex gap-2 overflow-x-auto rounded-xl border border-slate-200 bg-white p-2 shadow-sm" aria-label="Fee catalogue sections">
        <button type="button" class="min-h-10 shrink-0 rounded-lg border px-4 text-sm font-bold" [class.bg-brand-700]="tab() === 'catalogue'" [class.text-white]="tab() === 'catalogue'" [class.border-brand-700]="tab() === 'catalogue'" [class.border-slate-200]="tab() !== 'catalogue'" [class.text-slate-600]="tab() !== 'catalogue'" (click)="setTab('catalogue')">
          {{ fr() ? 'Catalogue' : 'Catalogue' }} <span class="ml-1 rounded-full bg-white/20 px-2 py-0.5 text-xs">{{ feeTypes().length }}</span>
        </button>
        <button type="button" class="min-h-10 shrink-0 rounded-lg border px-4 text-sm font-bold" [class.bg-brand-700]="tab() === 'legacy'" [class.text-white]="tab() === 'legacy'" [class.border-brand-700]="tab() === 'legacy'" [class.border-slate-200]="tab() !== 'legacy'" [class.text-slate-600]="tab() !== 'legacy'" (click)="setTab('legacy')">
          {{ fr() ? 'Revue legacy' : 'Legacy review' }} <span class="ml-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">{{ legacyPreview()?.candidateCount ?? 0 }}</span>
        </button>
      </nav>

      @if (error()) {
        <div role="alert" class="flex flex-col gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 sm:flex-row sm:items-start sm:justify-between">
          <div><div class="font-bold">{{ error() }}</div>@if (correlationId()) {<div class="mt-1 text-xs">{{ fr() ? 'Identifiant de support' : 'Support correlation ID' }}: {{ correlationId() }}</div>}</div>
          <button type="button" class="font-bold underline" (click)="reload()">{{ fr() ? 'Réessayer' : 'Retry' }}</button>
        </div>
      }
      @if (success()) { <div role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">{{ success() }}</div> }
      @if (loading()) {
        <div class="grid gap-4 md:grid-cols-3" aria-label="Loading fee types"><div class="h-24 animate-pulse rounded-xl bg-slate-100"></div><div class="h-24 animate-pulse rounded-xl bg-slate-100"></div><div class="h-24 animate-pulse rounded-xl bg-slate-100"></div></div>
      } @else {
        @if (tab() === 'catalogue') {
          <section class="space-y-4">
            <div class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:flex-row lg:items-end lg:justify-between">
              <div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Catalogue réutilisable' : 'Reusable catalogue' }}</h2><p class="text-sm text-slate-500">{{ filteredTypes().length }} {{ fr() ? 'type(s) affiché(s)' : 'type(s) shown' }}</p></div>
              <div class="grid gap-2 sm:grid-cols-3">
                <label class="field-label">{{ fr() ? 'Rechercher' : 'Search' }}<input class="field min-w-52" [ngModel]="query()" (ngModelChange)="query.set($event)" placeholder="TUITION, transport..." aria-label="Search fee types"></label>
                <label class="field-label">{{ fr() ? 'Cycle de vie' : 'Lifecycle' }}<select class="field" [ngModel]="lifecycleFilter()" (ngModelChange)="lifecycleFilter.set($event)"><option value="">{{ fr() ? 'Tous' : 'All' }}</option><option value="DRAFT">{{ fr() ? 'Brouillon' : 'Draft' }}</option><option value="ACTIVE">{{ fr() ? 'Actif' : 'Active' }}</option><option value="INACTIVE">{{ fr() ? 'Inactif' : 'Inactive' }}</option></select></label>
                <label class="field-label">{{ fr() ? 'Catégorie' : 'Category' }}<select class="field" [ngModel]="categoryFilter()" (ngModelChange)="categoryFilter.set($event)"><option value="">{{ fr() ? 'Toutes' : 'All' }}</option>@for (category of categories; track category) {<option [value]="category">{{ category }}</option>}</select></label>
              </div>
            </div>

            @if (!filteredTypes().length) {
              <div class="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center shadow-sm">
                <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-2xl text-brand-700">₣</div>
                <h2 class="mt-4 text-lg font-extrabold text-ink">{{ query() || lifecycleFilter() || categoryFilter() ? (fr() ? 'Aucun résultat' : 'No matching fee types') : (fr() ? 'Votre catalogue est vide' : 'Your catalogue is empty') }}</h2>
                <p class="mx-auto mt-2 max-w-lg text-sm text-slate-500">{{ query() || lifecycleFilter() || categoryFilter() ? (fr() ? 'Modifiez les filtres ou réinitialisez la recherche.' : 'Change the filters or reset the search.') : (fr() ? 'Commencez par un type versionné et reliez-le à une créance et un produit.' : 'Start with a versioned fee type and connect it to receivable and revenue accounts.') }}</p>
                <div class="mt-5 flex flex-wrap justify-center gap-2"><button type="button" class="btn-primary" [disabled]="!canManage()" (click)="openCreate()">{{ fr() ? 'Créer le premier type de frais' : 'Create first fee type' }}</button>@if (legacyPreview()?.candidateCount) {<button type="button" class="btn-secondary" (click)="setTab('legacy')">{{ fr() ? 'Revoir les éléments legacy' : 'Review legacy fee items' }}</button>}</div>
              </div>
            } @else {
              <div class="fee-table hidden overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm md:block">
                <table class="w-full min-w-[1180px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">{{ fr() ? 'Code / nom' : 'Code / name' }}</th><th class="px-4 py-3">{{ fr() ? 'Catégorie' : 'Category' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Montant par défaut' : 'Default amount' }}</th><th class="px-4 py-3">{{ fr() ? 'Règles' : 'Rules' }}</th><th class="px-4 py-3">{{ fr() ? 'Comptes' : 'Accounts' }}</th><th class="px-4 py-3">{{ fr() ? 'Cycle / effet' : 'Lifecycle / effective' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Usage' : 'Usage' }}</th><th class="px-4 py-3 text-right">{{ fr() ? 'Action' : 'Action' }}</th></tr></thead><tbody>@for (item of filteredTypes(); track item.id) {<tr class="border-t border-slate-100 align-top hover:bg-slate-50"><td class="px-4 py-4"><div class="font-mono text-xs font-extrabold text-brand-700">{{ item.code }}</div><div class="mt-1 font-bold text-ink">{{ name(item) }}</div><div class="text-xs text-slate-500">{{ otherName(item) }} · Rev {{ item.currentRevisionNo ?? '—' }}</div></td><td class="px-4 py-4"><span class="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold">{{ current(item)?.category || '—' }}</span></td><td class="px-4 py-4 text-right font-extrabold">{{ money(current(item)?.defaultAmountMinor ?? 0, current(item)?.defaultCurrency ?? 'XAF') }}</td><td class="px-4 py-4 text-xs text-slate-600"><div>{{ current(item)?.mandatory ? (fr() ? 'Obligatoire' : 'Mandatory') : (fr() ? 'Optionnel' : 'Optional') }}</div><div>{{ current(item)?.refundable ? (fr() ? 'Remboursable' : 'Refundable') : '' }}{{ current(item)?.taxable ? ' · TAX' : '' }}</div></td><td class="px-4 py-4 text-xs"><div>{{ accountLabel(current(item)?.receivableAccount) }}</div><div>{{ accountLabel(current(item)?.revenueAccount) }}</div></td><td class="px-4 py-4"><span class="rounded-full px-2 py-1 text-xs font-extrabold" [class]="lifecycleClass(item.lifecycle)">{{ lifecycleLabel(item.lifecycle) }}</span><div class="mt-2 text-xs text-slate-500">{{ effectiveLabel(item) }}</div></td><td class="px-4 py-4 text-right font-bold">{{ item.usageCount }}</td><td class="px-4 py-4 text-right"><div class="flex min-w-28 flex-col items-end gap-1"><button type="button" class="text-xs font-extrabold text-brand-700 underline" (click)="openEdit(item)">{{ item.lifecycle === 'ACTIVE' ? (fr() ? 'Nouvelle révision' : 'New revision') : (fr() ? 'Modifier' : 'Edit') }}</button>@if (item.lifecycle === 'DRAFT') {<button type="button" class="text-xs font-extrabold text-emerald-700 underline" [disabled]="!canManage()" (click)="openActivate(item)">{{ fr() ? 'Activer' : 'Activate' }}</button>} @if (item.lifecycle === 'ACTIVE') {<button type="button" class="text-xs font-extrabold text-rose-700 underline" [disabled]="!canManage()" (click)="openDeactivate(item)">{{ fr() ? 'Désactiver' : 'Deactivate' }}</button>} @if (item.revisions.length > 1) {<button type="button" class="text-xs font-extrabold text-slate-600 underline" (click)="openCompare(item)">{{ fr() ? 'Comparer' : 'Compare' }}</button>}</div></td></tr>}</tbody></table>
              </div>
              <div class="grid gap-3 md:hidden">@for (item of filteredTypes(); track item.id) {<article class="fee-card rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div class="flex items-start justify-between gap-3"><div><div class="font-mono text-xs font-extrabold text-brand-700">{{ item.code }}</div><h3 class="mt-1 font-extrabold text-ink">{{ name(item) }}</h3><p class="text-xs text-slate-500">{{ otherName(item) }} · Rev {{ item.currentRevisionNo ?? '—' }}</p></div><span class="rounded-full px-2 py-1 text-xs font-extrabold" [class]="lifecycleClass(item.lifecycle)">{{ lifecycleLabel(item.lifecycle) }}</span></div><div class="mt-4 grid grid-cols-2 gap-3 text-sm"><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Montant' : 'Amount' }}</div><div class="mt-1 font-extrabold">{{ money(current(item)?.defaultAmountMinor ?? 0, current(item)?.defaultCurrency ?? 'XAF') }}</div></div><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Usage' : 'Usage' }}</div><div class="mt-1 font-extrabold">{{ item.usageCount }}</div></div></div><div class="mt-3 text-xs text-slate-600">{{ accountLabel(current(item)?.receivableAccount) }} · {{ accountLabel(current(item)?.revenueAccount) }}</div><div class="mt-4 flex flex-wrap gap-2"><button type="button" class="btn-secondary" (click)="openEdit(item)">{{ item.lifecycle === 'ACTIVE' ? (fr() ? 'Nouvelle révision' : 'New revision') : (fr() ? 'Modifier' : 'Edit') }}</button>@if (item.lifecycle === 'DRAFT') {<button type="button" class="btn-primary" [disabled]="!canManage()" (click)="openActivate(item)">{{ fr() ? 'Activer' : 'Activate' }}</button>} @if (item.lifecycle === 'ACTIVE') {<button type="button" class="btn-secondary" [disabled]="!canManage()" (click)="openDeactivate(item)">{{ fr() ? 'Désactiver' : 'Deactivate' }}</button>} </div></article>}</div>
            }
          </section>
        } @else {
          <section class="space-y-4">
            <div class="flex flex-col gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-5 sm:flex-row sm:items-center sm:justify-between"><div><h2 class="text-lg font-extrabold text-ink">{{ fr() ? 'Revue des éléments legacy' : 'Review legacy fee items' }}</h2><p class="mt-1 text-sm text-slate-700">{{ fr() ? 'Aucune ligne n’est activée automatiquement. Validez chaque proposition ou laissez-la en rapprochement.' : 'Nothing is activated automatically. Approve each proposal or leave it in reconciliation.' }}</p></div><button type="button" class="btn-secondary" (click)="loadLegacy()">{{ fr() ? 'Actualiser l’extraction' : 'Refresh extraction' }}</button></div>
            @if (!legacyPreview()?.candidateCount) {<div class="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center text-sm text-slate-500">{{ fr() ? 'Aucun élément fee_config.items à revoir.' : 'No fee_config.items candidates found.' }}</div>} @else {<div class="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm"><table class="w-full min-w-[980px] text-sm"><thead class="bg-slate-50 text-left text-[11px] uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">{{ fr() ? 'Source / nom' : 'Source / name' }}</th><th class="px-4 py-3">{{ fr() ? 'Suggestion' : 'Suggestion' }}</th><th class="px-4 py-3">{{ fr() ? 'Décision' : 'Decision' }}</th><th class="px-4 py-3">{{ fr() ? 'Code accepté' : 'Accepted code' }}</th><th class="px-4 py-3">{{ fr() ? 'Nom FR / EN' : 'FR / EN name' }}</th></tr></thead><tbody>@for (candidate of legacyPreview()!.candidates; track candidate.sourceKey; let i = $index) {<tr class="border-t border-slate-100 align-top"><td class="px-4 py-4"><div class="font-mono text-xs text-brand-700">{{ candidate.sourceKey }}</div><div class="mt-1 font-bold">{{ candidate.rawName || '(empty)' }}</div><div class="text-xs text-slate-500">{{ candidate.level }} · {{ money(candidate.amountMinor, candidate.currency) }}</div></td><td class="px-4 py-4"><div class="font-mono text-xs font-bold">{{ candidate.suggestedCode }}</div><div class="text-xs text-slate-500">{{ candidate.reviewReason || (fr() ? 'Proposition à vérifier' : 'Suggested; review') }}</div></td><td class="px-4 py-4"><label class="inline-flex min-h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-xs font-bold"><input type="checkbox" [ngModel]="legacyRows()[i].accept" (ngModelChange)="updateLegacyRow(i, { accept: $event })"> {{ legacyRows()[i].accept ? (fr() ? 'Accepter' : 'Accept') : (fr() ? 'Rapprochement' : 'Reconciliation') }}</label></td><td class="px-4 py-4"><input class="field" [ngModel]="legacyRows()[i].code" (ngModelChange)="updateLegacyRow(i, { code: $event })" [class.input-error]="candidate.ambiguous && legacyRows()[i].accept && !legacyRows()[i].code" aria-label="Accepted fee type code"></td><td class="px-4 py-4"><div class="grid gap-2 sm:grid-cols-2"><input class="field" [ngModel]="legacyRows()[i].nameFr" (ngModelChange)="updateLegacyRow(i, { nameFr: $event })" placeholder="Français"><input class="field" [ngModel]="legacyRows()[i].nameEn" (ngModelChange)="updateLegacyRow(i, { nameEn: $event })" placeholder="English"></div></td></tr>}</tbody></table></div><div class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4"><p class="text-sm text-slate-600">{{ acceptedLegacyCount() }} {{ fr() ? 'accepté(s)' : 'accepted' }} · {{ (legacyPreview()?.candidateCount ?? 0) - acceptedLegacyCount() }} {{ fr() ? 'vers le rapprochement' : 'to reconciliation' }}</p><button type="button" class="btn-primary" [disabled]="!canManage() || legacySaving()" (click)="migrateLegacy()">{{ legacySaving() ? (fr() ? 'Enregistrement…' : 'Saving…') : (fr() ? 'Enregistrer la revue' : 'Save review') }}</button></div>}
          </section>
        }
      }
    </div>

    @if (drawerOpen()) {
      <div class="fee-drawer-backdrop" role="presentation" (click)="closeDrawer()">
        <aside class="fee-drawer" role="dialog" aria-modal="true" aria-labelledby="fee-drawer-title" (click)="$event.stopPropagation()">
          <div class="flex items-start justify-between gap-4 border-b border-slate-200 pb-4"><div><div class="text-xs font-bold uppercase tracking-wide text-brand-600">{{ mode() === 'create' ? (fr() ? 'Nouveau catalogue' : 'New catalogue item') : mode() === 'revision' ? (fr() ? 'Nouvelle révision' : 'New revision') : (fr() ? 'Brouillon' : 'Draft') }}</div><h2 id="fee-drawer-title" class="mt-1 text-xl font-extrabold text-ink">{{ fr() ? 'Détails du type de frais' : 'Fee type details' }}</h2><p class="mt-1 text-xs text-slate-500">* {{ fr() ? 'Champ obligatoire' : 'Required field' }}</p></div><button type="button" class="btn-secondary" (click)="closeDrawer()" aria-label="Close">×</button></div>
          @if (formError()) {<div role="alert" class="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800"><div class="font-bold">{{ formError() }}</div>@if (formCorrelationId()) {<div class="mt-1 text-xs">{{ formCorrelationId() }}</div>}</div>}
          <div class="mt-5 space-y-4">
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '1. Identité' : '1. Identity' }}</h3><div class="mt-3 grid gap-4 sm:grid-cols-2"><label class="field-label">Code *<input class="field" [class.input-error]="fieldErrors()['code']" [ngModel]="form().code" (ngModelChange)="updateCode($event)" placeholder="TUITION_TERM" [readonly]="mode() === 'revision'"><span class="field-help">{{ fr() ? 'Lettres, chiffres et underscores; normalisé en majuscules.' : 'Letters, numbers and underscores; normalized to uppercase.' }}</span>@if (fieldErrors()['code']) {<span class="field-error">{{ fieldErrors()['code'] }}</span>}</label><label class="field-label">{{ fr() ? 'Catégorie' : 'Category' }} *<select class="field" [class.input-error]="fieldErrors()['category']" [ngModel]="form().revision.category" (ngModelChange)="updateRevision({ category: $event })"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (category of categories; track category) {<option [value]="category">{{ category }}</option>}</select>@if (fieldErrors()['category']) {<span class="field-error">{{ fieldErrors()['category'] }}</span>}</label><label class="field-label">{{ fr() ? 'Nom français' : 'French name' }} *<input class="field" [class.input-error]="fieldErrors()['nameFr']" [ngModel]="form().revision.nameFr" (ngModelChange)="updateRevision({ nameFr: $event })">@if (fieldErrors()['nameFr']) {<span class="field-error">{{ fieldErrors()['nameFr'] }}</span>}</label><label class="field-label">English name *<input class="field" [class.input-error]="fieldErrors()['nameEn']" [ngModel]="form().revision.nameEn" (ngModelChange)="updateRevision({ nameEn: $event })">@if (fieldErrors()['nameEn']) {<span class="field-error">{{ fieldErrors()['nameEn'] }}</span>}</label><label class="field-label">{{ fr() ? 'Description française' : 'French description' }}<textarea class="field" rows="2" [ngModel]="form().revision.descriptionFr" (ngModelChange)="updateRevision({ descriptionFr: $event })"></textarea></label><label class="field-label">English description<textarea class="field" rows="2" [ngModel]="form().revision.descriptionEn" (ngModelChange)="updateRevision({ descriptionEn: $event })"></textarea></label></div></section>
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '2. Tarification' : '2. Pricing' }}</h3><div class="mt-3 grid gap-4 sm:grid-cols-3"><label class="field-label">{{ fr() ? 'Montant par défaut' : 'Default amount' }} *<input class="field" type="number" min="0" step="1" [class.input-error]="fieldErrors()['defaultAmountMinor']" [ngModel]="form().revision.defaultAmountMinor" (ngModelChange)="updateRevision({ defaultAmountMinor: +$event })">@if (fieldErrors()['defaultAmountMinor']) {<span class="field-error">{{ fieldErrors()['defaultAmountMinor'] }}</span>}</label><label class="field-label">{{ fr() ? 'Devise' : 'Currency' }} *<input class="field" maxlength="3" [class.input-error]="fieldErrors()['defaultCurrency']" [ngModel]="form().revision.defaultCurrency" (ngModelChange)="updateRevision({ defaultCurrency: $event.toUpperCase() })">@if (fieldErrors()['defaultCurrency']) {<span class="field-error">{{ fieldErrors()['defaultCurrency'] }}</span>}</label><label class="field-label">{{ fr() ? 'Fréquence' : 'Frequency' }} *<select class="field" [ngModel]="form().revision.frequency" (ngModelChange)="updateRevision({ frequency: $event })"><option value="ONCE">{{ fr() ? 'Une fois' : 'Once' }}</option><option value="MONTHLY">{{ fr() ? 'Mensuelle' : 'Monthly' }}</option><option value="TERM">{{ fr() ? 'Par trimestre' : 'Per term' }}</option><option value="ANNUAL">{{ fr() ? 'Annuelle' : 'Annual' }}</option></select></label></div></section>
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '3. Règles' : '3. Rules' }}</h3><div class="mt-3 grid gap-3 sm:grid-cols-3"><label class="inline-flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-bold"><input type="checkbox" [ngModel]="form().revision.mandatory" (ngModelChange)="updateRevision({ mandatory: $event })"> {{ fr() ? 'Obligatoire' : 'Mandatory' }}</label><label class="inline-flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-bold"><input type="checkbox" [ngModel]="form().revision.refundable" (ngModelChange)="updateRevision({ refundable: $event })"> {{ fr() ? 'Remboursable' : 'Refundable' }}</label><label class="inline-flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-bold"><input type="checkbox" [ngModel]="form().revision.taxable" (ngModelChange)="updateRevision({ taxable: $event })"> {{ fr() ? 'Taxable' : 'Taxable' }}</label><label class="field-label sm:col-span-3">{{ fr() ? 'Taxe (points de base)' : 'Tax basis points' }}<input class="field" type="number" min="0" max="10000" step="1" [ngModel]="form().revision.taxBasisPoints" (ngModelChange)="updateRevision({ taxBasisPoints: +$event })"><span class="field-help">{{ fr() ? '0 = aucune taxe; 10000 = 100%.' : '0 = no tax; 10000 = 100%.' }}</span></label></div></section>
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '4. Comptabilité' : '4. Accounting' }}</h3><p class="mt-1 text-xs text-slate-500">{{ fr() ? 'Les sélecteurs restent utilisables au clavier. Les comptes incompatibles sont désactivés et expliqués.' : 'Selectors are keyboard accessible. Incompatible accounts are disabled and explained.' }}</p><div class="mt-3 grid gap-4 sm:grid-cols-2"><label class="field-label">{{ fr() ? 'Compte de créance' : 'Receivable account' }} *<select class="field" [class.input-error]="fieldErrors()['receivableAccountId']" [ngModel]="form().revision.receivableAccountId" (ngModelChange)="updateRevision({ receivableAccountId: $event || null })"><option value="">{{ fr() ? 'Choisir un compte ASSET' : 'Choose an ASSET account' }}</option>@for (account of accounts(); track account.id) {<option [value]="account.id" [disabled]="!accountCompatible(account, 'receivable')">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }} · {{ account.accountType }}{{ accountCompatible(account, 'receivable') ? '' : ' — incompatible' }}</option>}</select><span class="field-help">{{ accountHint('receivable') }}</span>@if (fieldErrors()['receivableAccountId']) {<span class="field-error">{{ fieldErrors()['receivableAccountId'] }}</span>}</label><label class="field-label">{{ fr() ? 'Compte de produit' : 'Revenue account' }} *<select class="field" [class.input-error]="fieldErrors()['revenueAccountId']" [ngModel]="form().revision.revenueAccountId" (ngModelChange)="updateRevision({ revenueAccountId: $event || null })"><option value="">{{ fr() ? 'Choisir un compte REVENUE' : 'Choose a REVENUE account' }}</option>@for (account of accounts(); track account.id) {<option [value]="account.id" [disabled]="!accountCompatible(account, 'revenue')">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }} · {{ account.accountType }}{{ accountCompatible(account, 'revenue') ? '' : ' — incompatible' }}</option>}</select><span class="field-help">{{ accountHint('revenue') }}</span>@if (fieldErrors()['revenueAccountId']) {<span class="field-error">{{ fieldErrors()['revenueAccountId'] }}</span>}</label></div></section>
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '5. Dates d’effet' : '5. Effective dates' }}</h3><div class="mt-3 grid gap-4 sm:grid-cols-2"><label class="field-label">{{ fr() ? 'Effective à partir du' : 'Effective from' }}<input class="field" type="date" [class.input-error]="fieldErrors()['effectiveFrom']" [ngModel]="form().revision.effectiveFrom" (ngModelChange)="updateRevision({ effectiveFrom: $event })">@if (fieldErrors()['effectiveFrom']) {<span class="field-error">{{ fieldErrors()['effectiveFrom'] }}</span>}</label><label class="field-label">{{ fr() ? 'Effective jusqu’au' : 'Effective to' }}<input class="field" type="date" [class.input-error]="fieldErrors()['effectiveTo']" [ngModel]="form().revision.effectiveTo" (ngModelChange)="updateRevision({ effectiveTo: $event || null })">@if (fieldErrors()['effectiveTo']) {<span class="field-error">{{ fieldErrors()['effectiveTo'] }}</span>}</label></div></section>
            <section class="fee-section"><h3 class="font-extrabold text-ink">{{ fr() ? '6. Revue' : '6. Review' }}</h3><div class="grid gap-2 text-sm sm:grid-cols-2"><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Disponible comme' : 'Available as' }}</div><div class="mt-1 font-extrabold">{{ form().revision.nameFr || '—' }} · {{ form().revision.category || '—' }}</div></div><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Effet comptable' : 'Accounting effect' }}</div><div class="mt-1 font-extrabold">{{ money(form().revision.defaultAmountMinor, form().revision.defaultCurrency) }} · DR {{ selectedAccountCode('receivable') || '—' }} / CR {{ selectedAccountCode('revenue') || '—' }}</div></div></div><p class="mt-3 text-xs text-slate-600">{{ mode() === 'revision' ? (fr() ? 'La révision active reste immuable; cette sauvegarde créera un brouillon.' : 'The active revision remains immutable; this save creates a draft.') : (fr() ? 'L’activation rend cette révision disponible aux futurs plans, sans modifier les données legacy.' : 'Activation makes this revision available to future plans without changing legacy data.') }}</p></section>
          </div>
          <div class="mt-5 flex flex-wrap justify-end gap-2 border-t border-slate-200 pt-4"><button type="button" class="btn-secondary" (click)="closeDrawer()">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="saving() || !canManage()" (click)="saveForm()">{{ saving() ? (fr() ? 'Enregistrement…' : 'Saving…') : (mode() === 'create' ? (fr() ? 'Créer le brouillon' : 'Create draft') : mode() === 'revision' ? (fr() ? 'Créer la révision' : 'Create revision') : (fr() ? 'Enregistrer le brouillon' : 'Save draft')) }}</button></div>
        </aside>
      </div>
    }

    @if (activateTarget(); as target) {
      <div class="fee-modal-backdrop" role="presentation" (click)="closeActionModal()"><section class="fee-modal rounded-2xl border border-slate-200 bg-white p-5 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="activate-title" (click)="$event.stopPropagation()"><div class="flex items-start justify-between gap-3"><div><div class="text-xs font-bold uppercase tracking-wide text-emerald-700">{{ fr() ? 'Revue d’activation' : 'Activation review' }}</div><h2 id="activate-title" class="mt-1 text-xl font-extrabold text-ink">{{ target.code }}</h2></div><button type="button" class="btn-secondary" (click)="closeActionModal()">×</button></div><div class="mt-4 grid gap-3 sm:grid-cols-2"><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Ce qui devient disponible' : 'What becomes available' }}</div><div class="mt-1 font-bold">{{ name(target) }} · {{ money(current(target)?.defaultAmountMinor ?? 0, current(target)?.defaultCurrency ?? 'XAF') }}</div><div class="mt-1 text-xs text-slate-500">{{ current(target)?.category }} · {{ current(target)?.frequency }}</div></div><div class="detail-field"><div class="text-[10px] font-bold uppercase text-slate-500">{{ fr() ? 'Mappages' : 'Mappings' }}</div><div class="mt-1 text-xs font-bold">DR {{ accountLabel(current(target)?.receivableAccount) }}</div><div class="text-xs font-bold">CR {{ accountLabel(current(target)?.revenueAccount) }}</div></div></div>@if (activationBlockers(target).length) {<div class="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><div class="font-extrabold">{{ fr() ? 'Blocages à corriger' : 'Blockers to fix' }}</div><ul class="mt-2 list-disc space-y-1 pl-5">@for (blocker of activationBlockers(target); track blocker) {<li>{{ blocker }}</li>}</ul></div>}<label class="field-label mt-4">{{ fr() ? 'Note de revue' : 'Review note' }}<textarea class="field" rows="2" [(ngModel)]="actionReason" placeholder="Reviewed by finance manager"></textarea></label><div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="closeActionModal()">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="!canManage() || activationBlockers(target).length > 0 || actionSaving()" (click)="activate()">{{ actionSaving() ? (fr() ? 'Activation…' : 'Activating…') : (fr() ? 'Activer la révision' : 'Activate revision') }}</button></div></section></div>
    }

    @if (deactivateTarget(); as target) {
      <div class="fee-modal-backdrop" role="presentation" (click)="closeActionModal()"><section class="fee-modal rounded-2xl border border-slate-200 bg-white p-5 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="deactivate-title" (click)="$event.stopPropagation()"><div class="flex items-start justify-between gap-3"><div><div class="text-xs font-bold uppercase tracking-wide text-rose-700">{{ fr() ? 'Désactivation contrôlée' : 'Controlled deactivation' }}</div><h2 id="deactivate-title" class="mt-1 text-xl font-extrabold text-ink">{{ target.code }}</h2></div><button type="button" class="btn-secondary" (click)="closeActionModal()">×</button></div>@if (usageLoading()) {<div class="mt-5 h-20 animate-pulse rounded-xl bg-slate-100"></div>} @else {<p class="mt-4 text-sm text-slate-700">{{ fr() ? 'La désactivation conserve l’identité et les révisions historiques. Les dépendances actives doivent d’abord être traitées.' : 'Deactivation preserves the identity and historical revisions. Active dependencies must be handled first.' }}</p>@if (usage()?.dependencies?.length) {<div class="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900"><div class="font-extrabold">{{ fr() ? 'Dépendances bloquantes' : 'Blocking dependencies' }}</div><ul class="mt-2 space-y-2">@for (dependency of usage()!.dependencies; track dependency.entityType + dependency.entityId) {<li class="rounded-lg border border-rose-200 bg-white p-3"><div class="font-bold">{{ dependency.label }}</div><div class="text-xs">{{ dependency.entityType }} · {{ dependency.status || '—' }} · {{ dependency.detail }}</div>@if (dependency.sessionLabel || dependency.classLabel) {<div class="mt-1 text-xs font-semibold">{{ dependency.sessionLabel || '' }}{{ dependency.classLabel ? ' · ' + dependency.classLabel : '' }}</div>}</li>}</ul></div>} @else {<div class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">{{ fr() ? 'Aucun plan ou charge actif ne bloque cette désactivation.' : 'No active plan or charge blocks this deactivation.' }}</div>}<label class="field-label mt-4">{{ fr() ? 'Motif' : 'Reason' }}<textarea class="field" rows="2" [(ngModel)]="actionReason" placeholder="No longer offered"></textarea></label><div class="mt-4 flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="closeActionModal()">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="!canManage() || !!usage()?.dependencies?.length || actionSaving()" (click)="deactivate()">{{ actionSaving() ? (fr() ? 'Désactivation…' : 'Deactivating…') : (fr() ? 'Désactiver stablement' : 'Deactivate stably') }}</button></div>}</section></div>
    }

    @if (comparison(); as comparison) {
      <div class="fee-modal-backdrop" role="presentation" (click)="closeComparison()"><section class="fee-modal rounded-2xl border border-slate-200 bg-white p-5 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="comparison-title" (click)="$event.stopPropagation()"><div class="flex items-start justify-between gap-3"><div><div class="text-xs font-bold uppercase tracking-wide text-slate-500">{{ fr() ? 'Comparaison de révisions' : 'Revision comparison' }}</div><h2 id="comparison-title" class="mt-1 text-xl font-extrabold text-ink">{{ comparison.code }}</h2></div><button type="button" class="btn-secondary" (click)="closeComparison()">×</button></div>@if (!comparison.differences.length) {<div class="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{{ fr() ? 'Les révisions sont identiques.' : 'The revisions are identical.' }}</div>} @else {<div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full text-sm"><thead class="bg-slate-50 text-left text-xs text-slate-500"><tr><th class="px-3 py-2">{{ fr() ? 'Champ' : 'Field' }}</th><th class="px-3 py-2">Rev {{ comparison.leftRevision }}</th><th class="px-3 py-2">Rev {{ comparison.rightRevision }}</th></tr></thead><tbody>@for (change of comparison.differences; track change.field) {<tr class="border-t border-slate-100"><td class="px-3 py-2 font-bold">{{ change.field }}</td><td class="px-3 py-2">{{ change.leftValue }}</td><td class="px-3 py-2">{{ change.rightValue }}</td></tr>}</tbody></table></div>}</section></div>
    }
  `,
  styleUrl: './finance-fee-types.scss',
})
export class FinanceFeeTypesComponent {
  protected feeApi = inject(FeeTypesApi);
  protected accountingApi = inject(FinanceAccountingApi);
  protected auth = inject(AuthService);
  protected i18n = inject(I18nService);

  protected fr = () => this.i18n.lang() === 'fr';
  protected categories = ['TUITION', 'REGISTRATION', 'TRANSPORT', 'EXAM', 'UNIFORM', 'OTHER'];
  protected tab = signal<FeeTab>('catalogue');
  protected loading = signal(false);
  protected saving = signal(false);
  protected actionSaving = signal(false);
  protected legacySaving = signal(false);
  protected usageLoading = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected formError = signal<string | null>(null);
  protected correlationId = signal<string | null>(null);
  protected formCorrelationId = signal<string | null>(null);
  protected fieldErrors = signal<Record<string, string>>({});
  protected query = signal('');
  protected lifecycleFilter = signal('');
  protected categoryFilter = signal('');
  protected feeTypes = signal<FeeTypeView[]>([]);
  protected accounts = signal<AccountView[]>([]);
  protected legacyPreview = signal<LegacyPreviewView | null>(null);
  protected legacyRows = signal<LegacyMappingRow[]>([]);
  protected drawerOpen = signal(false);
  protected mode = signal<FormMode>('create');
  protected form = signal(blankForm());
  protected activateTarget = signal<FeeTypeView | null>(null);
  protected deactivateTarget = signal<FeeTypeView | null>(null);
  protected usage = signal<FeeTypeUsageView | null>(null);
  protected comparison = signal<FeeTypeComparison | null>(null);
  protected actionReason = '';

  constructor() { this.reload(); }

  protected canManage(): boolean { return this.auth.can('finance', 'write'); }
  protected setTab(tab: FeeTab): void { this.tab.set(tab); this.error.set(null); this.success.set(null); if (tab === 'legacy' && !this.legacyPreview()) this.loadLegacy(); }
  protected filteredTypes(): FeeTypeView[] {
    const q = this.query().trim().toLowerCase();
    const lifecycle = this.lifecycleFilter();
    const category = this.categoryFilter();
    return this.feeTypes().filter((item) => (!lifecycle || item.lifecycle === lifecycle)
      && (!category || this.current(item)?.category === category)
      && (!q || item.code.toLowerCase().includes(q) || this.name(item).toLowerCase().includes(q) || this.otherName(item).toLowerCase().includes(q)));
  }
  protected current(item: FeeTypeView | null): FeeTypeRevisionView | null { return item?.currentRevision ?? null; }
  protected name(item: FeeTypeView): string { return item.currentRevision?.nameFr || item.currentRevision?.nameEn || item.code; }
  protected otherName(item: FeeTypeView): string { return item.currentRevision?.nameEn || item.currentRevision?.nameFr || item.code; }
  protected money(amount: number, currency: string): string { return `${Math.round(Number(amount) || 0).toLocaleString('fr-FR')} ${currency || 'XAF'}`; }
  protected accountLabel(account: FeeTypeRevisionView['receivableAccount'] | undefined): string { return account ? `${account.code} · ${this.fr() ? account.nameFr : account.nameEn}` : '—'; }
  protected lifecycleLabel(lifecycle: string): string { return lifecycle === 'ACTIVE' ? (this.fr() ? 'Actif' : 'Active') : lifecycle === 'INACTIVE' ? (this.fr() ? 'Inactif' : 'Inactive') : (this.fr() ? 'Brouillon' : 'Draft'); }
  protected lifecycleClass(lifecycle: string): string { return lifecycle === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : lifecycle === 'INACTIVE' ? 'bg-slate-200 text-slate-600' : 'bg-amber-100 text-amber-800'; }
  protected effectiveLabel(item: FeeTypeView): string { const status = item.effectiveStatus; return status === 'EFFECTIVE' ? (this.fr() ? 'Effectif aujourd’hui' : 'Effective today') : status === 'NOT_YET_EFFECTIVE' ? (this.fr() ? 'À venir' : 'Effective later') : status === 'EXPIRED' ? (this.fr() ? 'Expiré' : 'Expired') : status; }
  protected acceptedLegacyCount(): number { return this.legacyRows().filter((row) => row.accept).length; }

  protected reload(): void {
    this.loading.set(true); this.error.set(null);
    forkJoin({ feeTypes: this.feeApi.list(), accounts: this.accountingApi.accounts(), legacy: this.feeApi.legacyPreview() }).subscribe({
      next: (value) => { this.feeTypes.set(value.feeTypes); this.accounts.set(value.accounts); this.setLegacyPreview(value.legacy); this.loading.set(false); },
      error: (err) => { this.loading.set(false); this.applyError(err, false); },
    });
  }
  protected loadLegacy(): void { this.feeApi.legacyPreview().subscribe({ next: (value) => this.setLegacyPreview(value), error: (err) => this.applyError(err, false) }); }
  private setLegacyPreview(value: LegacyPreviewView): void {
    this.legacyPreview.set(value);
    const previous = new Map(this.legacyRows().map((row) => [row.sourceKey, row]));
    this.legacyRows.set(value.candidates.map((candidate) => previous.get(candidate.sourceKey) ?? ({ sourceKey: candidate.sourceKey, accept: false, feeTypeId: null, code: candidate.suggestedCode, nameFr: candidate.suggestedNameFr, nameEn: candidate.suggestedNameEn, category: candidate.category })));
  }
  protected openCreate(): void { this.mode.set('create'); this.form.set(blankForm()); this.openDrawerState(); }
  protected openEdit(item: FeeTypeView): void {
    const revision = item.currentRevision ?? item.revisions[0];
    if (!revision) return;
    this.mode.set(item.lifecycle === 'ACTIVE' ? 'revision' : 'edit');
    this.form.set({ code: item.code, typeId: item.id, typeVersion: item.version, revision: this.revisionInput(revision) });
    this.openDrawerState();
  }
  private revisionInput(revision: FeeTypeRevisionView): FeeTypeRevisionInput { return { nameFr: revision.nameFr, nameEn: revision.nameEn, descriptionFr: revision.descriptionFr, descriptionEn: revision.descriptionEn, category: revision.category, defaultAmountMinor: revision.defaultAmountMinor, defaultCurrency: revision.defaultCurrency, frequency: revision.frequency, mandatory: revision.mandatory, refundable: revision.refundable, taxable: revision.taxable, taxBasisPoints: revision.taxBasisPoints, receivableAccountId: revision.receivableAccountId, revenueAccountId: revision.revenueAccountId, effectiveFrom: revision.effectiveFrom, effectiveTo: revision.effectiveTo, version: revision.version }; }
  private openDrawerState(): void { this.drawerOpen.set(true); this.formError.set(null); this.formCorrelationId.set(null); this.fieldErrors.set({}); this.success.set(null); }
  protected closeDrawer(): void { this.drawerOpen.set(false); }
  protected updateCode(code: string): void { this.form.update((current) => ({ ...current, code })); }
  protected updateRevision(patch: Partial<FeeTypeRevisionInput>): void { this.form.update((current) => ({ ...current, revision: { ...current.revision, ...patch } })); }
  protected accountCompatible(account: AccountView, kind: AccountKind): boolean { const revision = this.form().revision; const expected = kind === 'receivable' ? 'ASSET' : 'REVENUE'; return account.accountType === expected && account.active && account.postingAllowed && (!account.currency || account.currency === revision.defaultCurrency); }
  protected accountHint(kind: AccountKind): string { const expected = kind === 'receivable' ? 'ASSET' : 'REVENUE'; return this.fr() ? `Compte ${expected}, actif, mouvement et compatible avec ${this.form().revision.defaultCurrency}.` : `${expected} account, active, postable and compatible with ${this.form().revision.defaultCurrency}.`; }
  protected selectedAccountCode(kind: AccountKind): string { const id = kind === 'receivable' ? this.form().revision.receivableAccountId : this.form().revision.revenueAccountId; return this.accounts().find((account) => account.id === id)?.code ?? ''; }

  protected saveForm(): void {
    this.fieldErrors.set({}); this.formError.set(null); this.formCorrelationId.set(null);
    const draft = this.form();
    const errors: Record<string, string> = {};
    if (!draft.code.trim()) errors['code'] = this.fr() ? 'Le code est obligatoire.' : 'Code is required.';
    if (!draft.revision.nameFr.trim()) errors['nameFr'] = this.fr() ? 'Le nom français est obligatoire.' : 'French name is required.';
    if (!draft.revision.nameEn.trim()) errors['nameEn'] = this.fr() ? 'Le nom anglais est obligatoire.' : 'English name is required.';
    if (draft.revision.defaultAmountMinor < 0) errors['defaultAmountMinor'] = this.fr() ? 'Le montant doit être positif.' : 'Amount must be positive.';
    if (draft.revision.effectiveFrom && draft.revision.effectiveTo && draft.revision.effectiveTo < draft.revision.effectiveFrom) errors['effectiveTo'] = this.fr() ? 'La date de fin précède la date de début.' : 'End date precedes start date.';
    if (Object.keys(errors).length) { this.fieldErrors.set(errors); this.formError.set(this.fr() ? 'Corrigez les champs signalés.' : 'Correct the highlighted fields.'); return; }
    this.saving.set(true);
    const request = { code: draft.code, revision: draft.revision };
    const operation = draft.typeId ? (this.mode() === 'revision' ? this.feeApi.createRevision(draft.typeId, { revision: draft.revision, typeVersion: draft.typeVersion, reason: 'Fee catalogue revision' }) : this.feeApi.updateDraft(draft.typeId, { code: draft.code, revision: draft.revision, typeVersion: draft.typeVersion })) : this.feeApi.create(request as FeeTypeCreateRequest);
    operation.subscribe({ next: () => { this.saving.set(false); this.drawerOpen.set(false); this.success.set(this.fr() ? 'Type de frais enregistré.' : 'Fee type saved.'); this.reload(); }, error: (err) => { this.saving.set(false); this.applyError(err, true); } });
  }

  protected openActivate(item: FeeTypeView): void { this.activateTarget.set(item); this.deactivateTarget.set(null); this.actionReason = ''; this.formError.set(null); this.fieldErrors.set({}); }
  protected activationBlockers(item: FeeTypeView): string[] { const revision = item.currentRevision; if (!revision) return [this.fr() ? 'Créez une révision avant activation.' : 'Create a revision before activation.']; const blockers: string[] = []; if (!revision.receivableAccount?.compatible) blockers.push(this.fr() ? 'Choisissez un compte de créance ASSET compatible.' : 'Choose a compatible ASSET receivable account.'); if (!revision.revenueAccount?.compatible) blockers.push(this.fr() ? 'Choisissez un compte de produit REVENUE compatible.' : 'Choose a compatible REVENUE account.'); if (!revision.effectiveFrom) blockers.push(this.fr() ? 'Ajoutez une date d’effet.' : 'Add an effective-from date.'); return blockers; }
  protected activate(): void { const target = this.activateTarget(); if (!target) return; this.actionSaving.set(true); const body: FeeTypeActionRequest = { typeVersion: target.version, reason: this.actionReason || null }; this.feeApi.activate(target.id, body).subscribe({ next: () => { this.actionSaving.set(false); this.closeActionModal(); this.success.set(this.fr() ? 'Révision activée.' : 'Revision activated.'); this.reload(); }, error: (err) => { this.actionSaving.set(false); this.applyError(err, true); } }); }
  protected openDeactivate(item: FeeTypeView): void { this.deactivateTarget.set(item); this.activateTarget.set(null); this.actionReason = ''; this.usage.set(null); this.usageLoading.set(true); this.feeApi.usage(item.id).subscribe({ next: (usage) => { this.usage.set(usage); this.usageLoading.set(false); }, error: (err) => { this.usageLoading.set(false); this.applyError(err, true); } }); }
  protected deactivate(): void { const target = this.deactivateTarget(); if (!target) return; this.actionSaving.set(true); this.feeApi.deactivate(target.id, { typeVersion: target.version, reason: this.actionReason || null }).subscribe({ next: () => { this.actionSaving.set(false); this.closeActionModal(); this.success.set(this.fr() ? 'Type de frais désactivé sans supprimer son historique.' : 'Fee type deactivated without deleting history.'); this.reload(); }, error: (err) => { this.actionSaving.set(false); this.applyError(err, true); } }); }
  protected closeActionModal(): void { this.activateTarget.set(null); this.deactivateTarget.set(null); this.usage.set(null); this.actionReason = ''; this.formError.set(null); }
  protected openCompare(item: FeeTypeView): void { if (item.revisions.length < 2) return; const left = item.revisions[item.revisions.length - 1].revisionNo; const right = item.revisions[0].revisionNo; this.feeApi.compare(item.id, left, right).subscribe({ next: (comparison) => this.comparison.set(comparison), error: (err) => this.applyError(err, false) }); }
  protected closeComparison(): void { this.comparison.set(null); }
  protected updateLegacyRow(index: number, patch: Partial<LegacyMappingRow>): void { this.legacyRows.update((rows) => rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row)); }
  protected migrateLegacy(): void { this.legacySaving.set(true); const request: LegacyMappingRequest = { rows: this.legacyRows(), reason: 'Reviewed in fee type catalogue' }; this.feeApi.migrateLegacy(request).subscribe({ next: (result) => { this.legacySaving.set(false); this.success.set(`${result.acceptedCount} ${this.fr() ? 'mapping(s) accepté(s)' : 'mapping(s) accepted'} · ${result.unresolvedCount} ${this.fr() ? 'exception(s) dans le rapprochement' : 'exception(s) in reconciliation'}.`); this.reload(); }, error: (err) => { this.legacySaving.set(false); this.applyError(err, false); } }); }
  private applyError(err: unknown, form: boolean): void { const payload = (err as { error?: { message?: string; fieldErrors?: Record<string, string>; correlationId?: string; code?: string } })?.error ?? err as { message?: string; fieldErrors?: Record<string, string>; correlationId?: string; code?: string }; const message = payload?.message || (this.fr() ? 'Action impossible. Vérifiez les blocages et réessayez.' : 'Action failed. Review blockers and try again.'); this.error.set(form ? null : String(message)); this.formError.set(form ? String(message) : null); this.fieldErrors.set(payload?.fieldErrors ?? {}); this.correlationId.set(payload?.correlationId ?? null); this.formCorrelationId.set(payload?.correlationId ?? null); }
}
