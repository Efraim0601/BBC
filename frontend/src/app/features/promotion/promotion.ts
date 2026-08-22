import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent,
  KpiComponent, TabsComponent, ConfirmComponent,
} from '../../core/ui';
import {
  PromotionApi, PromotionConfig, PromotionPreview, ProgressionView, RuleView, RuleUpsert,
  CandidateView, PromotionResult, BatchView, ClosurePreview, ClosureResult, ClosureView,
} from './promotion.api';

interface ResultMeta { fr: string; en: string; badge: string; }

/** Ce que l'administration peut réellement acter en fin d'année. */
const APPLICABLE = ['promoted', 'repeated', 'graduated', 'transferred_out', 'excluded'];

@Component({
  selector: 'bbc-promotion',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent, TabsComponent, ConfirmComponent, RouterLink,
  ],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Passage de classe' : 'Class promotion'"
        [subtitle]="fr()
          ? 'Fin d’année : proposition automatique, arbitrage du conseil, application'
          : 'End of year: automatic proposal, council override, application'" />

      <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="switchTab($event)" />
      <div class="mb-4 flex justify-end"><a routerLink="/pathways" class="inline-flex items-center gap-2 rounded-lg border border-brand-200 bg-brand-50 px-3 py-2 text-xs font-bold text-brand-700 hover:bg-brand-100"><bbc-icon name="route" [s]="15" />{{ fr() ? 'Choix manuel du parcours bilingue' : 'Manual bilingual pathway choice' }}</a></div>

      @if (error()) {
        <div class="mb-4 px-4 py-3 rounded-lg bg-rose-50 border border-rose-200 text-sm text-rose-700 flex items-start gap-2">
          <bbc-icon name="alert" [s]="16" class="mt-0.5 shrink-0" />
          <span class="flex-1">{{ error() }}</span>
          <button (click)="error.set(null)" class="text-rose-400 hover:text-rose-600"><bbc-icon name="x" [s]="14" /></button>
        </div>
      }

      <!-- ================= 1. PASSAGE ================= -->
      @if (tab() === 'run') {
        <div class="space-y-4">
          <bbc-card>
            <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
              <label class="block">
                <span class="text-[11px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Classe' : 'Class' }}</span>
                <select [ngModel]="classId()" (ngModelChange)="pickClass($event)"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white font-semibold focus:outline-none focus:border-brand-400">
                  <option value="">{{ fr() ? '— Choisir une classe —' : '— Pick a class —' }}</option>
                  @for (p of progression(); track p.classId) {
                    <option [value]="p.classId">{{ p.className }} · {{ p.sectionLabel }}</option>
                  }
                </select>
              </label>
              <label class="block">
                <span class="text-[11px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Année qui se termine' : 'Year ending' }}</span>
                <input [ngModel]="year()" (ngModelChange)="changeYear($event)" placeholder="2025-2026"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block">
                <span class="text-[11px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Année d’accueil' : 'Receiving year' }}</span>
                <input [(ngModel)]="nextYearInput" placeholder="2026-2027"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              </label>
              <div class="flex items-end">
                <button (click)="reload()" [disabled]="!classId() || busy()"
                  class="w-full h-10 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200 disabled:opacity-40">
                  <bbc-icon name="history" [s]="15" class="inline-block mr-1.5 align-[-2px]" />
                  {{ fr() ? 'Recalculer' : 'Recompute' }}
                </button>
              </div>
            </div>
          </bbc-card>

          @if (!preview()) {
            <bbc-card>
              <bbc-empty icon="cap"
                [label]="fr() ? 'Choisissez une classe pour simuler son passage.' : 'Pick a class to simulate its promotion.'" />
            </bbc-card>
          } @else {
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
              <bbc-kpi [label]="fr() ? 'Effectif' : 'Headcount'" [value]="preview()!.total.toString()"
                [sub]="preview()!.graded + (fr() ? ' noté(s)' : ' graded')" icon="users" />
              <bbc-kpi [label]="fr() ? 'Seuil d’admission' : 'Pass mark'" [value]="preview()!.passMark + '/20'"
                [sub]="preview()!.ruleScope" icon="chart" />
              <bbc-kpi [label]="fr() ? 'Classe d’accueil' : 'Receiving class'"
                [value]="preview()!.nextClassName ?? (preview()!.terminal ? (fr() ? 'Sortie' : 'Exit') : '—')"
                [sub]="preview()!.nextAcademicYear" icon="route" />
              <bbc-kpi [label]="fr() ? 'Décisions prêtes' : 'Decisions ready'"
                [value]="readyCount() + ' / ' + preview()!.candidates.length"
                [sub]="overriddenCount() + (fr() ? ' arbitrage(s)' : ' override(s)')" icon="check" />
            </div>

            @for (w of preview()!.warnings; track w) {
              <div class="px-4 py-2.5 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-800 flex items-start gap-2">
                <bbc-icon name="alert" [s]="15" class="mt-0.5 shrink-0" /><span>{{ w }}</span>
              </div>
            }

            @if (lastResult(); as r) {
              <div class="px-4 py-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
                <span class="font-semibold">{{ fr() ? 'Passage appliqué' : 'Promotion applied' }}</span> —
                {{ r.applied }} {{ fr() ? 'élève(s)' : 'student(s)' }} ·
                {{ r.promoted }} {{ fr() ? 'admis' : 'promoted' }} ·
                {{ r.repeated }} {{ fr() ? 'redoublant(s)' : 'repeating' }} ·
                {{ r.graduated }} {{ fr() ? 'diplômé(s)' : 'graduated' }} ·
                {{ r.overridden }} {{ fr() ? 'arbitrage(s) manuel(s)' : 'manual override(s)' }}.
              </div>
            }

            <bbc-card [title]="preview()!.className"
              [subtitle]="(fr() ? 'Année ' : 'Year ') + preview()!.academicYear + ' → ' + nextYearInput">
              <div action class="flex items-center gap-2">
                <button (click)="resetToProposals()"
                  class="h-9 px-3 rounded-lg bg-slate-100 text-xs font-semibold text-ink hover:bg-slate-200">
                  {{ fr() ? 'Reprendre les propositions' : 'Reset to proposals' }}
                </button>
                @if (canRun) {
                  <button (click)="askApply()" [disabled]="readyCount() === 0 || busy()"
                    class="h-9 px-4 rounded-lg bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 disabled:opacity-40">
                    <bbc-icon name="check" [s]="14" class="inline-block mr-1 align-[-2px]" />
                    {{ fr() ? 'Appliquer' : 'Apply' }} ({{ readyCount() }})
                  </button>
                }
              </div>

              @if (preview()!.candidates.length === 0) {
                <bbc-empty icon="users" [label]="fr() ? 'Aucun élève actif dans cette classe.' : 'No active student in this class.'" />
              } @else {
                <div class="overflow-x-auto -mx-5">
                  <table class="w-full text-sm min-w-[64rem]">
                    <thead>
                      <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                        <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Élève' : 'Student' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Moyenne' : 'Average' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Rang' : 'Rank' }}</th>
                        <th class="text-center font-semibold py-2 px-2">{{ fr() ? 'Redoubl.' : 'Repeats' }}</th>
                        <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Proposition' : 'Proposal' }}</th>
                        <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Décision retenue' : 'Final decision' }}</th>
                        <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Classe d’accueil' : 'Receiving class' }}</th>
                        <th class="text-left font-semibold py-2 pr-5">{{ fr() ? 'Motif de l’arbitrage' : 'Override reason' }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (c of preview()!.candidates; track c.studentId) {
                        <tr class="border-b border-slate-50 last:border-0 align-middle"
                          [class]="isOverridden(c) ? 'bg-amber-50/40' : ''">
                          <td class="py-2 pl-5">
                            <div class="flex items-center gap-2.5">
                              <bbc-avatar [name]="c.studentName" [hue]="c.photoHue" [size]="32" />
                              <div class="min-w-0">
                                <div class="font-semibold text-ink truncate">{{ c.studentName }}</div>
                                <div class="text-[11px] text-mute font-mono">{{ c.matricule }}</div>
                              </div>
                            </div>
                          </td>
                          <td class="py-2 px-2 text-right font-semibold"
                            [class]="c.annualAverage == null ? 'text-slate-300'
                                     : (c.annualAverage >= preview()!.passMark ? 'text-emerald-600' : 'text-rose-600')">
                            {{ c.annualAverage != null ? c.annualAverage + '/20' : '—' }}
                            @if (c.sequencesCounted > 0) {
                              <div class="text-[10px] text-mute font-normal">
                                {{ c.sequencesCounted }} {{ fr() ? 'séq.' : 'seq.' }}
                              </div>
                            }
                          </td>
                          <td class="py-2 px-2 text-right text-mute">
                            {{ c.rank != null ? c.rank + '/' + c.classSize : '—' }}
                          </td>
                          <td class="py-2 px-2 text-center">
                            @if (c.priorRepeats > 0) {
                              <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-amber-100 text-amber-700 text-[11px] font-bold">
                                {{ c.priorRepeats }}
                              </span>
                            } @else { <span class="text-slate-300">—</span> }
                          </td>
                          <td class="py-2 px-2">
                            <span class="inline-flex items-center gap-1.5 text-[11px] font-semibold px-2 py-1 rounded-full"
                              [class]="meta(c.proposedResult).badge">
                              {{ label(c.proposedResult) }}
                            </span>
                            <div class="text-[10px] text-mute mt-0.5 max-w-[15rem]">{{ c.proposalReason }}</div>
                            @if (c.appliedResult) {
                              <div class="text-[10px] text-sky-600 font-semibold mt-0.5">
                                {{ fr() ? 'Déjà appliqué : ' : 'Already applied: ' }}{{ label(c.appliedResult) }}
                              </div>
                            }
                          </td>
                          <td class="py-2 px-2">
                            <select [ngModel]="row(c).result" (ngModelChange)="setResult(c, $event)"
                              [disabled]="!canRun"
                              class="h-9 px-2 rounded-lg border text-xs bg-white focus:outline-none focus:border-brand-400 min-w-[9rem]"
                              [class]="isOverridden(c) ? 'border-amber-300 font-semibold' : 'border-slate-200'">
                              <option value="">{{ fr() ? '— À trancher —' : '— Undecided —' }}</option>
                              @for (r of applicable; track r) {
                                <option [value]="r">{{ label(r) }}</option>
                              }
                            </select>
                          </td>
                          <td class="py-2 px-2">
                            @if (row(c).result === 'promoted') {
                              <select [ngModel]="row(c).toClassId" (ngModelChange)="setTarget(c, $event)"
                                [disabled]="!canRun"
                                class="h-9 px-2 rounded-lg border border-slate-200 text-xs bg-white focus:outline-none focus:border-brand-400 min-w-[9rem]">
                                <option value="">{{ fr() ? '— Choisir —' : '— Pick —' }}</option>
                                @for (p of progression(); track p.classId) {
                                  @if (p.classId !== preview()!.classId) {
                                    <option [value]="p.classId">{{ p.className }}</option>
                                  }
                                }
                              </select>
                            } @else if (row(c).result === 'repeated') {
                              <span class="text-xs text-mute">{{ preview()!.className }}</span>
                            } @else {
                              <span class="text-xs text-slate-300">—</span>
                            }
                          </td>
                          <td class="py-2 pr-5">
                            @if (isOverridden(c)) {
                              <input [ngModel]="row(c).reason" (ngModelChange)="setReason(c, $event)"
                                [disabled]="!canRun"
                                [placeholder]="fr() ? 'Motif obligatoire…' : 'Reason required…'"
                                class="h-9 px-2 w-full min-w-[12rem] rounded-lg border text-xs focus:outline-none focus:border-brand-400"
                                [class]="row(c).reason.trim() ? 'border-slate-200' : 'border-rose-300 bg-rose-50'" />
                            } @else {
                              <span class="text-xs text-slate-300">—</span>
                            }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </bbc-card>
          }
        </div>
      }

      <!-- ================= 2. PROGRESSION & RÈGLES ================= -->
      @if (tab() === 'config') {
        <div class="space-y-4">
          <bbc-card [title]="fr() ? 'Progression des classes' : 'Class progression'"
            [subtitle]="fr()
              ? 'Vers quelle classe un élève admis est transféré l’année suivante'
              : 'Which class a promoted student moves to next year'">
            <div action class="flex items-center gap-2">
              @if (canConfig) {
                <button (click)="autoFill()" [disabled]="busy()"
                  class="h-9 px-3 rounded-lg bg-slate-100 text-xs font-semibold text-ink hover:bg-slate-200 disabled:opacity-40">
                  <bbc-icon name="spark" [s]="14" class="inline-block mr-1 align-[-2px]" />
                  {{ fr() ? 'Déduire automatiquement' : 'Auto-detect' }}
                </button>
                <button (click)="saveProgression()" [disabled]="busy()"
                  class="h-9 px-4 rounded-lg bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 disabled:opacity-40">
                  {{ i18n.t('save') }}
                </button>
              }
            </div>

            @if (progression().length === 0) {
              <bbc-empty icon="building" [label]="fr() ? 'Aucune classe dans ce parcours.' : 'No class in this parcours.'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm min-w-[46rem]">
                  <thead>
                    <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Section' : 'Section' }}</th>
                      <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Classe' : 'Class' }}</th>
                      <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Effectif' : 'Students' }}</th>
                      <th class="text-center font-semibold py-2 px-2">{{ fr() ? 'Ordre' : 'Order' }}</th>
                      <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Classe suivante' : 'Next class' }}</th>
                      <th class="text-center font-semibold py-2 pr-5">{{ fr() ? 'Classe de sortie' : 'Exit class' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (p of progression(); track p.classId) {
                      <tr class="border-b border-slate-50 last:border-0">
                        <td class="py-2 pl-5 text-mute text-xs">{{ p.sectionLabel }}</td>
                        <td class="py-2 px-2 font-semibold text-ink">{{ p.className }}</td>
                        <td class="py-2 px-2 text-right text-mute">{{ p.studentCount }}</td>
                        <td class="py-2 px-2 text-center">
                          <input type="number" min="0" [ngModel]="prog(p).gradeOrder" (ngModelChange)="setOrder(p, $event)"
                            [disabled]="!canConfig"
                            class="h-9 w-16 px-2 text-center rounded-lg border border-slate-200 text-xs focus:outline-none focus:border-brand-400" />
                        </td>
                        <td class="py-2 px-2">
                          <select [ngModel]="prog(p).nextClassId ?? ''" (ngModelChange)="setNext(p, $event)"
                            [disabled]="!canConfig || prog(p).terminal"
                            class="h-9 px-2 rounded-lg border border-slate-200 text-xs bg-white focus:outline-none focus:border-brand-400 min-w-[10rem] disabled:bg-slate-50">
                            <option value="">{{ fr() ? '— Aucune —' : '— None —' }}</option>
                            @for (t of progression(); track t.classId) {
                              @if (t.classId !== p.classId) {
                                <option [value]="t.classId">{{ t.className }}</option>
                              }
                            }
                          </select>
                        </td>
                        <td class="py-2 pr-5 text-center">
                          <input type="checkbox" [ngModel]="prog(p).terminal" (ngModelChange)="setTerminal(p, $event)"
                            [disabled]="!canConfig"
                            class="w-4 h-4 accent-brand-600" />
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <p class="text-xs text-mute mt-3">
                {{ fr()
                  ? 'Une classe de sortie (Terminale, Upper Sixth) n’a pas de classe suivante : y réussir vaut « Diplômé ».'
                  : 'An exit class (Terminale, Upper Sixth) has no next class: passing it means “Graduated”.' }}
              </p>
            }
          </bbc-card>

          <bbc-card [title]="fr() ? 'Règles de décision' : 'Decision rules'"
            [subtitle]="fr()
              ? 'La règle la plus précise l’emporte : classe > parcours > école'
              : 'Most specific rule wins: class > parcours > school'">
            <div class="overflow-x-auto -mx-5">
              <table class="w-full text-sm min-w-[42rem]">
                <thead>
                  <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                    <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Périmètre' : 'Scope' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Seuil' : 'Pass mark' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Zone conseil' : 'Council zone' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Redoubl. max' : 'Max repeats' }}</th>
                    <th class="py-2 pr-5"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of rules(); track r.id) {
                    <tr class="border-b border-slate-50 last:border-0">
                      <td class="py-2 pl-5 font-semibold text-ink">{{ r.scopeLabel }}</td>
                      <td class="py-2 px-2 text-right">{{ r.passMark }}/20</td>
                      <td class="py-2 px-2 text-right text-mute">
                        {{ r.councilMargin > 0 ? '−' + r.councilMargin : '—' }}
                      </td>
                      <td class="py-2 px-2 text-right text-mute">{{ r.maxRepeats ?? '—' }}</td>
                      <td class="py-2 pr-5 text-right">
                        @if (canConfig) {
                          <button (click)="editRule(r)" class="w-7 h-7 rounded text-mute hover:text-brand-600 hover:bg-brand-50">
                            <bbc-icon name="edit" [s]="14" />
                          </button>
                          @if (r.specificity > 0) {
                            <button (click)="removeRule(r)" class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50">
                              <bbc-icon name="trash" [s]="14" />
                            </button>
                          }
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (canConfig) {
              <div class="mt-4 pt-4 border-t border-slate-100">
                <div class="text-xs font-semibold text-ink mb-2">
                  {{ ruleDraft.id ? (fr() ? 'Modifier la règle' : 'Edit rule') : (fr() ? 'Nouvelle règle' : 'New rule') }}
                </div>
                <div class="grid grid-cols-1 md:grid-cols-6 gap-2.5">
                  <select [(ngModel)]="ruleDraft.classId"
                    class="h-10 px-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    <option [ngValue]="null">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>
                    @for (p of progression(); track p.classId) {
                      <option [ngValue]="p.classId">{{ p.className }}</option>
                    }
                  </select>
                  <select [(ngModel)]="ruleDraft.level" [disabled]="!!ruleDraft.classId"
                    class="h-10 px-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-50">
                    <option [ngValue]="null">{{ fr() ? 'Tous niveaux' : 'All levels' }}</option>
                    <option value="maternelle">{{ fr() ? 'Maternelle' : 'Nursery' }}</option>
                    <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                    <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
                  </select>
                  <select [(ngModel)]="ruleDraft.subsystem" [disabled]="!!ruleDraft.classId"
                    class="h-10 px-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-50">
                    <option [ngValue]="null">FR + EN</option>
                    <option value="FR">FR</option>
                    <option value="EN">EN</option>
                  </select>
                  <input type="number" step="0.25" min="0" max="20" [(ngModel)]="ruleDraft.passMark"
                    [placeholder]="fr() ? 'Seuil (10)' : 'Pass mark (10)'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input type="number" step="0.25" min="0" max="20" [(ngModel)]="ruleDraft.councilMargin"
                    [placeholder]="fr() ? 'Zone conseil (1)' : 'Council zone (1)'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input type="number" min="0" [(ngModel)]="ruleDraft.maxRepeats"
                    [placeholder]="fr() ? 'Redoubl. max' : 'Max repeats'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                </div>
                <div class="flex items-center gap-2 mt-3">
                  <button (click)="saveRule()" [disabled]="busy()"
                    class="h-9 px-4 rounded-lg bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 disabled:opacity-40">
                    {{ i18n.t('save') }}
                  </button>
                  @if (ruleDraft.id) {
                    <button (click)="resetRuleDraft()" class="h-9 px-3 rounded-lg bg-slate-100 text-xs font-semibold text-ink hover:bg-slate-200">
                      {{ i18n.t('cancel') }}
                    </button>
                  }
                </div>
                <p class="text-xs text-mute mt-3">
                  {{ fr()
                    ? 'Zone conseil : largeur, sous le seuil, où l’élève est renvoyé au conseil au lieu d’être proposé redoublant. Redoubl. max : au-delà, plus aucun redoublement n’est proposé automatiquement.'
                    : 'Council zone: band below the pass mark where the student goes to the council instead of being proposed for repeat. Max repeats: beyond it, no repeat is proposed automatically.' }}
                </p>
              </div>
            }
          </bbc-card>
        </div>
      }

      <!-- ================= 3. CLÔTURE DE L'ANNÉE ================= -->
      @if (tab() === 'closure') {
        @if (closure(); as k) {
          <div class="space-y-4">
            <bbc-card [title]="fr() ? 'Clôturer ' + k.academicYear : 'Close ' + k.academicYear"
              [subtitle]="fr()
                ? 'Archive l’année écoulée, rouvre les compteurs et bascule l’établissement sur ' + k.nextAcademicYear
                : 'Archives the closing year, resets the counters and moves the school to ' + k.nextAcademicYear">

              @if (k.closedAt) {
                <div class="px-4 py-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800 mb-4">
                  <span class="font-semibold">{{ fr() ? 'Année déjà clôturée' : 'Year already closed' }}</span>
                  — {{ k.closedAt | date: 'dd/MM/yyyy HH:mm' }}.
                  {{ fr() ? 'Une seconde clôture est refusée.' : 'A second closure is refused.' }}
                </div>
              }

              <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
                <bbc-kpi [label]="fr() ? 'Élèves actifs' : 'Active students'" [value]="k.activeStudents.toString()"
                  [sub]="k.studentsDecided + (fr() ? ' avec décision' : ' with a decision')" icon="users"
                  [tone]="k.studentsPending > 0 ? 'warn' : 'ok'" />
                <bbc-kpi [label]="fr() ? 'Notes à archiver' : 'Grades to archive'"
                  [value]="k.gradesToArchive.toString()"
                  [sub]="k.validationsToArchive + (fr() ? ' bulletins validés' : ' validated reports')" icon="book" />
                <bbc-kpi [label]="fr() ? 'Scolarités à figer' : 'Fees to freeze'" [value]="k.feesToArchive.toString()"
                  [sub]="k.feesToCreate + (fr() ? ' à rouvrir' : ' to reopen')" icon="wallet" />
                <bbc-kpi [label]="fr() ? 'Nouvelle année' : 'New year'" [value]="k.nextAcademicYear"
                  [sub]="fr() ? 'deviendra l’année courante' : 'will become the current year'" icon="calendar" />
              </div>

              @for (w of k.warnings; track w) {
                <div class="px-4 py-2.5 mb-2 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-800 flex items-start gap-2">
                  <bbc-icon name="alert" [s]="15" class="mt-0.5 shrink-0" /><span>{{ w }}</span>
                </div>
              }

              @if (k.pendingClasses.length) {
                <div class="px-4 py-3 rounded-lg bg-rose-50 border border-rose-200 text-sm text-rose-800 mb-2">
                  <div class="font-semibold mb-1">{{ fr() ? 'Classes à terminer' : 'Classes still to finish' }}</div>
                  <div class="flex flex-wrap gap-1.5">
                    @for (p of k.pendingClasses; track p.className) {
                      <span class="px-2 py-0.5 rounded-full bg-white border border-rose-200 text-[11px] font-semibold">
                        {{ p.className }} · {{ p.students }}
                      </span>
                    }
                  </div>
                </div>
              }

              <div class="mt-4 pt-4 border-t border-slate-100 space-y-2.5">
                <label class="flex items-start gap-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="closureDraft.archiveGrades" class="mt-0.5 w-4 h-4 accent-brand-600" />
                  <span>
                    <span class="font-semibold text-ink">{{ fr() ? 'Archiver les notes' : 'Archive the grades' }}</span>
                    <span class="block text-xs text-mute">{{ fr()
                      ? 'Notes et bulletins validés sont recopiés sous ' + k.academicYear + ', puis les séquences repartent à vide.'
                      : 'Grades and validated reports are copied under ' + k.academicYear + ', then the sequences start empty.' }}</span>
                  </span>
                </label>
                <label class="flex items-start gap-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="closureDraft.resetFees" class="mt-0.5 w-4 h-4 accent-brand-600" />
                  <span>
                    <span class="font-semibold text-ink">{{ fr() ? 'Rouvrir les scolarités' : 'Reopen the school fees' }}</span>
                    <span class="block text-xs text-mute">{{ fr()
                      ? 'Les états de compte sont figés, puis régénérés au tarif de la nouvelle classe. Les encaissements passés restent dans l’historique.'
                      : 'Statements are frozen, then regenerated at the new class rate. Past payments stay in the history.' }}</span>
                  </span>
                </label>
                <label class="flex items-start gap-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="closureDraft.makeCurrent" class="mt-0.5 w-4 h-4 accent-brand-600" />
                  <span>
                    <span class="font-semibold text-ink">{{ fr() ? 'Basculer l’année courante' : 'Switch the current year' }}</span>
                    <span class="block text-xs text-mute">{{ fr()
                      ? k.nextAcademicYear + ' devient l’année de l’établissement : bulletins, reçus et documents la porteront.'
                      : k.nextAcademicYear + ' becomes the school year: report cards, receipts and documents will carry it.' }}</span>
                  </span>
                </label>
                @if (k.studentsPending > 0) {
                  <label class="flex items-start gap-2.5 text-sm">
                    <input type="checkbox" [(ngModel)]="closureDraft.ignorePending" class="mt-0.5 w-4 h-4 accent-rose-600" />
                    <span>
                      <span class="font-semibold text-rose-700">{{ fr() ? 'Clôturer malgré tout' : 'Close anyway' }}</span>
                      <span class="block text-xs text-mute">{{ fr()
                        ? k.studentsPending + ' élève(s) seront archivés sans décision de fin d’année.'
                        : k.studentsPending + ' student(s) will be archived with no end-of-year decision.' }}</span>
                    </span>
                  </label>
                }
              </div>

              @if (!k.closedAt) {
                <div class="mt-4 pt-4 border-t border-slate-100">
                  <div class="text-xs text-mute mb-2">{{ fr()
                    ? 'Cette opération est irréversible. Saisissez « ' + k.academicYear + ' » pour la confirmer.'
                    : 'This cannot be undone. Type “' + k.academicYear + '” to confirm.' }}</div>
                  <div class="flex items-center gap-2">
                    <input [(ngModel)]="closureConfirm" [placeholder]="k.academicYear"
                      class="h-10 px-3 w-44 rounded-lg border border-slate-200 text-sm font-mono focus:outline-none focus:border-brand-400" />
                    <button (click)="doClose()" [disabled]="!closureArmed() || busy()"
                      class="h-10 px-5 rounded-lg bg-rose-600 text-white text-sm font-semibold hover:bg-rose-700 disabled:opacity-40">
                      <bbc-icon name="check" [s]="15" class="inline-block mr-1.5 align-[-2px]" />
                      {{ fr() ? 'Clôturer l’année' : 'Close the year' }}
                    </button>
                  </div>
                </div>
              }
            </bbc-card>

            @if (closureResult(); as r) {
              <div class="px-4 py-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
                <span class="font-semibold">{{ fr() ? 'Année clôturée' : 'Year closed' }}</span> —
                {{ r.gradesArchived }} {{ fr() ? 'notes archivées' : 'grades archived' }} ·
                {{ r.validationsArchived }} {{ fr() ? 'bulletins' : 'reports' }} ·
                {{ r.feesArchived }} {{ fr() ? 'scolarités figées' : 'fees frozen' }} ·
                {{ r.feesCreated }} {{ fr() ? 'rouvertes' : 'reopened' }}{{ r.madeCurrent ? ' · ' + r.nextAcademicYear + (fr() ? ' est l’année courante' : ' is the current year') : '' }}.
              </div>
            }

            <bbc-card [title]="fr() ? 'Clôtures précédentes' : 'Previous closures'">
              @if (closures().length === 0) {
                <bbc-empty icon="history" [label]="fr() ? 'Aucune année clôturée à ce jour.' : 'No year closed yet.'" />
              } @else {
                <div class="overflow-x-auto -mx-5">
                  <table class="w-full text-sm min-w-[42rem]">
                    <thead>
                      <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                        <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Année' : 'Year' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Notes' : 'Grades' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Bulletins' : 'Reports' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Scolarités' : 'Fees' }}</th>
                        <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Sans décision' : 'Undecided' }}</th>
                        <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Par' : 'By' }}</th>
                        <th class="text-left font-semibold py-2 pr-5">{{ fr() ? 'Date' : 'Date' }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (c of closures(); track c.id) {
                        <tr class="border-b border-slate-50 last:border-0">
                          <td class="py-2 pl-5 font-semibold text-ink">{{ c.academicYear }} → {{ c.nextAcademicYear }}</td>
                          <td class="py-2 px-2 text-right">{{ c.gradesArchived }}</td>
                          <td class="py-2 px-2 text-right">{{ c.validationsArchived }}</td>
                          <td class="py-2 px-2 text-right">{{ c.feesArchived }} → {{ c.feesCreated }}</td>
                          <td class="py-2 px-2 text-right" [class]="c.studentsPending > 0 ? 'text-rose-600 font-semibold' : 'text-mute'">
                            {{ c.studentsPending }}
                          </td>
                          <td class="py-2 px-2 text-mute text-xs">{{ c.closedBy ?? '—' }}</td>
                          <td class="py-2 pr-5 text-mute text-xs">{{ c.closedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </bbc-card>
          </div>
        }
      }

      <!-- ================= 4. HISTORIQUE ================= -->
      @if (tab() === 'history') {
        <bbc-card [title]="fr() ? 'Lots appliqués' : 'Applied batches'"
          [subtitle]="fr() ? '50 derniers passages de classe' : 'Last 50 promotions'">
          @if (batches().length === 0) {
            <bbc-empty icon="history" [label]="fr() ? 'Aucun passage appliqué à ce jour.' : 'No promotion applied yet.'" />
          } @else {
            <div class="overflow-x-auto -mx-5">
              <table class="w-full text-sm min-w-[44rem]">
                <thead>
                  <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                    <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Date' : 'Date' }}</th>
                    <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Classe' : 'Class' }}</th>
                    <th class="text-left font-semibold py-2 px-2">{{ fr() ? 'Années' : 'Years' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Élèves' : 'Students' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Admis' : 'Promoted' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Redoubl.' : 'Repeated' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Diplômés' : 'Graduated' }}</th>
                    <th class="text-right font-semibold py-2 px-2">{{ fr() ? 'Arbitrages' : 'Overrides' }}</th>
                    <th class="text-left font-semibold py-2 pr-5">{{ fr() ? 'Par' : 'By' }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (b of batches(); track b.id) {
                    <tr class="border-b border-slate-50 last:border-0">
                      <td class="py-2 pl-5 text-mute text-xs">{{ b.appliedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td class="py-2 px-2 font-semibold text-ink">{{ b.className }}</td>
                      <td class="py-2 px-2 text-mute text-xs">{{ b.academicYear }} → {{ b.nextAcademicYear }}</td>
                      <td class="py-2 px-2 text-right">{{ b.studentsTotal }}</td>
                      <td class="py-2 px-2 text-right text-emerald-600 font-semibold">{{ b.promotedCount }}</td>
                      <td class="py-2 px-2 text-right text-amber-600 font-semibold">{{ b.repeatedCount }}</td>
                      <td class="py-2 px-2 text-right text-violet-600 font-semibold">{{ b.graduatedCount }}</td>
                      <td class="py-2 px-2 text-right text-mute">{{ b.overriddenCount }}</td>
                      <td class="py-2 pr-5 text-mute text-xs">{{ b.appliedBy ?? '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </bbc-card>
      }
    </div>

    @if (confirming()) {
      <bbc-confirm
        [title]="fr() ? 'Appliquer le passage de classe ?' : 'Apply the promotion?'"
        [body]="confirmBody()"
        [confirmLabel]="fr() ? 'Appliquer' : 'Apply'"
        [cancelLabel]="i18n.t('cancel')"
        [danger]="false"
        (confirm)="doApply()" (cancel)="confirming.set(false)" />
    }
  `,
})
export class PromotionComponent {
  protected i18n = inject(I18nService);
  private api = inject(PromotionApi);
  private auth = inject(AuthService);

  protected readonly applicable = APPLICABLE;
  protected readonly META: Record<string, ResultMeta> = {
    promoted:        { fr: 'Admis',        en: 'Promoted',        badge: 'bg-emerald-100 text-emerald-700' },
    repeated:        { fr: 'Redouble',     en: 'Repeats',         badge: 'bg-amber-100 text-amber-700' },
    graduated:       { fr: 'Diplômé',      en: 'Graduated',       badge: 'bg-violet-100 text-violet-700' },
    transferred_out: { fr: 'Transféré',    en: 'Transferred out', badge: 'bg-slate-100 text-slate-600' },
    excluded:        { fr: 'Exclu',        en: 'Excluded',        badge: 'bg-rose-100 text-rose-700' },
    review:          { fr: 'À examiner',   en: 'To review',       badge: 'bg-sky-100 text-sky-700' },
    undecided:       { fr: 'Sans note',    en: 'No grades',       badge: 'bg-slate-100 text-slate-500' },
  };

  protected tab = signal<'run' | 'config' | 'closure' | 'history'>('config');
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected confirming = signal(false);

  protected progression = signal<ProgressionView[]>([]);
  protected rules = signal<RuleView[]>([]);
  protected batches = signal<BatchView[]>([]);
  protected preview = signal<PromotionPreview | null>(null);
  protected lastResult = signal<PromotionResult | null>(null);

  protected classId = signal('');
  protected year = signal('');
  protected nextYearInput = '';

  /** Décisions en cours d'arbitrage, par élève — objets simples pilotés par ngModel. */
  private draft: Record<string, { result: string; toClassId: string; reason: string }> = {};
  /** Progression en cours d'édition, par classe. */
  private progDraft: Record<string, { gradeOrder: number; nextClassId: string | null; terminal: boolean }> = {};

  protected ruleDraft: RuleUpsert = this.blankRule();

  protected closure = signal<ClosurePreview | null>(null);
  protected closures = signal<ClosureView[]>([]);
  protected closureResult = signal<ClosureResult | null>(null);
  protected closureDraft = { archiveGrades: true, resetFees: true, makeCurrent: true, ignorePending: false };
  protected closureConfirm = '';

  protected canRun = this.auth.can('promotion', 'write');
  protected canConfig = this.auth.can('settings', 'write');
  /** Clôturer archive et bascule toute l'école : réservé à qui cumule les deux droits. */
  protected canClose = this.auth.can('settings', 'write') && this.auth.can('promotion', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  constructor() {
    // Un compte en lecture seule n'a pas accès à la simulation (elle exige
    // l'écriture) : on ne lui présente pas un onglet qui répondrait « accès refusé ».
    if (this.canRun) this.tab.set('run');
    this.loadConfig();
  }

  protected tabs(): { id: string; label: string }[] {
    const tabs: { id: string; label: string }[] = [];
    if (this.canRun) tabs.push({ id: 'run', label: this.fr() ? 'Passage de classe' : 'Promotion' });
    tabs.push({ id: 'config', label: this.fr() ? 'Progression & règles' : 'Progression & rules' });
    if (this.canClose) tabs.push({ id: 'closure', label: this.fr() ? 'Clôture de l’année' : 'Year closure' });
    tabs.push({ id: 'history', label: this.fr() ? 'Historique' : 'History' });
    return tabs;
  }

  protected switchTab(id: string): void {
    this.tab.set(id as 'run' | 'config' | 'closure' | 'history');
    if (id === 'history') this.api.batches().subscribe({ next: (b) => this.batches.set(b), error: () => {} });
    if (id === 'closure') this.loadClosure();
  }

  // ---- Chargement ----------------------------------------------------------

  private loadConfig(): void {
    this.api.config().subscribe({
      next: (c: PromotionConfig) => {
        this.applyConfig(c);
        if (!this.year()) this.year.set(c.currentYear);
        if (!this.nextYearInput) this.nextYearInput = c.nextYear;
      },
      error: (e) => this.fail(e),
    });
  }

  private applyConfig(c: PromotionConfig): void {
    this.setProgression(c.progression);
    this.rules.set(c.rules);
  }

  private setProgression(rows: ProgressionView[]): void {
    this.progression.set(rows);
    this.progDraft = {};
    for (const p of rows) {
      this.progDraft[p.classId] = {
        gradeOrder: p.gradeOrder, nextClassId: p.nextClassId, terminal: p.terminal,
      };
    }
  }

  protected pickClass(id: string): void {
    this.classId.set(id);
    this.lastResult.set(null);
    if (!id) { this.preview.set(null); return; }
    this.reload();
  }

  protected changeYear(value: string): void {
    this.year.set(value);
    this.nextYearInput = this.deriveNextYear(value);
    if (this.classId()) this.reload();
  }

  protected reload(): void {
    const id = this.classId();
    if (!id) return;
    this.busy.set(true);
    this.api.preview(id, this.year() || undefined).subscribe({
      next: (p) => {
        this.preview.set(p);
        this.year.set(p.academicYear);
        if (!this.nextYearInput) this.nextYearInput = p.nextAcademicYear;
        this.resetToProposals();
        this.busy.set(false);
      },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  // ---- Arbitrage ligne à ligne ---------------------------------------------

  /** Reprend, pour chaque élève, la proposition automatique quand elle est actable. */
  protected resetToProposals(): void {
    const p = this.preview();
    this.draft = {};
    if (!p) return;
    for (const c of p.candidates) {
      const proposed = APPLICABLE.includes(c.proposedResult) ? c.proposedResult : '';
      this.draft[c.studentId] = {
        result: proposed,
        toClassId: c.proposedClassId ?? (proposed === 'promoted' ? (p.nextClassId ?? '') : ''),
        reason: '',
      };
    }
  }

  protected row(c: CandidateView): { result: string; toClassId: string; reason: string } {
    return this.draft[c.studentId] ?? (this.draft[c.studentId] = { result: '', toClassId: '', reason: '' });
  }

  protected setResult(c: CandidateView, value: string): void {
    const row = this.row(c);
    row.result = value;
    if (value === 'promoted' && !row.toClassId) {
      row.toClassId = c.proposedClassId ?? this.preview()?.nextClassId ?? '';
    }
  }

  protected setTarget(c: CandidateView, value: string): void { this.row(c).toClassId = value; }
  protected setReason(c: CandidateView, value: string): void { this.row(c).reason = value; }

  /** Une décision s'écarte de la proposition — c'est elle qui exige un motif. */
  protected isOverridden(c: CandidateView): boolean {
    const result = this.row(c).result;
    return !!result && result !== c.proposedResult;
  }

  /** Lignes réellement soumises : décidées, et motivées quand elles arbitrent. */
  private ready(): CandidateView[] {
    const p = this.preview();
    if (!p) return [];
    return p.candidates.filter((c) => {
      const row = this.row(c);
      if (!row.result) return false;
      if (row.result === 'promoted' && !row.toClassId) return false;
      return !(this.isOverridden(c) && !row.reason.trim());
    });
  }

  protected readyCount(): number { return this.ready().length; }

  protected overriddenCount(): number {
    return (this.preview()?.candidates ?? []).filter((c) => this.isOverridden(c)).length;
  }

  // ---- Application ---------------------------------------------------------

  protected confirmBody(): string {
    const p = this.preview();
    if (!p) return '';
    const ready = this.ready();
    const promoted = ready.filter((c) => this.row(c).result === 'promoted').length;
    const repeated = ready.filter((c) => this.row(c).result === 'repeated').length;
    return this.fr()
      ? `${ready.length} élève(s) de ${p.className} : ${promoted} admis (transférés en ${this.nextYearInput}), `
        + `${repeated} redoublant(s). Les élèves changent réellement de classe et leur parcours est écrit.`
      : `${ready.length} student(s) from ${p.className}: ${promoted} promoted (moved for ${this.nextYearInput}), `
        + `${repeated} repeating. Students actually change class and their journey is written.`;
  }

  protected askApply(): void {
    if (this.readyCount() === 0) return;
    this.confirming.set(true);
  }

  protected doApply(): void {
    const p = this.preview();
    if (!p) return;
    this.confirming.set(false);
    this.busy.set(true);
    this.api.apply({
      classId: p.classId,
      academicYear: this.year() || p.academicYear,
      nextAcademicYear: this.nextYearInput || p.nextAcademicYear,
      lines: this.ready().map((c) => {
        const row = this.row(c);
        return {
          studentId: c.studentId,
          result: row.result,
          toClassId: row.result === 'promoted' ? (row.toClassId || null) : null,
          reason: row.reason.trim() || null,
        };
      }),
    }).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.lastResult.set(r);
        if (r.warnings.length) this.error.set(r.warnings.join(' '));
        this.reload();
      },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  // ---- Progression ---------------------------------------------------------

  protected prog(p: ProgressionView): { gradeOrder: number; nextClassId: string | null; terminal: boolean } {
    return this.progDraft[p.classId]
      ?? (this.progDraft[p.classId] = { gradeOrder: p.gradeOrder, nextClassId: p.nextClassId, terminal: p.terminal });
  }

  protected setOrder(p: ProgressionView, value: number): void { this.prog(p).gradeOrder = Number(value) || 0; }
  protected setNext(p: ProgressionView, value: string): void { this.prog(p).nextClassId = value || null; }

  protected setTerminal(p: ProgressionView, value: boolean): void {
    const row = this.prog(p);
    row.terminal = value;
    if (value) row.nextClassId = null;     // une classe de sortie ne mène nulle part
  }

  protected saveProgression(): void {
    this.busy.set(true);
    this.api.saveProgression(this.progression().map((p) => ({
      classId: p.classId,
      gradeOrder: this.prog(p).gradeOrder,
      nextClassId: this.prog(p).nextClassId,
      terminal: this.prog(p).terminal,
    }))).subscribe({
      next: (rows) => { this.setProgression(rows); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  protected autoFill(): void {
    this.busy.set(true);
    this.api.autoProgression().subscribe({
      next: (rows) => { this.setProgression(rows); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  // ---- Règles --------------------------------------------------------------

  protected editRule(r: RuleView): void {
    this.ruleDraft = {
      id: r.id, level: r.level, subsystem: r.subsystem, classId: r.classId,
      passMark: r.passMark, councilMargin: r.councilMargin, maxRepeats: r.maxRepeats,
    };
  }

  protected resetRuleDraft(): void { this.ruleDraft = this.blankRule(); }

  protected saveRule(): void {
    const d = this.ruleDraft;
    this.busy.set(true);
    this.api.saveRule({
      id: d.id ?? null,
      classId: d.classId ?? null,
      level: d.classId ? null : (d.level ?? null),
      subsystem: d.classId ? null : (d.subsystem ?? null),
      passMark: Number(d.passMark),
      councilMargin: Number(d.councilMargin ?? 0),
      maxRepeats: d.maxRepeats === null || (d.maxRepeats as unknown as string) === '' ? null : Number(d.maxRepeats),
    }).subscribe({
      next: (rows) => { this.rules.set(rows); this.resetRuleDraft(); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  protected removeRule(r: RuleView): void {
    this.busy.set(true);
    this.api.deleteRule(r.id).subscribe({
      next: (rows) => { this.rules.set(rows); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  // ---- Clôture de l'année ---------------------------------------------------

  private loadClosure(): void {
    this.api.closurePreview(this.year() || undefined).subscribe({
      next: (k) => {
        this.closure.set(k);
        this.closureDraft.ignorePending = false;
        this.closureConfirm = '';
      },
      error: (e) => this.fail(e),
    });
    this.api.closureHistory().subscribe({ next: (h) => this.closures.set(h), error: () => {} });
  }

  /** Le bouton ne s'arme que lorsque l'année a été retapée à l'identique. */
  protected closureArmed(): boolean {
    const k = this.closure();
    if (!k || k.closedAt) return false;
    if (k.studentsPending > 0 && !this.closureDraft.ignorePending) return false;
    return this.closureConfirm.trim() === k.academicYear;
  }

  protected doClose(): void {
    const k = this.closure();
    if (!k || !this.closureArmed()) return;
    this.busy.set(true);
    this.api.close({
      academicYear: k.academicYear,
      nextAcademicYear: k.nextAcademicYear,
      archiveGrades: this.closureDraft.archiveGrades,
      resetFees: this.closureDraft.resetFees,
      makeCurrent: this.closureDraft.makeCurrent,
      ignorePending: this.closureDraft.ignorePending,
    }).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.closureResult.set(r);
        if (r.warnings.length) this.error.set(r.warnings.join(' '));
        // L'année courante a changé : la configuration et la simulation la suivent.
        this.year.set(r.nextAcademicYear);
        this.nextYearInput = this.deriveNextYear(r.nextAcademicYear);
        this.preview.set(null);
        this.classId.set('');
        this.loadClosure();
      },
      error: (e) => { this.busy.set(false); this.fail(e); },
    });
  }

  // ---- Divers --------------------------------------------------------------

  protected meta(result: string): ResultMeta {
    return this.META[result] ?? { fr: result, en: result, badge: 'bg-slate-100 text-slate-600' };
  }

  protected label(result: string): string {
    const m = this.meta(result);
    return this.fr() ? m.fr : m.en;
  }

  private deriveNextYear(label: string): string {
    const m = /^(\d{4})\s*-\s*(\d{4})$/.exec((label ?? '').trim());
    if (!m) return this.nextYearInput;
    const start = Number(m[1]) + 1;
    return `${start}-${start + 1}`;
  }

  private blankRule(): RuleUpsert {
    return { id: null, level: null, subsystem: null, classId: null, passMark: 10, councilMargin: 1, maxRepeats: 2 };
  }

  private fail(e: { error?: { message?: unknown } }): void {
    const msg = e.error?.message;
    this.error.set(typeof msg === 'string' ? msg : (this.fr() ? 'Opération refusée.' : 'Operation refused.'));
  }
}
