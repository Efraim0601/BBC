import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CashierSessionView,
  ChannelView,
  CollectionApiError,
  CollectionsApi,
  PaymentQuoteView,
  PaymentView,
  ProviderTransactionView,
  RefundView,
  ReversalPreview,
  StudentSearchView,
} from './collections.api';
import { I18nService } from '../../core/i18n.service';

type CollectionTab = 'collect' | 'payments' | 'cashier' | 'provider';

@Component({
  selector: 'bbc-finance-collections',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="collections-shell">
      <header class="collections-hero">
        <div>
          <div class="eyebrow">Finance / <span>{{ fr() ? 'Encaissements' : 'Collections' }}</span> <b>BAY-47 Â· V63 Â· XAF</b></div>
          <h1>{{ fr() ? 'Encaissements & comptes Ã©lÃ¨ves' : 'Collections & student accounts' }}</h1>
          <p>{{ fr() ? 'Recherchez un compte, affectez le paiement aux Ã©chÃ©ances les plus anciennes et postez une Ã©criture Ã©quilibrÃ©e.' : 'Find an account, allocate to the oldest open installments and post a balanced journal.' }}</p>
        </div>
        <div class="hero-links"><a href="/finance/charges">{{ fr() ? 'Charges' : 'Charges' }}</a><a href="/finance/accounting">{{ fr() ? 'ComptabilitÃ©' : 'Accounting' }}</a></div>
      </header>

      @if (error()) {
        <div class="state-error" role="alert"><span><strong>{{ error() }}</strong>@if (correlationId()) { <small> Â· {{ correlationId() }}</small> }</span><button type="button" (click)="clearMessage()">{{ fr() ? 'Fermer' : 'Dismiss' }}</button></div>
      }
      @if (success()) { <div class="state-success" role="status">{{ success() }}</div> }

      <nav class="collection-tabs" aria-label="Collection sections">
        @for (item of tabs; track item.key) {
          <button type="button" [class.active]="tab() === item.key" [attr.aria-current]="tab() === item.key ? 'page' : null" (click)="setTab(item.key)">{{ fr() ? item.fr : item.en }}</button>
        }
      </nav>

      @if (loading()) { <div class="loading-panel" aria-live="polite">{{ fr() ? 'Chargement des encaissementsâ€¦' : 'Loading collection workspaceâ€¦' }}</div> }

      @if (!loading()) {
        @switch (tab()) {
          @case ('collect') {
            <section class="collect-layout" aria-label="New collection">
              <div class="step-rail" aria-label="Collection steps">
                @for (item of steps; track item.no) {
                  <button type="button" [class.current]="step() === item.no" [class.done]="step() > item.no" [disabled]="item.no > step()" (click)="jumpTo(item.no)"><span>{{ item.no }}</span><strong>{{ fr() ? item.fr : item.en }}</strong></button>
                }
              </div>

              <div class="workspace-stack">
                @if (step() === 1) {
                  <section class="panel">
                    <div class="section-heading"><div><h2>{{ fr() ? '1. Trouver le compte' : '1. Find account' }}</h2><p>{{ fr() ? 'Nom, matricule, classe ou responsable lÃ©gal.' : 'Search by name, matricule, class or guardian.' }}</p></div><span class="status-pill">{{ searchResults().length }} {{ fr() ? 'rÃ©sultats' : 'results' }}</span></div>
                    <div class="search-row"><label class="field-label grow">{{ fr() ? 'Recherche' : 'Search' }} *<input class="collection-field" [(ngModel)]="searchModel" (keyup.enter)="search()" placeholder="Nom Â· matricule Â· classe Â· tÃ©lÃ©phone"></label><label class="field-label session-filter">{{ fr() ? 'Session (optionnel)' : 'Session (optional)' }}<input class="collection-field" [(ngModel)]="sessionModel" placeholder="UUID session"></label><button type="button" class="btn-primary" [disabled]="busy()" (click)="search()">{{ busy() ? 'â€¦' : (fr() ? 'Rechercher' : 'Search') }}</button></div>
                    @if (fieldError()) { <div class="field-error">{{ fieldError() }}</div> }
                    @if (!searchResults().length) { <div class="empty-state"><strong>{{ fr() ? 'Aucun compte sÃ©lectionnÃ©' : 'No account selected' }}</strong><p>{{ fr() ? 'Saisissez une recherche; le contexte de session et de classe sera visible avant tout encaissement.' : 'Enter a search; session and class context are shown before any collection.' }}</p></div> }
                    @if (searchResults().length) {
                      <div class="student-results">
                        @for (item of searchResults(); track item.enrollmentId) {
                          <button type="button" class="student-card" [class.selected]="selected()?.enrollmentId === item.enrollmentId" (click)="chooseStudent(item)">
                            <span class="avatar">{{ initials(item.studentName) }}</span><span class="student-card-main"><strong>{{ item.studentName }}</strong><small>{{ item.matricule || 'â€”' }} Â· {{ item.className || (fr() ? 'Classe inconnue' : 'Class unavailable') }}</small></span><span class="student-balance"><b>{{ money(item.outstandingMinor) }}</b><small>{{ fr() ? 'solde' : 'balance' }} Â· <em>{{ money(item.overdueMinor) }} {{ fr() ? 'en retard' : 'overdue' }}</em></small></span>
                          </button>
                        }
                      </div>
                    }
                  </section>
                }

                @if (selected(); as student) {
                  <section class="account-context panel"><div><span class="eyebrow">{{ fr() ? 'Compte sÃ©lectionnÃ©' : 'Selected account' }}</span><h2>{{ student.studentName }}</h2><p>{{ student.matricule || 'â€”' }} Â· {{ student.className || 'â€”' }} Â· {{ student.academicSessionId.slice(0, 8) }}</p></div><div class="context-metrics"><span><b>{{ money(student.outstandingMinor) }}</b>{{ fr() ? 'solde' : 'balance' }}</span><span><b>{{ money(student.overdueMinor) }}</b>{{ fr() ? 'en retard' : 'overdue' }}</span></div></section>
                }

                @if (selected() && step() >= 2) {
                  <section class="panel" [class.muted-panel]="step() > 2">
                    <div class="section-heading"><div><h2>{{ fr() ? '2. Affecter' : '2. Allocate' }}</h2><p>{{ fr() ? 'Les Ã©chÃ©ances dues sont proposÃ©es dans lâ€™ordre chronologique.' : 'Due installments are proposed oldest first.' }}</p></div><span class="status-pill">{{ quote() ? (quote()!.installments.length + ' ' + (fr() ? 'Ã©chÃ©ances' : 'installments')) : (fr() ? 'En attente' : 'Waiting') }}</span></div>
                    @if (!quote()) {
                      <div class="form-grid"><label class="field-label">{{ fr() ? 'Montant reÃ§u (XAF)' : 'Received amount (XAF)' }} *<input type="number" min="1" class="collection-field" [class.invalid]="fieldError()" [(ngModel)]="amountModel"></label><label class="field-label" id="payment-date">{{ fr() ? 'Date de rÃ©ception' : 'Received date' }} *<input type="date" class="collection-field" [min]="selected()?.enrolledOn || null" [max]="selected()?.exitedOn || null" [(ngModel)]="paymentDateModel"><small class="field-hint">{{ fr() ? 'Inscription : ' : 'Enrollment window: ' }}{{ selected()?.enrolledOn }}{{ selected()?.exitedOn ? ' â†’ ' + selected()?.exitedOn : (fr() ? ' â†’ en cours' : ' â†’ active') }}</small></label></div><button type="button" class="btn-primary" [disabled]="busy()" (click)="loadQuote()">{{ busy() ? (fr() ? 'Calculâ€¦' : 'Calculatingâ€¦') : (fr() ? 'PrÃ©parer lâ€™affectation' : 'Prepare allocation') }}</button>
                    }
                    @if (quote(); as q) {
                      <div class="allocation-toolbar"><label><input type="radio" name="allocationMode" value="auto" [(ngModel)]="allocationMode"> {{ fr() ? 'Affecter automatiquement (du plus ancien au plus rÃ©cent)' : 'Apply automatically (oldest due first)' }}</label><label><input type="radio" name="allocationMode" value="manual" [(ngModel)]="allocationMode"> {{ fr() ? 'Affecter manuellement' : 'Manual allocation' }}</label></div>
                      <div class="table-wrap"><table><thead><tr><th>{{ fr() ? 'Ã‰chÃ©ance' : 'Installment' }}</th><th>{{ fr() ? 'Ã‰chÃ©ance due' : 'Due date' }}</th><th>{{ fr() ? 'Ouvert' : 'Open' }}</th><th>{{ fr() ? 'AffectÃ©' : 'Applied' }}</th></tr></thead><tbody>@for (line of q.installments; track line.installmentId) { <tr><td><strong>{{ line.feeTypeCode }}</strong><small>{{ line.label }}</small></td><td>{{ line.dueDate }}</td><td>{{ money(line.outstandingMinor) }}</td><td><input type="number" min="0" [max]="line.outstandingMinor" class="allocation-input" [disabled]="allocationMode === 'auto'" [ngModel]="allocationFor(line.installmentId)" (ngModelChange)="setAllocation(line.installmentId, $event)"></td></tr>} @if (!q.installments.length) { <tr><td colspan="4" class="empty-cell">{{ fr() ? 'Aucune Ã©chÃ©ance ouverte.' : 'No open installments.' }}</td></tr> }</tbody></table></div>
                      <div class="quote-summary"><span>{{ fr() ? 'ReÃ§u' : 'Received' }} <b>{{ money(q.requestedMinor) }}</b></span><span>{{ fr() ? 'AffectÃ©' : 'Allocated' }} <b>{{ money(allocationTotal()) }}</b></span><span>{{ fr() ? 'CrÃ©dit crÃ©Ã©' : 'New credit' }} <b>{{ money(calculatedCredit(q)) }}</b></span><span>{{ fr() ? 'Solde aprÃ¨s' : 'Balance after' }} <b>{{ money(projectedOutstanding(q)) }}</b></span></div>
                      @if (q.blockers.length) { <div class="blocked-box"><strong>{{ fr() ? 'BloquÃ© avant paiement' : 'Blocked before payment' }}</strong><ul>@for (blocker of q.blockers; track blocker.code) { <li><b>{{ blocker.code }}</b> Â· {{ blocker.message }}@if (blocker.actionLink) { <a [href]="blocker.actionLink">{{ fr() ? 'Corriger' : 'Fix' }}</a> }</li> }</ul></div> }
                      <div class="actions"><button type="button" class="btn-secondary" (click)="step.set(1)">{{ fr() ? 'Retour' : 'Back' }}</button><button type="button" class="btn-primary" [disabled]="q.blockers.length > 0 || allocationTotal() > q.requestedMinor" (click)="goToPaymentDetails()">{{ fr() ? 'Continuer' : 'Continue' }}</button></div>
                    }
                  </section>
                }

                @if (quote() && step() >= 3) {
                  <section class="panel" [class.muted-panel]="step() > 3"><div class="section-heading"><div><h2>{{ fr() ? '3. DÃ©tails du paiement' : '3. Payment details' }}</h2><p>{{ fr() ? 'Le canal, la rÃ©fÃ©rence et la caisse ouverte sont vÃ©rifiÃ©s avant publication.' : 'Channel, reference and open cashier are checked before posting.' }}</p></div></div>
                    <div class="form-grid"><label class="field-label">{{ fr() ? 'Canal' : 'Channel' }} *<select class="collection-field" [(ngModel)]="selectedChannelId"><option value="">{{ fr() ? 'Choisir un canal' : 'Choose a channel' }}</option>@for (channel of quote()!.channels; track channel.id) { <option [value]="channel.id" [disabled]="!channel.enabled || !channel.debitAccountId">{{ channel.code }} Â· {{ fr() ? channel.labelFr : channel.labelEn }} Â· {{ channel.debitAccountCode || (fr() ? 'Compte manquant' : 'Missing account') }}{{ channel.cashierRequired ? ' Â· CASHIER' : '' }}</option> }</select></label><label class="field-label">{{ fr() ? 'RÃ©fÃ©rence opÃ©rateur' : 'Operator reference' }}<input class="collection-field" [class.invalid]="selectedChannel()?.requiresReference && !referenceModel.trim()" [(ngModel)]="referenceModel" placeholder="{{ fr() ? 'Obligatoire selon le canal' : 'Required for some channels' }}"></label><label class="field-label">{{ fr() ? 'Payeur' : 'Payer' }}<input class="collection-field" [(ngModel)]="payerModel"></label><label class="field-label">{{ fr() ? 'Note' : 'Note' }}<input class="collection-field" [(ngModel)]="noteModel"></label></div>
                    @if (selectedChannel()?.cashierRequired && !cashier()) { <div class="blocked-box"><strong>{{ fr() ? 'Caisse requise' : 'Cashier required' }}</strong><p>{{ fr() ? 'Ouvrez une session de caisse dans lâ€™onglet Caisse avant de poster du cash.' : 'Open a cashier session in the Cashier tab before posting cash.' }}</p><button type="button" class="btn-secondary" (click)="setTab('cashier')">{{ fr() ? 'Ouvrir la caisse' : 'Open cashier' }}</button></div> }
                    <div class="actions"><button type="button" class="btn-secondary" (click)="step.set(2)">{{ fr() ? 'Retour' : 'Back' }}</button><button type="button" class="btn-primary" [disabled]="!canReview()" (click)="goToReview()">{{ fr() ? 'Revoir' : 'Review' }}</button></div>
                  </section>
                }

                @if (quote() && step() === 4) {
                  <section class="panel review-panel"><div class="section-heading"><div><h2>{{ fr() ? '4. Revoir et poster' : '4. Review & post' }}</h2><p>{{ fr() ? 'Une Ã©criture immuable sera crÃ©Ã©e; une correction passe par un renversement ou un remboursement autorisÃ©.' : 'An immutable journal will be created; corrections require an authorized reversal or refund.' }}</p></div><span class="status-pill ready">{{ fr() ? 'PrÃªt Ã  poster' : 'Ready to post' }}</span></div><div class="review-grid"><div><span>{{ fr() ? 'Ã‰lÃ¨ve' : 'Student' }}</span><strong>{{ selected()!.studentName }}</strong></div><div><span>{{ fr() ? 'Canal' : 'Channel' }}</span><strong>{{ selectedChannel()?.code }} Â· {{ selectedChannel()?.debitAccountCode }}</strong></div><div><span>{{ fr() ? 'ReÃ§u' : 'Received' }}</span><strong>{{ money(quote()!.requestedMinor) }}</strong></div><div><span>{{ fr() ? 'AffectÃ© / crÃ©dit' : 'Allocated / credit' }}</span><strong>{{ money(allocationTotal()) }} / {{ money(calculatedCredit(quote()!)) }}</strong></div><div><span>{{ fr() ? 'RÃ©fÃ©rence' : 'Reference' }}</span><strong>{{ referenceModel || 'â€”' }}</strong></div><div><span>{{ fr() ? 'PÃ©riode' : 'Posting period' }}</span><strong>{{ quote()!.postingPeriodCode || 'â€”' }}</strong></div></div><div class="review-note">{{ fr() ? 'Le numÃ©ro de reÃ§u sera attribuÃ© atomiquement. Le tÃ©lÃ©chargement/impression du reÃ§u sera disponible dans BAY-48.' : 'The receipt number is allocated atomically. Receipt download/print will be available in BAY-48.' }}</div><div class="actions"><button type="button" class="btn-secondary" (click)="step.set(3)">{{ fr() ? 'Modifier' : 'Edit' }}</button><button type="button" class="btn-primary" [disabled]="busy()" (click)="postCollection()">{{ busy() ? (fr() ? 'Publicationâ€¦' : 'Postingâ€¦') : (fr() ? 'Poster lâ€™encaissement' : 'Post collection') }}</button></div></section>
                }

                @if (payment(); as posted) {
                  <section class="success-panel" role="status"><span class="success-icon">âœ“</span><div><h2>{{ fr() ? 'Encaissement postÃ©' : 'Collection posted' }}</h2><p>{{ posted.studentId.slice(0, 8) }} Â· {{ posted.channelCode }} Â· {{ posted.paymentDate }}</p><strong>{{ posted.receiptDocumentNumber || posted.receiptNo }}</strong><div class="success-details">{{ money(posted.amountMinor) }} Â· {{ money(posted.allocatedMinor) }} {{ fr() ? 'affectÃ©' : 'allocated' }} Â· {{ money(posted.creditMinor) }} {{ fr() ? 'crÃ©dit' : 'credit' }}</div>@if (posted.receiptDocumentStatus === 'ISSUED' && posted.receiptDocumentId) { <div class="receipt-ready-note">{{ fr() ? 'ReÃ§u PDF disponible sur le serveur.' : 'Server receipt PDF is available.' }} <b>{{ posted.receiptDocumentNumber }}</b></div><div class="actions"><button type="button" class="btn-primary" (click)="downloadReceipt(posted)">{{ fr() ? 'TÃ©lÃ©charger le reÃ§u' : 'Download receipt' }}</button><a class="btn-secondary" href="/finance/documents">{{ fr() ? 'Voir les documents' : 'Open documents' }}</a></div> } @if (posted.receiptDocumentStatus === 'GENERATION_FAILED') { <div class="placeholder-note">{{ fr() ? 'Le paiement est postÃ©, mais la gÃ©nÃ©ration du reÃ§u a Ã©chouÃ© : ' : 'Payment posted, but receipt generation failed: ' }}{{ posted.receiptGenerationError || (fr() ? 'rÃ©essayez depuis Documents financiers.' : 'retry from Financial documents.') }}</div><div class="actions"><a class="btn-secondary" href="/finance/documents">{{ fr() ? 'Ouvrir les documents' : 'Open documents' }}</a></div> }<div class="actions"><button type="button" class="btn-primary" (click)="collectAnother()">{{ fr() ? 'Nouvel encaissement' : 'Collect another' }}</button><button type="button" class="btn-secondary" (click)="openPayment(posted)">{{ fr() ? 'Voir le dÃ©tail' : 'Open details' }}</button></div></div></section>
                }
              </div>
            </section>
          }
          @case ('payments') {
            <section class="payments-layout"><div class="panel"><div class="section-heading"><div><h2>{{ fr() ? 'Encaissements postÃ©s' : 'Posted collections' }}</h2><p>{{ payments().length }} {{ fr() ? 'transactions visibles dans ce tenant' : 'tenant-scoped transactions' }}</p></div><button type="button" class="btn-secondary" (click)="loadPayments()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div><div class="filter-row"><input class="collection-field" [(ngModel)]="paymentQueryModel" placeholder="Reference / status"><select class="collection-field" [(ngModel)]="paymentStatusModel"><option value="">{{ fr() ? 'Tous les statuts' : 'All statuses' }}</option><option>POSTED</option><option>REVERSED</option><option>PARTIALLY_REFUNDED</option><option>REFUNDED</option></select><button type="button" class="btn-primary" (click)="loadPayments()">{{ fr() ? 'Filtrer' : 'Filter' }}</button></div>@if (!payments().length) { <div class="empty-state"><strong>{{ fr() ? 'Aucun encaissement' : 'No collections yet' }}</strong><p>{{ fr() ? 'Les encaissements postÃ©s apparaÃ®tront ici.' : 'Posted collections will appear here.' }}</p></div> } @if (payments().length) { <div class="table-wrap"><table><thead><tr><th>{{ fr() ? 'ReÃ§u' : 'Receipt' }}</th><th>{{ fr() ? 'Date / canal' : 'Date / channel' }}</th><th>{{ fr() ? 'Montant' : 'Amount' }}</th><th>{{ fr() ? 'AffectÃ© / crÃ©dit' : 'Allocated / credit' }}</th><th>{{ fr() ? 'Statut' : 'Status' }}</th><th></th></tr></thead><tbody>@for (item of payments(); track item.id) { <tr><td><button type="button" class="link-button" (click)="openPayment(item)">{{ item.receiptNo }}</button><small>{{ item.reference || 'â€”' }}</small></td><td>{{ item.paymentDate }}<small>{{ item.channelCode }}</small></td><td>{{ money(item.amountMinor) }}</td><td>{{ money(item.allocatedMinor) }} / {{ money(item.creditMinor) }}</td><td><span class="status-pill" [class.ready]="item.status === 'POSTED'">{{ item.status }}</span></td><td><button type="button" class="link-button" (click)="openPayment(item)">{{ fr() ? 'DÃ©tail' : 'Details' }}</button></td></tr> }</tbody></table></div> }</div>
              @if (selectedPayment(); as item) { <aside class="panel detail-panel"><div class="section-heading"><div><h2>{{ item.receiptNo }}</h2><p>{{ item.paymentDate }} Â· {{ item.channelCode }}</p></div><button type="button" class="icon-button" (click)="selectedPayment.set(null)" aria-label="Close">Ã—</button></div><div class="detail-list"><div><span>{{ fr() ? 'Montant' : 'Amount' }}</span><b>{{ money(item.amountMinor) }}</b></div><div><span>{{ fr() ? 'AffectÃ©' : 'Allocated' }}</span><b>{{ money(item.allocatedMinor) }}</b></div><div><span>{{ fr() ? 'CrÃ©dit' : 'Credit' }}</span><b>{{ money(item.creditMinor) }}</b></div><div><span>{{ fr() ? 'Journal' : 'Journal' }}</span><b>{{ item.journalEntryId?.slice(0, 8) || 'â€”' }}</b></div></div><h3>{{ fr() ? 'Affectations' : 'Allocations' }}</h3>@for (allocation of item.allocations; track allocation.id) { <div class="mini-row"><span>{{ allocation.installmentId.slice(0, 8) }}</span><b>{{ money(allocation.allocatedMinor) }}</b><small>{{ allocation.status }}</small></div> }<h3>{{ fr() ? 'Renversement / remboursement' : 'Reversal / refund' }}</h3>@if (reversalPreview(); as preview) { <div class="blocked-box" [class.allowed-box]="preview.allowed"><strong>{{ preview.allowed ? (fr() ? 'Renversement autorisÃ©' : 'Reversal allowed') : (fr() ? 'Renversement bloquÃ©' : 'Reversal blocked') }}</strong><p>{{ money(preview.amountMinor) }} Â· {{ money(preview.remainingCreditMinor) }} {{ fr() ? 'crÃ©dit restant' : 'remaining credit' }}</p>@if (preview.blockers.length) { <ul>@for (blocker of preview.blockers; track blocker.code) { <li>{{ blocker.code }} Â· {{ blocker.message }}</li> }</ul> }</div> }<label class="field-label">{{ fr() ? 'Motif de correction' : 'Correction reason' }}<textarea class="collection-field" rows="2" [(ngModel)]="correctionReasonModel"></textarea></label><button type="button" class="btn-secondary full-button" [disabled]="!reversalPreview()?.allowed || !correctionReasonModel.trim() || busy()" (click)="reverseSelected()">{{ fr() ? 'Renverser lâ€™encaissement' : 'Reverse collection' }}</button><div class="refund-form"><h4>{{ fr() ? 'Demander un remboursement du crÃ©dit disponible' : 'Request refund of available credit' }}</h4><label class="field-label">{{ fr() ? 'Montant XAF' : 'Amount XAF' }}<input type="number" min="1" class="collection-field" [(ngModel)]="refundAmountModel"></label><label class="field-label">{{ fr() ? 'Canal' : 'Channel' }}<select class="collection-field" [(ngModel)]="refundChannelModel"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (channel of paymentChannels(); track channel.code) { <option [value]="channel.code">{{ channel.code }}</option> }</select></label><label class="field-label">{{ fr() ? 'RÃ©fÃ©rence' : 'Reference' }}<input class="collection-field" [(ngModel)]="refundReferenceModel"></label><label class="field-label">{{ fr() ? 'Motif' : 'Reason' }}<textarea class="collection-field" rows="2" [(ngModel)]="refundReasonModel"></textarea></label><button type="button" class="btn-secondary full-button" [disabled]="busy() || !canRequestRefund()" (click)="requestRefund()">{{ fr() ? 'Soumettre pour maker-checker' : 'Submit for maker-checker' }}</button></div>@if (refunds().length) { <div class="refund-list"><h4>{{ fr() ? 'Demandes' : 'Requests' }}</h4>@for (refund of refunds(); track refund.id) { <div class="mini-row"><span>{{ refund.refundNo || 'REQUEST' }} Â· {{ money(refund.amountMinor) }}</span><b>{{ refund.status }}</b>@if (refund.status === 'REQUESTED') { <span><button type="button" class="link-button" (click)="decideRefund(refund, true)">{{ fr() ? 'Approuver' : 'Approve' }}</button> <button type="button" class="danger-link" (click)="decideRefund(refund, false)">{{ fr() ? 'Refuser' : 'Reject' }}</button></span> }</div> }</div> }</aside> }
            </section>
          }
          @case ('cashier') {
            <section class="cashier-layout">
              <div class="panel">
                <div class="section-heading">
                  <div><h2>{{ fr() ? 'Session de caisse' : 'Cashier session' }}</h2><p>{{ fr() ? 'Le cash ne peut Ãªtre postÃ© sans une session ouverte.' : 'Cash cannot be posted without an open session.' }}</p></div>
                  <span class="status-pill" [class.ready]="cashier()?.status === 'OPEN'">{{ cashier()?.status || (fr() ? 'FermÃ©e' : 'Closed') }}</span>
                </div>
                @if (!cashier()) {
                  <div class="empty-state"><strong>{{ fr() ? 'Aucune caisse ouverte' : 'No cashier open' }}</strong><p>{{ fr() ? 'Ouvrez une session pour encaisser du cash.' : 'Open a session before collecting cash.' }}</p><label class="field-label">{{ fr() ? 'Fond de caisse XAF' : 'Opening cash XAF' }} *<input type="number" min="0" class="collection-field" [(ngModel)]="openingCashModel"></label><button type="button" class="btn-primary" [disabled]="busy()" (click)="openCashier()">{{ fr() ? 'Ouvrir la caisse' : 'Open cashier' }}</button></div>
                }
                @if (cashier(); as current) {
                  <div class="cashier-summary">
                    <div><span>{{ fr() ? 'Ouverte le' : 'Opened' }}</span><b>{{ current.openedAt || 'â€”' }}</b></div>
                    <div><span>{{ fr() ? 'Fond initial' : 'Opening cash' }}</span><b>{{ money(current.openingCashMinor) }}</b></div>
                    <div><span>{{ fr() ? 'Attendu' : 'Expected' }}</span><b>{{ money(current.expectedCashMinor) }}</b></div>
                    <div><span>{{ fr() ? 'Ã‰cart' : 'Variance' }}</span><b [class.danger-text]="(current.varianceMinor || 0) !== 0">{{ money(current.varianceMinor || 0) }}</b></div>
                  </div>
                  @if (current.status === 'OPEN') {
                    <div class="form-grid"><label class="field-label">{{ fr() ? 'EspÃ¨ces dÃ©clarÃ©es XAF' : 'Declared cash XAF' }} *<input type="number" min="0" class="collection-field" [(ngModel)]="declaredCashModel"></label><label class="field-label">{{ fr() ? 'Note de clÃ´ture' : 'Close note' }}<textarea class="collection-field" rows="2" [(ngModel)]="cashierNoteModel"></textarea></label></div>
                    <button type="button" class="btn-primary" [disabled]="busy()" (click)="closeCashier()">{{ fr() ? 'ClÃ´turer la caisse' : 'Close cashier' }}</button>
                  }
                  @if (current.status === 'PENDING_APPROVAL') {
                    <div class="blocked-box"><strong>{{ fr() ? 'Approbation manager requise' : 'Manager approval required' }}</strong><p>{{ fr() ? 'Lâ€™Ã©cart doit Ãªtre expliquÃ© et approuvÃ© par un autre utilisateur.' : 'The variance needs an explanation and approval by another user.' }}</p><button type="button" class="btn-primary" [disabled]="busy()" (click)="approveCashier()">{{ fr() ? 'Approuver la clÃ´ture' : 'Approve close' }}</button></div>
                  }
                }
              </div>
              <div class="panel"><h2>{{ fr() ? 'ContrÃ´les' : 'Controls' }}</h2><ul class="plain-list"><li>âœ“ {{ fr() ? 'Montants et journaux restent immuables aprÃ¨s publication.' : 'Amounts and journals are immutable after posting.' }}</li><li>âœ“ {{ fr() ? 'Le sÃ©quenÃ§age des reÃ§us est atomique par annÃ©e.' : 'Receipt sequencing is atomic by year.' }}</li><li>! {{ fr() ? 'Le rapport imprimable sera un document BAY-48.' : 'Printable close report is a BAY-48 document.' }}</li></ul></div>
            </section>
          }
          @case ('provider') {
            <section class="provider-layout"><div class="panel"><div class="section-heading"><div><h2>{{ fr() ? 'Rapprochement fournisseur' : 'Provider reconciliation' }}</h2><p>{{ fr() ? 'Les callbacks sont idempotents et ne postent jamais de cash automatiquement.' : 'Callbacks are idempotent and never post cash automatically.' }}</p></div><span class="status-pill">MANUAL REVIEW</span></div><div class="form-grid"><label class="field-label">{{ fr() ? 'Fournisseur' : 'Provider' }} *<input class="collection-field" [(ngModel)]="providerCodeModel" placeholder="MTN_MOMO"></label><label class="field-label">{{ fr() ? 'ID Ã©vÃ©nement' : 'Event ID' }} *<input class="collection-field" [(ngModel)]="providerEventModel"></label><label class="field-label">{{ fr() ? 'RÃ©fÃ©rence externe' : 'External reference' }}<input class="collection-field" [(ngModel)]="providerReferenceModel"></label><label class="field-label">{{ fr() ? 'Montant XAF' : 'Amount XAF' }}<input type="number" min="0" class="collection-field" [(ngModel)]="providerAmountModel"></label><label class="field-label">{{ fr() ? 'Canal' : 'Channel' }} *<select class="collection-field" [(ngModel)]="providerChannelId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (channel of paymentChannels(); track channel.id) { <option [value]="channel.id">{{ channel.code }}</option> }</select></label></div><div class="review-note">{{ fr() ? 'Une correspondance de rÃ©fÃ©rence peut marquer une transaction MATCHED. Sinon, elle reste MANUAL_REVIEW jusquâ€™Ã  confirmation autorisÃ©e.' : 'A matching reference can mark a transaction MATCHED. Otherwise it remains MANUAL_REVIEW until authorized confirmation.' }}</div><button type="button" class="btn-primary" [disabled]="busy() || !canIngestProvider()" (click)="ingestProvider()">{{ busy() ? 'â€¦' : (fr() ? 'Enregistrer le callback' : 'Ingest callback') }}</button>@if (providerResult(); as result) { <div class="provider-result"><strong>{{ result.status }}</strong><p>{{ result.message || 'â€”' }}</p><span>{{ result.externalReference || 'â€”' }}</span></div> }</div></section>
          }
        }
      }
    </div>
  `,
  styleUrl: './finance-collections.scss',
})
export class FinanceCollectionsComponent implements OnInit {
  private readonly api = inject(CollectionsApi);
  private readonly i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected tabs: { key: CollectionTab; fr: string; en: string }[] = [
    { key: 'collect', fr: 'Nouvel encaissement', en: 'New collection' },
    { key: 'payments', fr: 'Encaissements', en: 'Collections' },
    { key: 'cashier', fr: 'Caisse', en: 'Cashier' },
    { key: 'provider', fr: 'Callbacks', en: 'Provider callbacks' },
  ];
  protected steps = [
    { no: 1, fr: 'Trouver', en: 'Find' }, { no: 2, fr: 'Affecter', en: 'Allocate' },
    { no: 3, fr: 'DÃ©tails', en: 'Details' }, { no: 4, fr: 'Revoir & poster', en: 'Review & post' },
  ];
  protected tab = signal<CollectionTab>('collect');
  protected step = signal(1);
  protected loading = signal(true);
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected correlationId = signal<string | null>(null);
  protected fieldError = signal<string | null>(null);
  protected searchModel = '';
  protected sessionModel = '';
  protected searchResults = signal<StudentSearchView[]>([]);
  protected selected = signal<StudentSearchView | null>(null);
  protected amountModel = 0;
  protected paymentDateModel = today();
  protected quote = signal<PaymentQuoteView | null>(null);
  protected paymentChannels = signal<ChannelView[]>([]);
  protected allocationMode: 'auto' | 'manual' = 'auto';
  protected allocations = signal<Record<string, number>>({});
  protected payment = signal<PaymentView | null>(null);
  protected selectedChannelId = '';
  protected referenceModel = '';
  protected payerModel = '';
  protected noteModel = '';
  protected cashier = signal<CashierSessionView | null>(null);
  protected payments = signal<PaymentView[]>([]);
  protected selectedPayment = signal<PaymentView | null>(null);
  protected paymentQueryModel = '';
  protected paymentStatusModel = '';
  protected reversalPreview = signal<ReversalPreview | null>(null);
  protected correctionReasonModel = '';
  protected refunds = signal<RefundView[]>([]);
  protected refundAmountModel = 0;
  protected refundChannelModel = '';
  protected refundReferenceModel = '';
  protected refundReasonModel = '';
  protected openingCashModel = 0;
  protected declaredCashModel = 0;
  protected cashierNoteModel = '';
  protected providerCodeModel = '';
  protected providerEventModel = '';
  protected providerReferenceModel = '';
  protected providerAmountModel: number | null = null;
  protected providerChannelId = '';
  protected providerResult = signal<ProviderTransactionView | null>(null);

  ngOnInit(): void {
    this.loadChannels();
    this.loadCashier();
    this.loadPayments();
    this.loading.set(false);
  }

  protected setTab(value: CollectionTab): void {
    this.tab.set(value);
    this.clearMessage();
    if (value === 'payments') this.loadPayments();
    if (value === 'cashier') this.loadCashier();
  }

  protected clearMessage(): void { this.error.set(null); this.correlationId.set(null); }

  protected search(): void {
    if (!this.searchModel.trim()) { this.fieldError.set(this.fr() ? 'Saisissez un nom, matricule, classe ou responsable.' : 'Enter a name, matricule, class or guardian.'); return; }
    this.fieldError.set(null); this.busy.set(true);
    this.api.search(this.searchModel.trim(), this.sessionModel.trim() || undefined).subscribe({
      next: value => { this.searchResults.set(value); this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); },
    });
  }

  protected chooseStudent(value: StudentSearchView): void {
    this.selected.set(value); this.quote.set(null); this.payment.set(null); this.allocations.set({}); this.amountModel = 0;
    const currentDate = today();
    this.paymentDateModel = currentDate < value.enrolledOn ? value.enrolledOn
      : (value.exitedOn && currentDate > value.exitedOn ? value.exitedOn : currentDate);
    this.step.set(2); this.fieldError.set(null); this.clearMessage();
  }

  protected loadQuote(): void {
    if (!this.selected() || Number(this.amountModel) <= 0 || !this.paymentDateModel) { this.fieldError.set(this.fr() ? 'Ã‰lÃ¨ve, montant positif et date sont obligatoires.' : 'Student, positive amount and date are required.'); return; }
    this.fieldError.set(null); this.busy.set(true);
    this.api.quote({ enrollmentId: this.selected()!.enrollmentId, amountMinor: Number(this.amountModel), paymentDate: this.paymentDateModel }).subscribe({
      next: value => { this.quote.set(value); this.allocations.set(Object.fromEntries(value.installments.map(line => [line.installmentId, line.proposedMinor]))); this.selectedChannelId = value.channels.find(channel => channel.enabled && channel.debitAccountId)?.id || ''; this.providerChannelId = this.selectedChannelId; this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); },
    });
  }

  protected allocationFor(id: string): number { return this.allocations()[id] || 0; }
  protected setAllocation(id: string, value: number): void { this.allocations.update(current => ({ ...current, [id]: Math.max(0, Number(value) || 0) })); }
  protected allocationTotal(): number { return Object.values(this.allocations()).reduce((sum, value) => sum + value, 0); }
  protected calculatedCredit(value: PaymentQuoteView): number { return Math.max(0, value.requestedMinor - this.allocationTotal()); }
  protected projectedOutstanding(value: PaymentQuoteView): number { return Math.max(0, value.projectedOutstandingMinor + value.proposedAllocatedMinor - this.allocationTotal()); }
  protected channels(): ChannelView[] { return this.quote()?.channels || []; }
  protected selectedChannel(): ChannelView | null { return this.channels().find(channel => channel.id === this.selectedChannelId) || null; }
  protected goToPaymentDetails(): void { if (this.quote() && this.allocationTotal() <= this.quote()!.requestedMinor) this.step.set(3); }
  protected canReview(): boolean { const channel = this.selectedChannel(); return !!this.quote() && !!channel && channel.enabled && !!channel.debitAccountId && (!channel.requiresReference || !!this.referenceModel.trim()) && (!channel.cashierRequired || !!this.cashier()); }
  protected goToReview(): void { if (this.canReview()) this.step.set(4); else this.fieldError.set(this.fr() ? 'Canal, compte, rÃ©fÃ©rence et caisse doivent Ãªtre valides.' : 'Channel, account, reference and cashier must be valid.'); }
  protected jumpTo(value: number): void { if (value <= this.step()) this.step.set(value); }

  protected postCollection(): void {
    const selected = this.selected(); const quote = this.quote(); const channel = this.selectedChannel();
    if (!selected || !quote || !channel || !this.canReview()) return;
    this.busy.set(true);
    const allocations = quote.installments.map(line => ({ installmentId: line.installmentId, amountMinor: this.allocationFor(line.installmentId) })).filter(line => line.amountMinor > 0);
    this.api.post({ enrollmentId: selected.enrollmentId, amountMinor: Number(this.amountModel), paymentChannelId: channel.id, paymentDate: this.paymentDateModel, reference: this.referenceModel.trim(), payerName: this.payerModel.trim(), note: this.noteModel.trim(), allocations, legacyReceiptNo: '' }, `collection-ui-${Date.now()}-${Math.random().toString(36).slice(2)}`).subscribe({
      next: value => { this.payment.set(value); this.selectedPayment.set(value); this.busy.set(false); this.success.set(this.fr() ? 'Encaissement postÃ© avec Ã©criture Ã©quilibrÃ©e.' : 'Collection posted with a balanced journal.'); this.loadPayments(); this.loadCashier(); }, error: err => { this.busy.set(false); this.applyError(err); },
    });
  }

  protected collectAnother(): void { this.selected.set(null); this.quote.set(null); this.payment.set(null); this.allocations.set({}); this.searchResults.set([]); this.searchModel = ''; this.amountModel = 0; this.referenceModel = ''; this.step.set(1); this.success.set(null); }
  protected downloadReceipt(value: PaymentView): void { if (!value.receiptDocumentId) return; this.api.receiptPdf(value.receiptDocumentId).subscribe({ next: blob => { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${value.receiptDocumentNumber || value.receiptNo}.pdf`; anchor.click(); URL.revokeObjectURL(url); }, error: err => this.applyError(err) }); }
  protected loadPayments(): void { this.api.list({ reference: this.paymentQueryModel || null, status: this.paymentStatusModel || null }).subscribe({ next: value => this.payments.set(value), error: err => this.applyError(err) }); }
  protected openPayment(value: PaymentView): void { this.selectedPayment.set(value); this.correctionReasonModel = ''; this.refundAmountModel = value.creditMinor; this.api.detail(value.id).subscribe({ next: detail => this.selectedPayment.set(detail), error: err => this.applyError(err) }); this.api.reversalPreview(value.id).subscribe({ next: preview => this.reversalPreview.set(preview), error: err => this.applyError(err) }); this.api.refunds(value.id).subscribe({ next: refunds => this.refunds.set(refunds), error: err => this.applyError(err) }); }
  protected reverseSelected(): void { const payment = this.selectedPayment(); if (!payment || !this.reversalPreview()?.allowed || !this.correctionReasonModel.trim()) return; this.busy.set(true); this.api.reverse(payment.id, this.correctionReasonModel.trim(), payment.version, `reversal-ui-${Date.now()}`).subscribe({ next: value => { this.busy.set(false); this.selectedPayment.set(value); this.success.set(this.fr() ? 'Encaissement renversÃ©; le journal original reste inchangÃ©.' : 'Collection reversed; the original journal remains unchanged.'); this.openPayment(value); this.loadPayments(); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected canRequestRefund(): boolean { return !!this.selectedPayment() && this.refundAmountModel > 0 && this.refundAmountModel <= this.selectedPayment()!.creditMinor && !!this.refundChannelModel && !!this.refundReasonModel.trim(); }
  protected requestRefund(): void { const payment = this.selectedPayment(); if (!payment || !this.canRequestRefund()) return; this.busy.set(true); this.api.requestRefund(payment.id, { amountMinor: Number(this.refundAmountModel), channelCode: this.refundChannelModel, reference: this.refundReferenceModel.trim(), reason: this.refundReasonModel.trim(), version: payment.version }).subscribe({ next: value => { this.busy.set(false); this.refunds.update(items => [value, ...items]); this.success.set(this.fr() ? 'Demande de remboursement soumise pour approbation.' : 'Refund request submitted for approval.'); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected decideRefund(value: RefundView, approve: boolean): void { this.busy.set(true); this.api.decideRefund(value.id, { version: value.version, approve, decisionReason: approve ? 'Approved in collection workspace' : 'Rejected in collection workspace' }).subscribe({ next: result => { this.busy.set(false); this.refunds.update(items => items.map(item => item.id === result.id ? result : item)); this.success.set(approve ? (this.fr() ? 'Remboursement approuvÃ© et postÃ©.' : 'Refund approved and posted.') : (this.fr() ? 'Remboursement refusÃ©.' : 'Refund rejected.')); }, error: err => { this.busy.set(false); this.applyError(err); } }); }

  protected loadCashier(): void { this.api.cashierCurrent().subscribe({ next: value => this.cashier.set(value), error: err => this.applyError(err) }); }
  protected loadChannels(): void {
    this.api.channels().subscribe({
      next: value => {
        this.paymentChannels.set(value.filter(channel => channel.enabled));
        if (!this.providerChannelId) this.providerChannelId = this.paymentChannels()[0]?.id || '';
      },
      error: err => this.applyError(err),
    });
  }
  protected openCashier(): void { this.busy.set(true); this.api.openCashier(Number(this.openingCashModel) || 0).subscribe({ next: value => { this.busy.set(false); this.cashier.set(value); this.success.set(this.fr() ? 'Session de caisse ouverte.' : 'Cashier session opened.'); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected closeCashier(): void { const current = this.cashier(); if (!current) return; this.busy.set(true); this.api.closeCashier(current.id, { declaredCashMinor: Number(this.declaredCashModel) || 0, closeNote: this.cashierNoteModel.trim(), version: current.version }).subscribe({ next: value => { this.busy.set(false); this.cashier.set(value); this.success.set(this.fr() ? 'Caisse clÃ´turÃ©e.' : 'Cashier closed.'); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected approveCashier(): void { const current = this.cashier(); if (!current) return; this.busy.set(true); this.api.approveCashier(current.id, { declaredCashMinor: Number(this.declaredCashModel) || current.declaredCashMinor || 0, closeNote: this.cashierNoteModel.trim(), version: current.version }).subscribe({ next: value => { this.busy.set(false); this.cashier.set(value); this.success.set(this.fr() ? 'ClÃ´ture approuvÃ©e.' : 'Cashier close approved.'); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected canIngestProvider(): boolean { return !!this.providerCodeModel.trim() && !!this.providerEventModel.trim() && !!this.providerChannelId; }
  protected ingestProvider(): void { if (!this.canIngestProvider()) return; this.busy.set(true); this.api.ingestProvider({ providerCode: this.providerCodeModel.trim(), eventId: this.providerEventModel.trim(), paymentChannelId: this.providerChannelId, externalReference: this.providerReferenceModel.trim(), amountMinor: this.providerAmountModel == null ? null : Number(this.providerAmountModel), currency: 'XAF', payload: { source: 'finance-collections-ui', receivedAt: new Date().toISOString() } }).subscribe({ next: value => { this.busy.set(false); this.providerResult.set(value); this.success.set(this.fr() ? 'Callback enregistrÃ©; aucune Ã©criture automatique nâ€™a Ã©tÃ© crÃ©Ã©e.' : 'Callback recorded; no automatic journal was created.'); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected initials(value: string): string { return value.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || '?'; }
  protected money(value: number | null | undefined): string { return `${Math.round(Number(value) || 0).toLocaleString('fr-FR')} XAF`; }
  private applyError(error: CollectionApiError): void { this.error.set(error?.error?.message || error?.message || (this.fr() ? 'Une erreur est survenue.' : 'Something went wrong.')); this.correlationId.set(error?.error?.correlationId || null); }
}

function today(): string { return new Date().toISOString().slice(0, 10); }
