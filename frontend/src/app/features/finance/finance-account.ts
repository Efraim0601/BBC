import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n.service';
import { StudentSearchView } from './collections.api';
import { ConsolidatedReceipt, FinanceAccountApi, StudentFinanceAccount } from './finance-account.api';

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
        <div class="section-heading"><div><h2>{{ fr() ? '1. Rechercher un élève' : '1. Find a student' }}</h2><p>{{ fr() ? 'Nom, matricule ou classe.' : 'Name, matricule or class.' }}</p></div></div>
        <div class="search-row">
          <input class="field" [(ngModel)]="query" (keyup.enter)="search()" [placeholder]="fr() ? 'Ex. Aminou, BBC-1356, 6ème A' : 'e.g. Aminou, BBC-1356, 6ème A'" aria-label="Student search">
          <button type="button" class="primary-button" [disabled]="busy() || !query.trim()" (click)="search()">{{ busy() ? '…' : (fr() ? 'Rechercher' : 'Search') }}</button>
        </div>
        @if (results().length) {
          <div class="result-list">
            @for (result of results(); track result.enrollmentId) {
              <button type="button" class="result-row" [class.selected]="selected()?.studentId === result.studentId" (click)="choose(result)">
                <span><strong>{{ result.studentName }}</strong><small>{{ result.matricule || '—' }} · {{ result.className || '—' }}</small></span>
                <b>{{ money(result.outstandingMinor) }}</b>
              </button>
            }
          </div>
        } @else if (searched()) {
          <div class="empty-state">{{ fr() ? 'Aucun élève trouvé.' : 'No student found.' }}</div>
        }
      </section>

      @if (account(); as current) {
        <section class="workspace-card account-card">
          <div class="section-heading">
            <div><div class="eyebrow">{{ current.matricule || '—' }} · {{ current.className || '—' }}</div><h2>{{ current.studentName }}</h2><p>{{ current.sessionLabel || (fr() ? 'Session courante' : 'Current session') }}</p></div>
            <button type="button" class="primary-button" [disabled]="busy()" (click)="printConsolidated(current)">{{ fr() ? 'Imprimer le reçu consolidé' : 'Print consolidated receipt' }}</button>
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
  protected readonly results = signal<StudentSearchView[]>([]);
  protected readonly selected = signal<StudentSearchView | null>(null);
  protected readonly account = signal<StudentFinanceAccount | null>(null);
  protected query = '';

  protected search(): void {
    if (!this.query.trim()) return;
    this.busy.set(true); this.error.set(null); this.success.set(null); this.searched.set(true);
    this.api.search(this.query.trim()).subscribe({
      next: value => { this.results.set(value); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected choose(value: StudentSearchView): void {
    this.selected.set(value); this.busy.set(true); this.error.set(null); this.success.set(null);
    this.api.student(value.studentId).subscribe({
      next: account => { this.account.set(account); this.busy.set(false); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected printConsolidated(account: StudentFinanceAccount): void {
    this.busy.set(true); this.error.set(null); this.success.set(null);
    this.api.consolidatedReceiptPdf(account.studentId).subscribe({
      next: blob => { this.download(blob, `releve-paiements-${account.matricule || account.studentId}.pdf`); this.busy.set(false); this.success.set(this.fr() ? 'Reçu consolidé généré. Il reprend tous les versements visibles.' : 'Consolidated receipt generated with every visible payment.'); },
      error: error => { this.busy.set(false); this.applyError(error); },
    });
  }

  protected money(value: number): string { return `${Math.round(value || 0).toLocaleString(this.fr() ? 'fr-FR' : 'en-US')} XAF`; }
  private download(blob: Blob, filename: string): void { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url); }
  private applyError(error: unknown): void { const response = error as HttpErrorResponse; const body = response?.error as { message?: string } | undefined; this.error.set(body?.message || response?.message || (this.fr() ? 'Le serveur n’a pas pu charger ce compte.' : 'The server could not load this account.')); }
}
