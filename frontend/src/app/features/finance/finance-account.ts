import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n.service';
import {
  ConsolidatedReceipt, FinanceAccountApi, StudentAccountClassOption,
  StudentAccountSearchView, StudentFinanceAccount,
} from './finance-account.api';

@Component({
  selector: 'bbc-finance-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './finance-account.scss',
  template: `
    <div class="account-shell">
      <header class="account-hero">
        <div>
          <div class="eyebrow">Finance / {{ fr() ? 'Compte élève' : 'Student account' }}</div>
          <h1>{{ fr() ? 'Historique et reçu consolidé' : 'Payment history & consolidated receipt' }}</h1>
          <p>{{ fr() ? 'Retrouvez tous les versements d’un élève, quel que soit le mode de paiement ou la tranche.' : 'See every payment for a student, across payment methods and instalments.' }}</p>
        </div>
        <a class="secondary-button" routerLink="/finance">{{ fr() ? 'Retour à Finance' : 'Back to Finance' }}</a>
      </header>

      @if (error(); as message) { <div class="message error" role="alert">{{ message }}</div> }
      @if (success(); as message) { <div class="message success" role="status">{{ message }}</div> }

      <section class="workspace-card search-card">
        <div class="section-heading"><div><h2>{{ fr() ? '1. Trouver un élève' : '1. Find a student' }}</h2><p>{{ fr() ? 'Choisissez une classe pour afficher sa liste, puis affinez si nécessaire.' : 'Choose a class to see its students, then narrow the list if needed.' }}</p></div></div>
        <div class="search-grid">
          <label>
            <span>{{ fr() ? 'Classe' : 'Class' }}</span>
            <select class="field" [(ngModel)]="selectedClassId" (ngModelChange)="classChanged($event)" [disabled]="contextBusy()" aria-label="Class filter">
              <option value="">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>
              @for (option of classes(); track option.id) {
                <option [value]="option.id">{{ option.name }} · {{ option.studentCount }} {{ fr() ? 'élève(s)' : 'student(s)' }}</option>
              }
            </select>
          </label>
          <label>
            <span>{{ fr() ? 'Nom ou matricule (facultatif)' : 'Name or matricule (optional)' }}</span>
            <input class="field" [(ngModel)]="query" (keyup.enter)="search()" [placeholder]="fr() ? 'Ex. Djamdoudou ou BBC-1094' : 'e.g. Djamdoudou or BBC-1094'" aria-label="Student search">
          </label>
          <button type="button" class="primary-button search-button" [disabled]="busy() || (!query.trim() && !selectedClassId)" (click)="search()">{{ busy() ? '…' : (fr() ? 'Afficher' : 'Show students') }}</button>
        </div>
        @if (results().length) {
          <div class="result-summary">{{ results().length }} {{ fr() ? 'élève(s) trouvé(s)' : 'student(s) found' }}</div>
          <div class="result-list">
            @for (result of results(); track result.enrollmentId) {
              <button type="button" class="result-row" [class.selected]="selected()?.studentId === result.studentId" (click)="choose(result)">
                <span><strong>{{ result.studentName }}</strong><small>{{ result.matricule || '—' }} · {{ result.className || '—' }}</small></span>
                <span class="account-state" [class.due]="result.outstandingMinor > 0" [class.settled]="result.billedMinor > 0 && result.outstandingMinor === 0" [class.unconfigured]="result.billedMinor === 0">
                  @if (result.outstandingMinor > 0) {
                    <small>{{ fr() ? 'Solde dû' : 'Balance due' }}</small><b>{{ money(result.outstandingMinor) }}</b>
                  } @else if (result.billedMinor > 0) {
                    <b>{{ fr() ? 'Soldé' : 'Paid in full' }}</b><small>{{ money(result.paidMinor) }} {{ fr() ? 'payé' : 'paid' }}</small>
                  } @else if (result.paidMinor > 0) {
                    <small>{{ fr() ? 'Versements enregistrés' : 'Payments recorded' }}</small><b>{{ money(result.paidMinor) }}</b><small>{{ fr() ? 'Aucun frais facturé' : 'No fees billed' }}</small>
                  } @else {
                    <b>{{ fr() ? 'Aucun frais configuré' : 'No fees configured' }}</b><small>{{ fr() ? 'Aucun versement' : 'No payment' }}</small>
                  }
                </span>
              </button>
            }
          </div>
        } @else if (searched()) {
          <div class="empty-state">{{ fr() ? 'Aucun élève trouvé.' : 'No student found.' }}</div>
        } @else {
          <div class="empty-state compact">{{ fr() ? 'Sélectionnez une classe ou saisissez un nom pour commencer.' : 'Select a class or enter a name to begin.' }}</div>
        }
      </section>

      @if (account(); as current) {
        <section class="workspace-card account-card">
          <div class="section-heading">
            <div><div class="eyebrow">{{ current.matricule || '—' }} · {{ current.className || '—' }}</div><h2>{{ current.studentName }}</h2><p>{{ current.sessionLabel || (fr() ? 'Session courante' : 'Current session') }}</p></div>
            <button type="button" class="primary-button" [disabled]="busy()" (click)="prepareConsolidated(current)">{{ fr() ? 'Préparer le reçu consolidé' : 'Prepare consolidated receipt' }}</button>
          </div>
          <div class="totals-grid">
            <div><span>{{ fr() ? 'Facturé' : 'Billed' }}</span><b>{{ money(current.billedMinor) }}</b></div>
            <div><span>{{ fr() ? 'Payé' : 'Paid' }}</span><b>{{ money(current.paidMinor) }}</b></div>
            <div class="total-highlight"><span>{{ fr() ? 'Solde dû' : 'Balance due' }}</span><b>{{ money(current.outstandingMinor) }}</b></div>
            <div><span>{{ fr() ? 'Crédit' : 'Credit' }}</span><b>{{ money(current.creditMinor) }}</b></div>
          </div>
          <div class="history-heading"><div><h3>{{ fr() ? 'Tous les versements' : 'All payments' }}</h3><p>{{ current.payments.length }} {{ fr() ? 'opération(s), y compris les anciennes saisies.' : 'transaction(s), including legacy entries.' }}</p></div><span class="hash">{{ current.snapshotHash.slice(0, 12) }}…</span></div>
          @if (current.payments.length) {
            <div class="table-wrap"><table><thead><tr><th>{{ fr() ? 'Date' : 'Date' }}</th><th>{{ fr() ? 'Reçu' : 'Receipt' }}</th><th>{{ fr() ? 'Canal' : 'Method' }}</th><th>{{ fr() ? 'Compte' : 'Account' }}</th><th>{{ fr() ? 'Montant' : 'Amount' }}</th><th>{{ fr() ? 'Statut' : 'Status' }}</th></tr></thead><tbody>
              @for (payment of current.payments; track payment.source + payment.id) {
                <tr><td>{{ payment.paymentDate }}</td><td><strong>{{ payment.receiptNo || '—' }}</strong><small>{{ payment.reference || '—' }}</small></td><td>{{ payment.channelLabel }}<small>{{ payment.channelCode }}</small></td><td>{{ payment.treasuryAccountName || '—' }}</td><td><strong>{{ money(payment.netAmountMinor) }}</strong>@if (payment.refundedMinor) {<small>{{ fr() ? 'Remboursé' : 'Refunded' }} {{ money(payment.refundedMinor) }}</small>}</td><td><span class="status-badge" [class.good]="payment.status === 'POSTED' || payment.status === 'PARTIALLY_REFUNDED'">{{ payment.status }}</span></td></tr>
              }
            </tbody></table></div>
          } @else { <div class="empty-state">{{ fr() ? 'Aucun versement enregistré.' : 'No payments recorded.' }}</div> }
        </section>
      }

      @if (consolidated(); as receipt) {
        <div class="document-modal" role="dialog" aria-modal="true" aria-label="Consolidated receipt">
          <button type="button" class="modal-backdrop" aria-label="Close" (click)="closeConsolidated()"></button>
          <div class="document-dialog">
            <div class="document-toolbar">
              <div><strong>{{ fr() ? 'Reçu consolidé' : 'Consolidated receipt' }}</strong><small>{{ receipt.receiptNumber }}</small></div>
              <div class="toolbar-actions">
                <button type="button" class="secondary-button" (click)="closeConsolidated()">{{ fr() ? 'Fermer' : 'Close' }}</button>
                <button type="button" class="secondary-button" [disabled]="busy()" (click)="downloadConsolidated(receipt)">{{ fr() ? 'Télécharger PDF' : 'Download PDF' }}</button>
                <button type="button" class="primary-button" (click)="printConsolidated()">{{ fr() ? 'Imprimer' : 'Print' }}</button>
              </div>
            </div>
            <div class="document-scroll">
              <article class="receipt-print-paper consolidated-sheet">
                <header class="receipt-header">
                  <div class="receipt-brand"><span class="brand-mark">BBC</span><div><strong>Bayo Bilingual Complex</strong><small>Maroua · Cameroun</small></div></div>
                  <div class="receipt-number"><span>{{ fr() ? 'RELEVÉ DES PAIEMENTS' : 'PAYMENT STATEMENT' }}</span><strong>{{ receipt.receiptNumber }}</strong><small>{{ fr() ? 'Émis le' : 'Issued' }} {{ receipt.issueDate }}</small></div>
                </header>
                <section class="student-identity">
                  <div><span>{{ fr() ? 'Élève' : 'Student' }}</span><strong>{{ receipt.studentName }}</strong><small>{{ receipt.matricule || '—' }}</small></div>
                  <div><span>{{ fr() ? 'Classe' : 'Class' }}</span><strong>{{ receipt.className || '—' }}</strong><small>{{ receipt.sessionLabel || '—' }}</small></div>
                </section>
                <section class="receipt-totals">
                  <div><span>{{ fr() ? 'Facturé' : 'Billed' }}</span><strong>{{ money(receipt.billedMinor) }}</strong></div>
                  <div><span>{{ fr() ? 'Payé' : 'Paid' }}</span><strong>{{ money(receipt.paidMinor) }}</strong></div>
                  <div class="balance"><span>{{ fr() ? 'Solde dû' : 'Balance due' }}</span><strong>{{ money(receipt.outstandingMinor) }}</strong></div>
                  <div><span>{{ fr() ? 'Crédit' : 'Credit' }}</span><strong>{{ money(receipt.creditMinor) }}</strong></div>
                </section>
                <section class="receipt-history">
                  <div class="receipt-section-title"><strong>{{ fr() ? 'Historique des versements' : 'Payment history' }}</strong><span>{{ receipt.payments.length }} {{ fr() ? 'opération(s)' : 'transaction(s)' }}</span></div>
                  @if (receipt.payments.length) {
                    <table><thead><tr><th>{{ fr() ? 'Date' : 'Date' }}</th><th>{{ fr() ? 'Reçu / Référence' : 'Receipt / Reference' }}</th><th>{{ fr() ? 'Mode / Compte' : 'Method / Account' }}</th><th>{{ fr() ? 'Montant' : 'Amount' }}</th></tr></thead><tbody>
                      @for (payment of receipt.payments; track payment.source + payment.id) {
                        <tr><td>{{ payment.paymentDate }}</td><td><strong>{{ payment.receiptNo || '—' }}</strong><small>{{ payment.reference || '—' }}</small></td><td>{{ payment.channelLabel }}<small>{{ payment.treasuryAccountName || '—' }}</small></td><td class="amount-cell">{{ money(payment.netAmountMinor) }}</td></tr>
                      }
                    </tbody></table>
                  } @else { <div class="receipt-empty">{{ fr() ? 'Aucun versement enregistré.' : 'No payment recorded.' }}</div> }
                </section>
                <footer class="receipt-footer"><span>{{ fr() ? 'Document généré par BBC SMS' : 'Generated by BBC SMS' }}</span><span>{{ receipt.snapshotHash.slice(0, 16) }}</span></footer>
              </article>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class FinanceAccountComponent {
  private readonly api = inject(FinanceAccountApi);
  private readonly i18n = inject(I18nService);
  protected readonly fr = () => this.i18n.lang() === 'fr';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly searched = signal(false);
  protected readonly contextBusy = signal(true);
  protected readonly classes = signal<StudentAccountClassOption[]>([]);
  protected readonly results = signal<StudentAccountSearchView[]>([]);
  protected readonly selected = signal<StudentAccountSearchView | null>(null);
  protected readonly account = signal<StudentFinanceAccount | null>(null);
  protected readonly consolidated = signal<ConsolidatedReceipt | null>(null);
  protected query = '';
  protected selectedClassId = '';

  constructor() {
    this.api.context().subscribe({
      next: value => { this.classes.set(value.classes); this.contextBusy.set(false); },
      error: error => { this.contextBusy.set(false); this.applyError(error); },
    });
  }

  protected search(): void {
    if (!this.query.trim() && !this.selectedClassId) return;
    this.busy.set(true); this.error.set(null); this.success.set(null); this.searched.set(true);
    this.api.search(this.query.trim(), this.selectedClassId || undefined).subscribe({
      next: value => { this.results.set(value); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected classChanged(value: string): void {
    this.selectedClassId = value || '';
    this.selected.set(null); this.account.set(null); this.consolidated.set(null);
    if (this.selectedClassId || this.query.trim()) this.search();
    else { this.results.set([]); this.searched.set(false); }
  }

  protected choose(value: StudentAccountSearchView): void {
    this.selected.set(value); this.busy.set(true); this.error.set(null); this.success.set(null);
    this.api.student(value.studentId).subscribe({
      next: account => { this.account.set(account); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected prepareConsolidated(account: StudentFinanceAccount): void {
    this.busy.set(true); this.error.set(null); this.success.set(null);
    this.api.consolidatedReceipt(account.studentId).subscribe({
      next: receipt => { this.consolidated.set(receipt); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected closeConsolidated(): void { this.consolidated.set(null); }
  protected downloadConsolidated(receipt: ConsolidatedReceipt): void {
    this.busy.set(true); this.error.set(null);
    this.api.consolidatedReceiptPdf(receipt.studentId).subscribe({
      next: blob => { this.download(blob, `${receipt.receiptNumber.replace(/[^A-Za-z0-9_-]/g, '-')}.pdf`); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }
  protected printConsolidated(): void {
    document.body.classList.add('printing-receipt');
    window.print();
    window.setTimeout(() => document.body.classList.remove('printing-receipt'), 250);
  }

  protected money(value: number): string { return `${Math.round(value || 0).toLocaleString(this.fr() ? 'fr-FR' : 'en-US')} XAF`; }
  private download(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.hidden = true;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
  }
  private applyError(error: unknown): void { const response = error as HttpErrorResponse; const body = response?.error as { message?: string } | undefined; this.error.set(body?.message || response?.message || (this.fr() ? 'Le serveur n’a pas pu charger ce compte.' : 'The server could not load this account.')); }
}
