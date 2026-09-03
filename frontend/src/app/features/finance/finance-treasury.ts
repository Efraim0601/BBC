import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AccountView, FinanceAccountingApi } from './accounting.api';
import {
  TreasuryAccountCreate, TreasuryAccountView, TreasuryApi, TreasuryMovementRequest, TreasuryMovementView,
} from './treasury.api';

const today = () => new Date().toISOString().slice(0, 10);

@Component({
  selector: 'bbc-finance-treasury',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="treasury-shell">
      <header class="treasury-hero">
        <div>
          <div class="eyebrow">Finance / <span>{{ fr() ? 'Trésorerie' : 'Treasury' }}</span></div>
          <h1>{{ fr() ? 'Comptes, dépôts et retraits' : 'Accounts, deposits and withdrawals' }}</h1>
          <p>{{ fr() ? 'Chaque solde vient du journal comptable. Une opération reste traçable et ne peut pas être supprimée.' : 'Every balance comes from the accounting ledger. Each operation stays traceable and cannot be deleted.' }}</p>
        </div>
        <div class="hero-links">
          <a routerLink="/finance">{{ fr() ? 'Vue finance' : 'Finance overview' }}</a>
          @if (auth.canAction('ACCOUNT_MANAGE')) { <a routerLink="/finance/accounting">{{ fr() ? 'Comptabilité avancée' : 'Advanced accounting' }}</a> }
          @if (!canManage() && !canMove()) { <span>{{ fr() ? 'Consultation uniquement' : 'View only' }}</span> }
        </div>
      </header>

      @if (error()) { <div class="state-error" role="alert">{{ error() }} <button type="button" (click)="reload()">{{ fr() ? 'Réessayer' : 'Retry' }}</button></div> }
      @if (success()) { <div class="state-success" role="status">{{ success() }}</div> }

      <div class="summary-grid">
        <article class="summary-card"><span>{{ fr() ? 'Trésorerie active' : 'Active treasury' }}</span><strong>{{ money(totalBalance()) }}</strong><small>{{ activeAccounts().length }} {{ fr() ? 'comptes' : 'accounts' }}</small></article>
        <article class="summary-card"><span>{{ fr() ? 'Banques' : 'Banks' }}</span><strong>{{ money(bankBalance()) }}</strong><small>{{ bankAccounts().length }} {{ fr() ? 'comptes bancaires' : 'bank accounts' }}</small></article>
        <article class="summary-card"><span>{{ fr() ? 'Espèces' : 'Cash' }}</span><strong>{{ money(cashBalance()) }}</strong><small>{{ fr() ? 'solde calculé du journal' : 'ledger-derived balance' }}</small></article>
      </div>

      <div class="treasury-grid">
        <section class="panel">
          <div class="section-heading"><div><h2>{{ fr() ? 'Comptes opérationnels' : 'Operational accounts' }}</h2><p>{{ fr() ? 'Cash, BGFI, Afriland, CCA et Regional. Archiver retire un compte des opérations sans effacer son historique.' : 'Cash, BGFI, Afriland, CCA and Regional. Archiving removes an account from operations without deleting its history.' }}</p></div>@if (canManage()) { <button type="button" class="btn-primary" (click)="accountFormOpen.set(!accountFormOpen())">+ {{ fr() ? 'Ajouter un compte' : 'Add account' }}</button> }</div>
          @if (accountFormOpen() && canManage()) {
            <div class="form-box">
              <div class="form-grid">
                <label class="field-label">{{ fr() ? 'Type' : 'Type' }} *<select class="field" [(ngModel)]="accountDraft.kind"><option value="CASH">{{ fr() ? 'Espèces' : 'Cash' }}</option><option value="BANK">{{ fr() ? 'Banque' : 'Bank' }}</option><option value="MOBILE_WALLET">{{ fr() ? 'Portefeuille mobile' : 'Mobile wallet' }}</option><option value="OTHER">{{ fr() ? 'Autre' : 'Other' }}</option></select></label>
                <label class="field-label">{{ fr() ? 'Nom affiché' : 'Display name' }} *<input class="field" [(ngModel)]="accountDraft.displayName" placeholder="BGFI Bank"></label>
                <label class="field-label">{{ fr() ? 'Institution' : 'Institution' }}<input class="field" [(ngModel)]="accountDraft.institutionName" placeholder="BGFI Bank"></label>
                <label class="field-label">{{ fr() ? '4 derniers chiffres' : 'Last 4 digits' }}<input class="field" maxlength="32" [(ngModel)]="accountDraft.accountNumberLast4" placeholder="1234"></label>
                <label class="field-label">{{ fr() ? 'Solde initial (XAF)' : 'Opening balance (XAF)' }}<input class="field" type="number" min="0" step="1" [(ngModel)]="accountDraft.openingBalanceMinor"></label>
                <label class="field-label">{{ fr() ? 'Date du solde initial' : 'Opening balance date' }}<input class="field" type="date" [(ngModel)]="accountDraft.openingBalanceDate"></label>
              </div>
              <div class="actions"><button type="button" class="btn-secondary" (click)="accountFormOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary" [disabled]="saving()" (click)="saveAccount()">{{ saving() ? '…' : (fr() ? 'Créer le compte' : 'Create account') }}</button></div>
            </div>
          }
          @if (canManage() && archiveTarget(); as target) {
            <div class="form-box archive-box">
              <strong>{{ fr() ? 'Archiver ' + target.displayName + ' ?' : 'Archive ' + target.displayName + '?' }}</strong>
              <p>{{ fr() ? 'Le compte disparaîtra des opérations mais son solde et son historique resteront consultables.' : 'The account will leave daily operations, but its balance and history will remain available.' }}</p>
              <label class="field-label">{{ fr() ? 'Motif d’archivage' : 'Archive reason' }} *<input class="field" [(ngModel)]="archiveReason" placeholder="{{ fr() ? 'Compte remplacé ou fermé' : 'Account replaced or closed' }}"></label>
              <div class="actions"><button type="button" class="btn-secondary" (click)="cancelArchive()">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="btn-primary danger-button" [disabled]="saving()" (click)="confirmArchive()">{{ saving() ? '…' : (fr() ? 'Archiver le compte' : 'Archive account') }}</button></div>
            </div>
          }
          <div class="account-list">
            @for (account of accounts(); track account.id) {
              <article class="account-row" [class.archived]="!account.active"><div class="account-icon">{{ kindIcon(account.kind) }}</div><div class="account-main"><div class="account-title"><strong>{{ account.displayName }}</strong>@if (account.defaultAccount) { <span class="default-pill">{{ fr() ? 'Principal' : 'Default' }}</span> }@if (!account.active) { <span class="archived-pill">{{ fr() ? 'Archivé' : 'Archived' }}</span> }</div><small>{{ account.institutionName || account.kind }} · {{ account.chartAccountCode }}@if (account.accountNumberLast4) { · •••• {{ account.accountNumberLast4 }} }</small></div><div class="account-balance"><strong>{{ money(account.balanceMinor) }}</strong><small>{{ account.currency }}</small></div>@if (account.active && canManage()) { <button type="button" class="link-button danger" (click)="archive(account)">{{ fr() ? 'Archiver' : 'Archive' }}</button> }</article>
            } @empty { <div class="empty-state">{{ fr() ? 'Aucun compte de trésorerie.' : 'No treasury accounts.' }}</div> }
          </div>
        </section>

        @if (canMove()) { <section class="panel">
          <div class="section-heading"><div><h2>{{ fr() ? 'Enregistrer un mouvement' : 'Record a movement' }}</h2><p>{{ fr() ? 'Dépôt et retrait exigent une contrepartie. Un transfert entre comptes ne crée ni recette ni dépense.' : 'A deposit or withdrawal requires a counter-account. A transfer between accounts creates neither revenue nor expense.' }}</p></div></div>
          <div class="form-grid single">
            <label class="field-label">{{ fr() ? 'Opération' : 'Operation' }} *<select class="field" [(ngModel)]="movementDraft.movementType"><option value="DEPOSIT">{{ fr() ? 'Dépôt' : 'Deposit' }}</option><option value="WITHDRAWAL">{{ fr() ? 'Retrait' : 'Withdrawal' }}</option><option value="TRANSFER">{{ fr() ? 'Transfert interne' : 'Internal transfer' }}</option></select></label>
            <label class="field-label">{{ fr() ? 'Date' : 'Date' }} *<input class="field" type="date" [(ngModel)]="movementDraft.entryDate"></label>
            @if (movementDraft.movementType === 'TRANSFER' || movementDraft.movementType === 'WITHDRAWAL') { <label class="field-label">{{ fr() ? 'Compte source' : 'From account' }} *<select class="field" [(ngModel)]="movementDraft.fromAccountId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of activeAccounts(); track account.id) {<option [value]="account.id">{{ account.displayName }} · {{ money(account.balanceMinor) }}</option>}</select></label> }
            @if (movementDraft.movementType === 'TRANSFER' || movementDraft.movementType === 'DEPOSIT') { <label class="field-label">{{ fr() ? 'Compte destination' : 'To account' }} *<select class="field" [(ngModel)]="movementDraft.toAccountId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of activeAccounts(); track account.id) {<option [value]="account.id">{{ account.displayName }}</option>}</select></label> }
            @if (movementDraft.movementType !== 'TRANSFER') { <label class="field-label">{{ fr() ? 'Contrepartie comptable' : 'Counter-account' }} *<select class="field" [(ngModel)]="movementDraft.offsetAccountId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of counterpartAccounts(); track account.id) {<option [value]="account.id">{{ account.code }} · {{ fr() ? account.nameFr : account.nameEn }}</option>}</select></label> }
            <label class="field-label">{{ fr() ? 'Montant (XAF)' : 'Amount (XAF)' }} *<input class="field" type="number" min="1" step="1" [(ngModel)]="movementDraft.amountMinor"></label>
            <label class="field-label">{{ fr() ? 'Motif' : 'Reason' }} *<textarea class="field" rows="2" [(ngModel)]="movementDraft.reason" placeholder="Dépôt BGFI du relevé bancaire"></textarea></label>
            <label class="field-label">{{ fr() ? 'Référence' : 'Reference' }}<input class="field" [(ngModel)]="movementDraft.reference" placeholder="N° bordereau, relevé…"></label>
          </div>
          <div class="actions"><button type="button" class="btn-primary full" [disabled]="!canMove() || saving()" (click)="saveMovement()">{{ saving() ? (fr() ? 'Publication…' : 'Posting…') : (fr() ? 'Enregistrer et poster' : 'Record and post') }}</button></div>
        </section> } @else {
          <section class="panel">
            <div class="section-heading"><div><h2>{{ fr() ? 'Consultation des mouvements' : 'Movement overview' }}</h2><p>{{ fr() ? 'Vous pouvez consulter les soldes et l’historique. Les dépôts, retraits et transferts sont réservés au service financier.' : 'You can view balances and history. Deposits, withdrawals and transfers are reserved for finance staff.' }}</p></div></div>
          </section>
        }
      </div>

      <section class="panel movement-panel"><div class="section-heading"><div><h2>{{ fr() ? 'Historique des mouvements' : 'Movement history' }}</h2><p>{{ movements().length }} {{ fr() ? 'opérations immuables' : 'immutable operations' }}</p></div><button type="button" class="btn-secondary" (click)="reload()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div>@if (!movements().length) { <div class="empty-state">{{ fr() ? 'Aucun mouvement enregistré.' : 'No movements recorded.' }}</div> } @else {<div class="table-wrap"><table><thead><tr><th>{{ fr() ? 'N° / date' : 'No. / date' }}</th><th>{{ fr() ? 'Opération' : 'Operation' }}</th><th>{{ fr() ? 'Comptes' : 'Accounts' }}</th><th>{{ fr() ? 'Motif' : 'Reason' }}</th><th class="right">{{ fr() ? 'Montant' : 'Amount' }}</th><th>{{ fr() ? 'Journal' : 'Journal' }}</th></tr></thead><tbody>@for (movement of movements(); track movement.id) {<tr><td><strong>{{ movement.movementNo }}</strong><small>{{ movement.entryDate }}</small></td><td><span class="movement-pill" [class]="'movement-' + movement.movementType.toLowerCase()">{{ movementLabel(movement.movementType) }}</span></td><td><small>{{ movement.fromAccountName || '—' }} → {{ movement.toAccountName || '—' }}</small></td><td><div>{{ movement.reason }}</div><small>{{ movement.reference || '—' }}</small></td><td class="right"><strong>{{ money(movement.amountMinor) }}</strong></td><td><small class="mono">{{ movement.journalNumber || '—' }}</small></td></tr>}</tbody></table></div>}</section>
    </div>
  `,
  styleUrl: './finance-treasury.scss',
})
export class FinanceTreasuryComponent implements OnInit {
  protected api = inject(TreasuryApi);
  protected accounting = inject(FinanceAccountingApi);
  protected auth = inject(AuthService);
  protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected accounts = signal<TreasuryAccountView[]>([]);
  protected movements = signal<TreasuryMovementView[]>([]);
  protected counterpartCatalog = signal<AccountView[]>([]);
  protected loading = signal(false);
  protected saving = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected accountFormOpen = signal(false);
  protected archiveTarget = signal<TreasuryAccountView | null>(null);
  protected archiveReason = '';
  protected accountDraft: TreasuryAccountCreate = this.blankAccount();
  protected movementDraft: TreasuryMovementRequest = this.blankMovement();

  protected activeAccounts = computed(() => this.accounts().filter(account => account.active));
  protected bankAccounts = computed(() => this.activeAccounts().filter(account => account.kind === 'BANK'));
  protected totalBalance = computed(() => this.activeAccounts().reduce((sum, account) => sum + Number(account.balanceMinor || 0), 0));
  protected bankBalance = computed(() => this.bankAccounts().reduce((sum, account) => sum + Number(account.balanceMinor || 0), 0));
  protected cashBalance = computed(() => this.activeAccounts().filter(account => account.kind === 'CASH').reduce((sum, account) => sum + Number(account.balanceMinor || 0), 0));

  ngOnInit(): void { this.reload(); }
  protected canManage(): boolean { return this.auth.canAction('TREASURY_ACCOUNT_MANAGE'); }
  protected canMove(): boolean { return this.auth.canAction('TREASURY_MOVEMENT_CREATE'); }
  protected money(value: number): string { return `${Math.round(Number(value) || 0).toLocaleString('fr-FR')} XAF`; }
  protected kindIcon(kind: string): string { return kind === 'CASH' ? '▣' : kind === 'BANK' ? '▤' : '◈'; }
  protected movementLabel(type: string): string { const labels: Record<string, string> = { DEPOSIT: this.fr() ? 'Dépôt' : 'Deposit', WITHDRAWAL: this.fr() ? 'Retrait' : 'Withdrawal', TRANSFER: this.fr() ? 'Transfert' : 'Transfer', OPENING: this.fr() ? 'Solde initial' : 'Opening balance', ADJUSTMENT: this.fr() ? 'Ajustement' : 'Adjustment' }; return labels[type] || type; }
  protected counterpartAccounts(): AccountView[] { const treasuryIds = new Set(this.accounts().map(account => account.chartAccountId)); return this.counterpartCatalog().filter(account => account.active && account.postingAllowed && !treasuryIds.has(account.id)); }

  protected reload(): void {
    this.loading.set(true); this.error.set(null);
    forkJoin({ accounts: this.api.accounts(), movements: this.api.movements(), catalog: this.accounting.accounts(undefined, true) }).subscribe({
      next: value => { this.accounts.set(value.accounts); this.movements.set(value.movements); this.counterpartCatalog.set(value.catalog); this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(this.message(err)); },
    });
  }
  protected saveAccount(): void {
    if (!this.accountDraft.displayName.trim() || !this.accountDraft.openingBalanceDate) { this.error.set(this.fr() ? 'Le nom et la date du solde initial sont obligatoires.' : 'Name and opening date are required.'); return; }
    this.saving.set(true);
    this.api.createAccount({ ...this.accountDraft, displayName: this.accountDraft.displayName.trim(), openingBalanceMinor: Number(this.accountDraft.openingBalanceMinor) || 0 }).subscribe({ next: () => { this.saving.set(false); this.accountFormOpen.set(false); this.accountDraft = this.blankAccount(); this.success.set(this.fr() ? 'Compte créé. Le solde initial a été posté au journal.' : 'Account created. The opening balance was posted to the ledger.'); this.reload(); }, error: err => { this.saving.set(false); this.error.set(this.message(err)); } });
  }
  protected archive(account: TreasuryAccountView): void {
    this.archiveTarget.set(account);
    this.archiveReason = '';
    this.error.set(null);
  }
  protected cancelArchive(): void {
    this.archiveTarget.set(null);
    this.archiveReason = '';
  }
  protected confirmArchive(): void {
    const account = this.archiveTarget();
    if (!account) return;
    if (!this.archiveReason.trim()) {
      this.error.set(this.fr() ? 'Indiquez le motif d’archivage.' : 'Enter an archive reason.');
      return;
    }
    this.saving.set(true);
    this.api.archiveAccount(account.id, { version: account.version, reason: this.archiveReason.trim() }).subscribe({
      next: () => {
        this.saving.set(false);
        this.archiveTarget.set(null);
        this.archiveReason = '';
        this.success.set(this.fr() ? 'Compte archivé. Son historique reste consultable.' : 'Account archived. Its history remains available.');
        this.reload();
      },
      error: err => { this.saving.set(false); this.error.set(this.message(err)); },
    });
  }
  protected saveMovement(): void {
    if (!this.movementDraft.reason.trim() || !this.movementDraft.amountMinor || (this.movementDraft.movementType === 'TRANSFER' && (!this.movementDraft.fromAccountId || !this.movementDraft.toAccountId)) || (this.movementDraft.movementType !== 'TRANSFER' && (!this.movementDraft.offsetAccountId || (!this.movementDraft.fromAccountId && !this.movementDraft.toAccountId)))) { this.error.set(this.fr() ? 'Complétez le compte, la contrepartie, le montant et le motif.' : 'Complete the account, counter-account, amount and reason.'); return; }
    this.saving.set(true);
    this.api.createMovement({ ...this.movementDraft, amountMinor: Number(this.movementDraft.amountMinor) || 0, reason: this.movementDraft.reason.trim(), reference: this.movementDraft.reference?.trim() || null }).subscribe({ next: value => { this.saving.set(false); this.movementDraft = this.blankMovement(); this.success.set(`${this.movementLabel(value.movementType)} ${value.movementNo} ${this.fr() ? 'posté.' : 'posted.'}`); this.reload(); }, error: err => { this.saving.set(false); this.error.set(this.message(err)); } });
  }
  private blankAccount(): TreasuryAccountCreate { return { kind: 'BANK', displayName: '', institutionName: null, accountNumberLast4: null, currency: 'XAF', openingBalanceMinor: 0, openingBalanceDate: today(), chartAccountCode: null }; }
  private blankMovement(): TreasuryMovementRequest { return { movementType: 'DEPOSIT', entryDate: today(), fromAccountId: null, toAccountId: '', offsetAccountId: '', amountMinor: 0, currency: 'XAF', reason: '', reference: null }; }
  private message(err: unknown): string { return (err as { error?: { message?: string } })?.error?.message || (this.fr() ? 'Opération impossible.' : 'Operation failed.'); }
}
