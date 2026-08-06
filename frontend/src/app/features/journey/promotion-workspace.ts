import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FoundationApi, AcademicSessionView } from '../../core/foundation.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { I18nService } from '../../core/i18n.service';
import { IconComponent, CardComponent, PageHeaderComponent, KpiComponent, EmptyComponent } from '../../core/ui';
import { JourneyApi, ProgressionPathView, PromotionBatchView, PromotionCandidateView, PromotionRuleView } from './journey.api';

@Component({
  selector: 'bbc-promotion-workspace',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent, CardComponent, PageHeaderComponent, KpiComponent, EmptyComponent],
  template: `
    <div class="fade-in max-w-7xl mx-auto space-y-5">
      <bbc-page-header [title]="fr() ? 'Promotion de fin d’année' : 'End-of-year promotion'"
        [subtitle]="fr() ? 'Configurer le passage des classes, contrôler les recommandations et valider le transfert vers la prochaine session.' : 'Configure class progression, review recommendations and commit next-session enrollment.'">
        <a action routerLink="/journey" class="h-9 px-4 rounded-lg border border-slate-300 bg-white text-sm font-semibold text-ink inline-flex items-center gap-2 hover:bg-slate-50">
          <bbc-icon name="book" [s]="16" /> {{ fr() ? 'Chronologie élève' : 'Student timeline' }}
        </a>
      </bbc-page-header>

      <div class="rounded-xl border border-slate-200 bg-white p-2 flex gap-2">
        <button (click)="tab.set('config')" [class]="tabClass('config')">1. {{ fr() ? 'Règles & parcours' : 'Rules & paths' }}</button>
        <button (click)="tab.set('review')" [class]="tabClass('review')">2. {{ fr() ? 'Révision & validation' : 'Review & commit' }}</button>
      </div>

      @if (message(); as m) {
        <div class="rounded-xl border px-4 py-3 text-sm" [class]="m.error ? 'border-rose-300 bg-rose-50 text-rose-700' : 'border-emerald-300 bg-emerald-50 text-emerald-800'">
          {{ m.text }}
        </div>
      }

      @if (tab() === 'config') {
        <bbc-card [title]="fr() ? 'Sessions concernées' : 'Sessions in scope'"
          [subtitle]="fr() ? 'La session cible doit commencer après la fin de la session source.' : 'The target session must begin after the source session ends.'">
          <div class="grid md:grid-cols-2 gap-4">
            <label class="field-label">{{ fr() ? 'Session source' : 'Source session' }} <span class="required">*</span>
              <select [(ngModel)]="sourceSessionId" (ngModelChange)="scopeChanged()" [class]="fieldClass(!sourceSessionId)">
                <option value="">{{ fr() ? 'Choisir une session' : 'Choose a session' }}</option>
                @for (s of sessions(); track s.id) { <option [value]="s.id">{{ s.label }} · {{ s.status }}</option> }
              </select>
              @if (!sourceSessionId && attempted()) { <span class="field-error">{{ fr() ? 'La session source est obligatoire.' : 'Source session is required.' }}</span> }
            </label>
            <label class="field-label">{{ fr() ? 'Session cible' : 'Target session' }} <span class="required">*</span>
              <select [(ngModel)]="targetSessionId" (ngModelChange)="scopeChanged()" [class]="fieldClass(!targetSessionId)">
                <option value="">{{ fr() ? 'Choisir la prochaine session' : 'Choose the next session' }}</option>
                @for (s of targetSessions(); track s.id) { <option [value]="s.id">{{ s.label }} · {{ s.status }}</option> }
              </select>
              @if (!targetSessionId && attempted()) { <span class="field-error">{{ fr() ? 'La session cible est obligatoire.' : 'Target session is required.' }}</span> }
            </label>
          </div>
        </bbc-card>

        <div class="grid lg:grid-cols-3 gap-5">
          <bbc-card className="lg:col-span-1" [title]="fr() ? 'Règle de décision' : 'Decision rule'"
            [subtitle]="fr() ? 'Règle générale. Une moyenne entre les deux seuils passe en révision manuelle.' : 'Default rule. An average between both thresholds requires review.'">
            <div class="space-y-4">
              <label class="field-label">{{ fr() ? 'Promotion automatique à partir de' : 'Automatically promote from' }} <span class="required">*</span>
                <div class="relative"><input [(ngModel)]="ruleDraft.promoteMin" type="number" min="0" max="20" step="0.01" class="field pr-12" /><span class="unit">/20</span></div>
              </label>
              <label class="field-label">{{ fr() ? 'Redoublement recommandé sous' : 'Recommend repeat below' }} <span class="required">*</span>
                <div class="relative"><input [(ngModel)]="ruleDraft.reviewMin" type="number" min="0" max="20" step="0.01" class="field pr-12" /><span class="unit">/20</span></div>
              </label>
              <label class="flex gap-3 items-start rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
                <input [(ngModel)]="ruleDraft.requireFinalAverage" type="checkbox" class="mt-1" />
                <span><strong>{{ fr() ? 'Exiger une moyenne finale' : 'Require a final average' }}</strong><br><span class="text-mute">{{ fr() ? 'Sans moyenne, l’élève passe en révision.' : 'Without an average, the student requires review.' }}</span></span>
              </label>
              <button (click)="saveRule()" [disabled]="!sourceSessionId || busy()" class="primary-btn w-full">
                <bbc-icon name="check" [s]="16" /> {{ fr() ? 'Enregistrer la règle' : 'Save rule' }}
              </button>
            </div>
          </bbc-card>

          <bbc-card className="lg:col-span-2" [title]="fr() ? 'Parcours de classe' : 'Class progression paths'"
            [subtitle]="fr() ? 'Définissez exactement la classe cible de chaque classe. Marquez une classe terminale pour une sortie/diplôme.' : 'Define the exact next class for every class. Mark terminal classes for graduation.'">
            @if (!sourceSessionId || !targetSessionId) {
              <bbc-empty icon="route" [label]="fr() ? 'Choisissez les deux sessions pour configurer les passages.' : 'Choose both sessions to configure progression.'" />
            } @else {
              <div class="rounded-lg border border-slate-200 overflow-auto">
                <table class="w-full text-sm">
                  <thead class="bg-slate-50 text-left text-xs uppercase text-mute"><tr><th class="p-3">{{ fr() ? 'Classe actuelle' : 'Current class' }}</th><th class="p-3">{{ fr() ? 'Classe suivante' : 'Next class' }}</th><th class="p-3 text-center">{{ fr() ? 'Terminale' : 'Terminal' }}</th><th class="p-3"></th></tr></thead>
                  <tbody>
                    @for (c of classes(); track c.id) {
                      <tr class="border-t border-slate-100">
                        <td class="p-3"><div class="font-semibold text-ink">{{ c.name }}</div><div class="text-xs text-mute">{{ c.level }} · {{ c.subsystem }}</div></td>
                        <td class="p-3">
                          <select [ngModel]="targetFor(c.id)" (ngModelChange)="setTarget(c.id, $event)" [disabled]="terminalFor(c.id)" class="field min-w-44">
                            <option value="">{{ fr() ? 'Non configurée' : 'Not configured' }}</option>
                            @for (t of compatibleTargets(c); track t.id) { <option [value]="t.id">{{ t.name }}</option> }
                          </select>
                        </td>
                        <td class="p-3 text-center"><input type="checkbox" [ngModel]="terminalFor(c.id)" (ngModelChange)="setTerminal(c.id, $event)" /></td>
                        <td class="p-3 text-right"><button (click)="savePath(c)" class="secondary-btn">{{ fr() ? 'Enregistrer' : 'Save' }}</button></td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <div class="mt-3 text-xs text-mute">{{ configuredCount() }}/{{ classes().length }} {{ fr() ? 'classes configurées' : 'classes configured' }}</div>
            }
          </bbc-card>
        </div>
      } @else {
        <bbc-card [title]="fr() ? 'Créer un aperçu de promotion' : 'Create promotion preview'"
          [subtitle]="fr() ? 'L’aperçu ne transfère aucun élève. Il calcule les recommandations et signale ce qui doit être revu.' : 'Preview does not transfer students. It calculates recommendations and flags required review.'">
          <div class="grid md:grid-cols-3 gap-4 items-end">
            <label class="field-label">{{ fr() ? 'Session source' : 'Source session' }} <span class="required">*</span><select [(ngModel)]="sourceSessionId" (ngModelChange)="scopeChanged()" class="field"><option value="">—</option>@for (s of sessions(); track s.id) { <option [value]="s.id">{{ s.label }}</option> }</select></label>
            <label class="field-label">{{ fr() ? 'Session cible' : 'Target session' }} <span class="required">*</span><select [(ngModel)]="targetSessionId" (ngModelChange)="scopeChanged()" class="field"><option value="">—</option>@for (s of targetSessions(); track s.id) { <option [value]="s.id">{{ s.label }}</option> }</select></label>
            <label class="field-label">{{ fr() ? 'Nom du lot' : 'Batch name' }} <span class="required">*</span><input [(ngModel)]="batchName" [placeholder]="fr() ? 'Promotion 2026-2027' : 'Promotion 2026-2027'" [class]="fieldClass(!batchName.trim())" /></label>
          </div>
          <div class="mt-4 flex justify-end"><button (click)="preview()" [disabled]="busy()" class="primary-btn"><bbc-icon name="eye" [s]="16" /> {{ fr() ? 'Prévisualiser les décisions' : 'Preview decisions' }}</button></div>
        </bbc-card>

        @if (batch(); as b) {
          <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
            <bbc-kpi [label]="fr() ? 'Élèves' : 'Students'" [value]="b.candidateCount.toString()" icon="users" />
            <bbc-kpi [label]="fr() ? 'Promouvoir' : 'Promote'" [value]="b.promoteCount.toString()" icon="check" />
            <bbc-kpi [label]="fr() ? 'Redoubler' : 'Repeat'" [value]="b.repeatCount.toString()" icon="refresh" />
            <bbc-kpi [label]="fr() ? 'Diplômer' : 'Graduate'" [value]="b.graduateCount.toString()" icon="award" />
            <bbc-kpi [label]="fr() ? 'À réviser' : 'Needs review'" [value]="b.reviewCount.toString()" icon="alert" />
          </div>
          <bbc-card [title]="b.name" [subtitle]="b.sourceSessionLabel + ' → ' + b.targetSessionLabel">
            <div action class="flex items-center gap-2">
              <span class="px-2 py-1 rounded text-xs font-bold" [class]="b.status === 'COMMITTED' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'">{{ b.status }}</span>
              @if (b.status === 'DRAFT') { <button (click)="openCommit()" class="primary-btn"><bbc-icon name="check" [s]="16" /> {{ fr() ? 'Valider le lot' : 'Commit batch' }}</button> }
            </div>
            <div class="rounded-lg border border-slate-200 overflow-auto">
              <table class="w-full text-sm">
                <thead class="bg-slate-50 text-left text-xs uppercase text-mute"><tr><th class="p-3">{{ fr() ? 'Élève' : 'Student' }}</th><th class="p-3">{{ fr() ? 'Classe' : 'Class' }}</th><th class="p-3">{{ fr() ? 'Moyenne' : 'Average' }}</th><th class="p-3">{{ fr() ? 'Recommandation' : 'Recommendation' }}</th><th class="p-3">{{ fr() ? 'Décision finale' : 'Final decision' }}</th><th class="p-3"></th></tr></thead>
                <tbody>
                  @for (c of b.candidates; track c.id) {
                    <tr class="border-t border-slate-100" [class.bg-amber-50]="c.finalDecision === 'REVIEW'">
                      <td class="p-3"><div class="font-semibold text-ink">{{ c.studentName }}</div><div class="text-xs text-mute">{{ c.matricule }}</div></td>
                      <td class="p-3">{{ c.sourceClassName }}<div class="text-xs text-mute">→ {{ c.targetClassName || c.mappedTargetClassName || '—' }}</div></td>
                      <td class="p-3 font-semibold">{{ c.finalAverage == null ? '—' : c.finalAverage + '/20' }}</td>
                      <td class="p-3"><span [class]="decisionBadge(c.recommendation)">{{ decisionLabel(c.recommendation) }}</span><div class="text-xs text-mute mt-1 max-w-72">{{ c.explanation }}</div></td>
                      <td class="p-3"><span [class]="decisionBadge(c.finalDecision)">{{ decisionLabel(c.finalDecision) }}</span>@if (c.overrideReason) { <div class="text-xs text-mute mt-1">{{ c.overrideReason }}</div> }</td>
                      <td class="p-3 text-right">@if (b.status === 'DRAFT') { <button (click)="openOverride(c)" class="secondary-btn">{{ fr() ? 'Décider' : 'Decide' }}</button> }</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </bbc-card>
        }
      }
    </div>

    @if (overrideCandidate(); as c) {
      <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" (click)="closeOverride()">
        <div class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-lg p-5" (click)="$event.stopPropagation()">
          <h2 class="font-display text-xl font-bold text-ink">{{ fr() ? 'Décision administrative' : 'Administrative decision' }}</h2>
          <p class="text-sm text-mute mt-1">{{ c.studentName }} · {{ c.sourceClassName }} · {{ c.finalAverage == null ? '—' : c.finalAverage + '/20' }}</p>
          <div class="mt-5 space-y-4">
            <label class="field-label">{{ fr() ? 'Décision finale' : 'Final decision' }} <span class="required">*</span><select [(ngModel)]="overrideDraft.decision" class="field"><option value="PROMOTE">{{ decisionLabel('PROMOTE') }}</option><option value="REPEAT">{{ decisionLabel('REPEAT') }}</option><option value="HOLD">{{ decisionLabel('HOLD') }}</option><option value="GRADUATE">{{ decisionLabel('GRADUATE') }}</option></select></label>
            @if (overrideDraft.decision !== 'GRADUATE') { <label class="field-label">{{ fr() ? 'Classe cible' : 'Target class' }} <span class="required">*</span><select [(ngModel)]="overrideDraft.targetClassId" [class]="fieldClass(!overrideDraft.targetClassId)"><option value="">—</option>@for (t of compatibleTargets(classById(c.sourceClassId)); track t.id) { <option [value]="t.id">{{ t.name }}</option> }</select></label> }
            <label class="field-label">{{ fr() ? 'Motif de la décision' : 'Decision reason' }} <span class="required">*</span><textarea [(ngModel)]="overrideDraft.reason" rows="3" [class]="fieldClass(!overrideDraft.reason.trim())" [placeholder]="fr() ? 'Ex. décision du conseil de classe…' : 'E.g. class council decision…'"></textarea><span class="text-xs text-mute">{{ fr() ? 'Le motif est conservé dans l’historique d’audit.' : 'The reason is retained in the audit history.' }}</span></label>
          </div>
          <div class="mt-5 flex justify-end gap-2"><button (click)="closeOverride()" class="secondary-btn">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="saveOverride()" class="primary-btn">{{ fr() ? 'Appliquer la décision' : 'Apply decision' }}</button></div>
        </div>
      </div>
    }

    @if (commitOpen()) {
      <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" (click)="commitOpen.set(false)">
        <div class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-lg p-5" (click)="$event.stopPropagation()">
          <h2 class="font-display text-xl font-bold text-ink">{{ fr() ? 'Valider définitivement le lot' : 'Commit promotion batch' }}</h2>
          <div class="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">{{ fr() ? 'Cette action clôt les inscriptions de la session source et crée les inscriptions de la session cible. Le lot devient non modifiable.' : 'This closes source enrollments and creates target-session enrollments. The batch becomes read-only.' }}</div>
          <label class="field-label mt-4">{{ fr() ? 'Motif de validation' : 'Commit reason' }} <span class="required">*</span><textarea [(ngModel)]="commitReason" rows="3" [class]="fieldClass(!commitReason.trim())"></textarea></label>
          <div class="mt-5 flex justify-end gap-2"><button (click)="commitOpen.set(false)" class="secondary-btn">{{ fr() ? 'Retour' : 'Back' }}</button><button (click)="commit()" class="primary-btn">{{ fr() ? 'Confirmer le transfert' : 'Confirm transfer' }}</button></div>
        </div>
      </div>
    }
  `,
  styles: [`
    .field-label{display:flex;flex-direction:column;gap:.4rem;font-size:.82rem;font-weight:700;color:#334155}
    .field{width:100%;min-height:2.6rem;border:1px solid #cbd5e1;border-radius:.6rem;background:#fff;padding:.55rem .75rem;font-size:.875rem;color:#0f172a;outline:none}
    .field:focus{border-color:#2563eb;box-shadow:0 0 0 3px #dbeafe}.field-invalid{border-color:#e11d48!important;box-shadow:0 0 0 3px #ffe4e6!important}
    .field-error,.required{color:#e11d48}.field-error{font-size:.75rem;font-weight:500}.unit{position:absolute;right:.75rem;top:.62rem;color:#64748b;font-size:.8rem}
    .primary-btn,.secondary-btn{min-height:2.35rem;border-radius:.55rem;padding:.45rem .85rem;font-size:.82rem;font-weight:700;display:inline-flex;align-items:center;justify-content:center;gap:.4rem}
    .primary-btn{background:#1d4ed8;color:#fff}.primary-btn:hover{background:#1e40af}.primary-btn:disabled{opacity:.45;cursor:not-allowed}.secondary-btn{background:#fff;color:#334155;border:1px solid #cbd5e1}.secondary-btn:hover{background:#f8fafc}
  `],
})
export class PromotionWorkspaceComponent {
  private api = inject(JourneyApi); private foundation = inject(FoundationApi); private setup = inject(SetupApi); protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected tab = signal<'config'|'review'>('config'); protected sessions = signal<AcademicSessionView[]>([]); protected classes = signal<ClassView[]>([]);
  protected paths = signal<ProgressionPathView[]>([]); protected rules = signal<PromotionRuleView[]>([]); protected batch = signal<PromotionBatchView|null>(null);
  protected busy = signal(false); protected attempted = signal(false); protected message = signal<{text:string;error:boolean}|null>(null);
  protected overrideCandidate = signal<PromotionCandidateView|null>(null); protected commitOpen = signal(false);
  protected sourceSessionId=''; protected targetSessionId=''; protected batchName=''; protected commitReason='';
  protected ruleDraft={promoteMin:10,reviewMin:8,requireFinalAverage:true};
  protected pathDraft=signal(new Map<string,{targetClassId:string;terminal:boolean}>());
  protected overrideDraft={decision:'PROMOTE',targetClassId:'',reason:''};
  protected targetSessions=computed(()=>{const src=this.sessions().find(s=>s.id===this.sourceSessionId);return src?this.sessions().filter(s=>s.startDate>src.endDate):this.sessions();});
  protected configuredCount=computed(()=>this.paths().length);

  constructor(){forkJoin([this.foundation.listSessions(),this.setup.listClasses()]).subscribe({next:([s,c])=>{this.sessions.set(s);this.classes.set(c);const ordered=[...s].sort((a,b)=>a.startDate.localeCompare(b.startDate));this.sourceSessionId=ordered.find(x=>x.current)?.id??ordered[0]?.id??'';this.targetSessionId=ordered.find(x=>x.startDate>(ordered.find(y=>y.id===this.sourceSessionId)?.endDate??''))?.id??'';this.batchName=`Promotion ${ordered.find(x=>x.id===this.sourceSessionId)?.label??''}`;this.scopeChanged();},error:e=>this.fail(e)});}
  protected tabClass(t:string){return `flex-1 h-10 rounded-lg text-sm font-semibold ${this.tab()===t?'bg-brand-600 text-white':'text-mute hover:bg-slate-50'}`;}
  protected fieldClass(invalid:boolean){return `field ${invalid&&this.attempted()?'field-invalid':''}`;}
  protected scopeChanged(){this.message.set(null);this.batch.set(null);if(!this.sourceSessionId||!this.targetSessionId)return;forkJoin([this.api.progressionPaths(this.sourceSessionId,this.targetSessionId),this.api.promotionRules(this.sourceSessionId)]).subscribe({next:([p,r])=>{this.paths.set(p);this.rules.set(r);this.pathDraft.set(new Map(p.map(x=>[x.sourceClassId,{targetClassId:x.targetClassId??'',terminal:x.terminal}])));const rule=r.find(x=>!x.subsystem&&!x.level);if(rule)this.ruleDraft={promoteMin:rule.promoteMin,reviewMin:rule.reviewMin,requireFinalAverage:rule.requireFinalAverage};},error:e=>this.fail(e)});}
  protected targetFor(id:string){return this.pathDraft().get(id)?.targetClassId??'';} protected terminalFor(id:string){return this.pathDraft().get(id)?.terminal??false;}
  protected setTarget(id:string,value:string){const next=new Map(this.pathDraft());const d=next.get(id)??{targetClassId:'',terminal:false};next.set(id,{...d,targetClassId:value});this.pathDraft.set(next);}
  protected setTerminal(id:string,value:boolean){const next=new Map(this.pathDraft());const d=next.get(id)??{targetClassId:'',terminal:false};next.set(id,{targetClassId:value?'':d.targetClassId,terminal:value});this.pathDraft.set(next);}
  protected classById(id:string){return this.classes().find(c=>c.id===id)??this.classes()[0];} protected compatibleTargets(c:ClassView|undefined){return c?this.classes().filter(x=>x.subsystem===c.subsystem):this.classes();}
  protected savePath(c:ClassView){this.attempted.set(true);const d=this.pathDraft().get(c.id)??{targetClassId:'',terminal:false};if(!this.sourceSessionId||!this.targetSessionId||(!d.terminal&&!d.targetClassId)){this.message.set({text:this.fr()?'Choisissez une classe cible ou marquez la classe comme terminale.':'Choose a target class or mark it terminal.',error:true});return;}this.busy.set(true);this.api.saveProgressionPath({sourceSessionId:this.sourceSessionId,sourceClassId:c.id,targetSessionId:this.targetSessionId,targetClassId:d.terminal?null:d.targetClassId,terminal:d.terminal}).subscribe({next:()=>{this.busy.set(false);this.scopeChanged();this.ok(this.fr()?`Parcours de ${c.name} enregistré.`:`${c.name} path saved.`);},error:e=>{this.busy.set(false);this.fail(e)}});}
  protected saveRule(){this.attempted.set(true);if(!this.sourceSessionId||this.ruleDraft.reviewMin>this.ruleDraft.promoteMin){this.message.set({text:this.fr()?'Vérifiez les seuils : révision ≤ promotion.':'Check thresholds: review ≤ promotion.',error:true});return;}this.api.savePromotionRule({academicSessionId:this.sourceSessionId,subsystem:null,level:null,...this.ruleDraft}).subscribe({next:r=>{this.rules.set([r]);this.ok(this.fr()?'Règle enregistrée.':'Rule saved.');},error:e=>this.fail(e)});}
  protected preview(){this.attempted.set(true);if(!this.sourceSessionId||!this.targetSessionId||!this.batchName.trim()){this.message.set({text:this.fr()?'Complétez les sessions et le nom du lot.':'Complete sessions and batch name.',error:true});return;}this.busy.set(true);this.api.previewPromotion({sourceSessionId:this.sourceSessionId,targetSessionId:this.targetSessionId,name:this.batchName.trim(),idempotencyKey:crypto.randomUUID()}).subscribe({next:b=>{this.busy.set(false);this.batch.set(b);this.ok(this.fr()?'Aperçu créé. Aucun transfert n’a encore été effectué.':'Preview created. No transfer has been made yet.');},error:e=>{this.busy.set(false);this.fail(e)}});}
  protected openOverride(c:PromotionCandidateView){this.overrideCandidate.set(c);this.overrideDraft={decision:c.finalDecision==='REVIEW'?'PROMOTE':c.finalDecision,targetClassId:c.targetClassId??c.mappedTargetClassId??c.sourceClassId,reason:c.overrideReason??''};}
  protected closeOverride(){this.overrideCandidate.set(null);} protected saveOverride(){const c=this.overrideCandidate();if(!c||!this.overrideDraft.reason.trim()||(this.overrideDraft.decision!=='GRADUATE'&&!this.overrideDraft.targetClassId)){this.message.set({text:this.fr()?'La classe cible et le motif sont obligatoires.':'Target class and reason are required.',error:true});return;}this.api.overrideDecision(c.id,{finalDecision:this.overrideDraft.decision,targetClassId:this.overrideDraft.decision==='GRADUATE'?null:this.overrideDraft.targetClassId,reason:this.overrideDraft.reason.trim(),version:c.version}).subscribe({next:()=>{this.closeOverride();this.reloadBatch();this.ok(this.fr()?'Décision enregistrée et auditée.':'Decision saved and audited.');},error:e=>this.fail(e)});}
  protected openCommit(){const b=this.batch();if(!b)return;if(b.reviewCount){this.message.set({text:this.fr()?`${b.reviewCount} décision(s) sont encore à réviser.`:`${b.reviewCount} decision(s) still need review.`,error:true});return;}this.commitReason='Validation du conseil de classe';this.commitOpen.set(true);}
  protected commit(){const b=this.batch();if(!b||!this.commitReason.trim())return;this.busy.set(true);this.api.commitPromotion(b.id,this.commitReason.trim(),b.version).subscribe({next:x=>{this.busy.set(false);this.commitOpen.set(false);this.batch.set(x);this.ok(this.fr()?'Lot validé : les inscriptions de la prochaine session ont été créées.':'Batch committed: next-session enrollments were created.');},error:e=>{this.busy.set(false);this.fail(e)}});}
  protected decisionLabel(d:string){const fr:Record<string,string>={PROMOTE:'Promouvoir',REPEAT:'Redoubler',REVIEW:'À réviser',GRADUATE:'Diplômer',HOLD:'Maintenir'};const en:Record<string,string>={PROMOTE:'Promote',REPEAT:'Repeat',REVIEW:'Review',GRADUATE:'Graduate',HOLD:'Hold back'};return (this.fr()?fr:en)[d]??d;}
  protected decisionBadge(d:string){return `inline-flex px-2 py-1 rounded text-xs font-bold ${d==='PROMOTE'?'bg-emerald-100 text-emerald-700':d==='GRADUATE'?'bg-violet-100 text-violet-700':d==='REVIEW'?'bg-amber-100 text-amber-700':'bg-rose-100 text-rose-700'}`;}
  private reloadBatch(){const b=this.batch();if(b)this.api.promotionBatch(b.id).subscribe(x=>this.batch.set(x));} private ok(text:string){this.message.set({text,error:false});} private fail(e:any){this.message.set({text:e?.error?.message??(this.fr()?'Opération impossible.':'Operation failed.'),error:true});}
}
