import { Component, ChangeDetectionStrategy, effect, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterLink } from '@angular/router';
import {
  SettingsApi, PermissionMatrix, RoleView, RoleUpsert, MailConfigUpdate,
  SchoolProfileView, SchoolProfileUpdate, HolidayView, CatalogItemView, CatalogItemUpsert,
  AdminView, AdminCreate, Section,
} from './settings.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, TabsComponent,
} from '../../core/ui';
import { AcademicSetupComponent } from '../setup/academic-setup';
import { FoundationSettingsComponent } from './foundation-settings';

type Level = 'none' | 'read' | 'write';
type SettingsTab =
  | 'academic' | 'sessions' | 'general' | 'perms' | 'roles' | 'mail' | 'calendar' | 'discipline' | 'admins';

@Component({
  selector: 'bbc-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, TabsComponent, AcademicSetupComponent, FoundationSettingsComponent],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('settings')"
        [subtitle]="fr() ? 'Configuration générale de l’établissement' : 'General school configuration'">
        <div right class="flex items-center gap-2">
          @if (auth.canAction('PERMISSION_VIEW')) {
            <a routerLink="/access-control" class="inline-flex items-center gap-1.5 h-9 px-3 text-xs font-semibold rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-700 hover:bg-indigo-100">
              <bbc-icon name="shield" [s]="14" /> {{ fr() ? 'Accès & responsabilités' : 'Access & responsibilities' }}
            </a>
          }
          @if (currentUser(); as u) {
            <span class="inline-flex items-center gap-1.5 h-9 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-mute">
              <bbc-icon name="shield" [s]="14" /> {{ u.role }}
            </span>
          }
        </div>
      </bbc-page-header>

      <bbc-tabs [tabs]="tabs()" [value]="activeTab()" (change)="onTab($any($event))" />

      @switch (activeTab()) {
        <!-- ===================== ACADEMIC SETUP ===================== -->
        @case ('academic') {
          @if (canViewAcademicSetup()) {
            <bbc-academic-setup />
          } @else {
            <bbc-card data-testid="academic-setup-guarded">
              <bbc-empty icon="shield" [label]="fr() ? 'La configuration scolaire est réservée aux profils autorisés.' : 'Academic setup is available only to authorized profiles.'" />
            </bbc-card>
          }
        }

        @case ('sessions') {
          <bbc-foundation-settings />
        }

        <!-- ===================== GENERAL ===================== -->
        @case ('general') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-6" [title]="fr() ? 'Établissement' : 'School'">
              <div action class="w-8 h-8 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center">
                <bbc-icon name="building" [s]="18" />
              </div>
              @if (school(); as s) {
                <div class="space-y-3">
                  <div class="grid grid-cols-2 gap-3">
                    <label class="block col-span-2">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Nom' : 'Name' }}</span>
                      <input [(ngModel)]="schoolDraft.name" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block col-span-2">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Devise / slogan' : 'Motto' }}</span>
                      <input [(ngModel)]="schoolDraft.motto" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Ville' : 'City' }}</span>
                      <input [(ngModel)]="schoolDraft.city" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Pays' : 'Country' }}</span>
                      <input [(ngModel)]="schoolDraft.country" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block col-span-2">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Adresse' : 'Street address' }}</span>
                      <input [(ngModel)]="schoolDraft.address" [disabled]="!canWrite"
                        autocomplete="street-address"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                      <input [(ngModel)]="schoolDraft.phone" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">E-mail</span>
                      <input [(ngModel)]="schoolDraft.email" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Devise monétaire' : 'Currency' }}</span>
                      <input [(ngModel)]="schoolDraft.currency" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Autorité de tutelle' : 'Authority' }}</span>
                      <input [(ngModel)]="schoolDraft.authority" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Début des cours' : 'School start' }}</span>
                      <input type="time" [(ngModel)]="schoolDraft.schoolStartTime" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Fin des cours' : 'School end' }}</span>
                      <input type="time" [(ngModel)]="schoolDraft.schoolEndTime" [disabled]="!canWrite"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    </label>
                  </div>
                  @if (s.academicYear) {
                    <div class="text-xs text-mute">{{ fr() ? 'Année scolaire' : 'Academic year' }}: <span class="font-semibold text-ink">{{ s.academicYear }}</span></div>
                  }
                  @if (canWrite) {
                    <button (click)="saveSchool()" [disabled]="savingSchool()"
                      class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-60">
                      <bbc-icon name="check" [s]="16" /> {{ savingSchool() ? '…' : (fr() ? 'Enregistrer' : 'Save') }}
                    </button>
                  }
                  @if (schoolMsg(); as m) {
                    <div class="text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
                  }
                </div>
              } @else {
                <bbc-empty icon="building" [label]="fr() ? 'Chargement…' : 'Loading…'" />
              }
            </bbc-card>

            <bbc-card className="col-span-12 lg:col-span-6" [title]="fr() ? 'Lecteur d’empreintes' : 'Fingerprint reader'">
              <div action class="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <bbc-icon name="fingerprint" [s]="18" />
              </div>
              <div class="space-y-0.5">
                @for (r of readerRows(); track r.label) {
                  <div class="flex items-center justify-between py-2 border-b border-slate-50 last:border-0">
                    <div class="text-sm text-mute">{{ r.label }}</div>
                    @if (r.online) {
                      <div class="text-sm font-semibold text-emerald-700 flex items-center gap-1.5">
                        <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>{{ r.value }}
                      </div>
                    } @else {
                      <div class="text-sm font-semibold text-ink">{{ r.value }}</div>
                    }
                  </div>
                }
              </div>
            </bbc-card>
          </div>
        }

        <!-- ===================== PERMISSIONS MATRIX ===================== -->
        @case ('perms') {
          @if (matrix(); as m) {
            <bbc-card [title]="fr() ? 'Matrice des permissions' : 'Permission matrix'"
              [subtitle]="fr() ? 'Accès granulaire par module pour chaque rôle' : 'Granular per-module access for each role'">
              <div action class="flex items-center gap-3 text-xs">
                @for (l of legend(); track l.level) {
                  <span class="flex items-center gap-1.5">
                    <span class="w-2.5 h-2.5 rounded-full" [class]="l.dot"></span>
                    <span class="text-mute font-semibold">{{ l.label }}</span>
                  </span>
                }
              </div>

              @if (canWrite) {
                <div class="text-xs text-mute mb-3">
                  {{ fr() ? 'Cliquez sur une cellule pour faire défiler — appliqué immédiatement.' : 'Click a cell to cycle — applied immediately.' }}
                </div>
              }

              <div class="overflow-x-auto -mx-5">
                <table class="min-w-full text-sm border-collapse">
                  <thead>
                    <tr class="border-y border-slate-100 bg-slate-50/50">
                      <th class="text-left font-semibold text-[11px] uppercase text-mute py-3 pl-5 sticky left-0 bg-slate-50 min-w-[160px]">
                        {{ fr() ? 'Module' : 'Module' }}
                      </th>
                      @for (role of m.roles; track role.code) {
                        <th class="py-3 px-3 min-w-[120px]">
                          <div class="flex flex-col items-center gap-1">
                            <span class="text-[10px] font-bold uppercase tracking-wide px-2 py-1 rounded" [class]="roleColor(role)">
                              {{ fr() ? role.labelFr : role.labelEn }}
                            </span>
                            @if (role.builtin) {
                              <span class="text-[10px] text-mute font-normal normal-case">
                                {{ fr() ? 'intégré' : 'built-in' }}
                              </span>
                            }
                          </div>
                        </th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (mod of m.modules; track mod) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/30">
                        <td class="py-2 pl-5 sticky left-0 bg-white font-semibold text-ink whitespace-nowrap">
                          {{ i18n.moduleLabel(mod) }}
                        </td>
                        @for (role of m.roles; track role.code) {
                          <td class="py-2 px-3 text-center">
                            @if (parentBlocked(role.code, mod)) {
                              <span class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold opacity-40 cursor-not-allowed"
                                [class]="cellClass(levelOf(role.code, mod))"
                                [title]="fr() ? 'Le rôle parent n’a accès qu’au module parent' : 'Parent role can only access the parent module'">
                                <span class="w-2 h-2 rounded-full" [class]="dotClass(levelOf(role.code, mod))"></span>
                                {{ cellLabel(levelOf(role.code, mod)) }}
                              </span>
                            } @else if (canWrite) {
                              <button (click)="cycle(role.code, mod)"
                                class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold transition hover:ring-2 hover:ring-brand-300"
                                [class]="cellClass(levelOf(role.code, mod))">
                                <span class="w-2 h-2 rounded-full" [class]="dotClass(levelOf(role.code, mod))"></span>
                                {{ cellLabel(levelOf(role.code, mod)) }}
                              </button>
                            } @else {
                              <span class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold"
                                [class]="cellClass(levelOf(role.code, mod))">
                                <span class="w-2 h-2 rounded-full" [class]="dotClass(levelOf(role.code, mod))"></span>
                                {{ cellLabel(levelOf(role.code, mod)) }}
                              </span>
                            }
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </bbc-card>
          } @else {
            <bbc-card>
              <bbc-empty icon="shield" [label]="fr() ? 'Chargement de la matrice…' : 'Loading matrix…'" />
            </bbc-card>
          }
        }

        <!-- ===================== ROLES ===================== -->
        @case ('roles') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-7" [title]="fr() ? 'Rôles utilisateurs' : 'User roles'"
              [subtitle]="fr() ? 'Libellés modifiables ; seuls les rôles personnalisés peuvent être supprimés' : 'Labels are editable; only custom roles can be deleted'">
              <div action class="w-8 h-8 rounded-lg bg-violet-50 text-violet-600 flex items-center justify-center">
                <bbc-icon name="users" [s]="18" />
              </div>
              @if (matrix(); as m) {
                <div class="space-y-2">
                  @for (role of m.roles; track role.code) {
                    <div class="p-3 rounded-lg border border-slate-100" [class]="role.builtin ? 'bg-brand-50/40' : 'bg-gold-50/40'">
                      @if (editingRole()?.code === role.code) {
                        <div class="grid grid-cols-2 gap-2">
                          <input [(ngModel)]="editRoleDraft.labelFr" [placeholder]="fr() ? 'Libellé FR' : 'FR label'"
                            class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                          <input [(ngModel)]="editRoleDraft.labelEn" [placeholder]="fr() ? 'Libellé EN' : 'EN label'"
                            class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                          <div class="col-span-2 flex items-center gap-2">
                            <button (click)="saveRoleEdit()" class="h-8 px-3 text-xs font-semibold rounded-lg bg-brand-600 text-white">{{ fr() ? 'OK' : 'Save' }}</button>
                            <button (click)="cancelRoleEdit()" class="h-8 px-3 text-xs font-semibold rounded-lg bg-slate-100 text-mute">{{ i18n.t('cancel') }}</button>
                          </div>
                        </div>
                      } @else {
                        <div class="flex items-center justify-between gap-2">
                          <div>
                            <div class="text-sm font-bold text-ink">{{ fr() ? role.labelFr : role.labelEn }}</div>
                            <div class="text-[11px] text-mute font-mono">{{ role.code }} · {{ role.builtin ? (fr() ? 'Intégré' : 'Built-in') : (fr() ? 'Personnalisé' : 'Custom') }}</div>
                          </div>
                          @if (canWrite) {
                            <div class="flex items-center gap-1">
                              <button (click)="startRoleEdit(role)"
                                class="h-8 px-2.5 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                                {{ fr() ? 'Libellé' : 'Label' }}
                              </button>
                              @if (!role.builtin) {
                                <button (click)="deleteRole(role)"
                                  class="h-8 px-2.5 text-xs font-semibold rounded-lg bg-white border border-rose-200 text-rose-600 hover:bg-rose-50">
                                  {{ fr() ? 'Supprimer' : 'Delete' }}
                                </button>
                              }
                            </div>
                          }
                        </div>
                      }
                    </div>
                  }
                </div>
                @if (roleMsg(); as m) {
                  <div class="mt-3 text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
                }
              } @else {
                <bbc-empty icon="users" [label]="fr() ? 'Aucun rôle' : 'No roles'" />
              }
            </bbc-card>

            @if (canWrite) {
              <bbc-card className="col-span-12 lg:col-span-5" [title]="fr() ? 'Nouveau rôle' : 'New role'"
                [subtitle]="fr() ? 'Créer un rôle personnalisé' : 'Create a custom role'">
                <div class="space-y-3">
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Libellé (FR)' : 'Label (FR)' }}</span>
                    <input [(ngModel)]="newRole.labelFr"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Libellé (EN, optionnel)' : 'Label (EN, optional)' }}</span>
                    <input [(ngModel)]="newRole.labelEn"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <button (click)="createRole()" [disabled]="!newRole.labelFr.trim() || creatingRole()"
                    class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-60">
                    <bbc-icon name="plus" [s]="16" /> {{ creatingRole() ? '…' : (fr() ? 'Créer' : 'Create') }}
                  </button>
                  @if (roleMsg(); as m) {
                    <div class="text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
                  }
                </div>
              </bbc-card>
            }
          </div>
        }

        <!-- ===================== CALENDAR ===================== -->
        @case ('calendar') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-7"
              [title]="fr() ? 'Jours fériés' : 'Holidays'"
              [subtitle]="fr() ? 'Jours non ouvrés de l’établissement' : 'Non-working school days'">
              <div action class="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                <bbc-icon name="calendar" [s]="18" />
              </div>
              @if (holidays().length === 0) {
                <bbc-empty icon="calendar" [label]="fr() ? 'Aucun jour férié' : 'No holidays'" />
              } @else {
                <div class="space-y-1">
                  @for (h of holidays(); track h.id) {
                    <div class="flex items-center justify-between py-2 border-b border-slate-50 last:border-0">
                      <div>
                        <div class="text-sm font-semibold text-ink">{{ h.label }}</div>
                        <div class="text-xs text-mute font-mono">{{ h.date }}</div>
                      </div>
                      @if (canWrite) {
                        <button (click)="removeHoliday(h)"
                          class="w-8 h-8 rounded-lg text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center">
                          <bbc-icon name="x" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </bbc-card>

            <div class="col-span-12 lg:col-span-5 space-y-4">
              <bbc-card [title]="fr() ? 'Horaires scolaires' : 'School hours'">
                <p class="text-sm text-mute">
                  {{ fr()
                    ? 'Les heures de début et de fin des cours se règlent dans l’onglet '
                    : 'School start and end times are set in the ' }}
                  <button type="button" (click)="tab.set('general')" class="font-semibold text-brand-600 hover:underline">
                    {{ fr() ? 'Général' : 'General' }}
                  </button>
                  {{ fr() ? '.' : ' tab.' }}
                </p>
                @if (school(); as s) {
                  <div class="mt-3 flex items-center gap-4 text-sm">
                    <div><span class="text-mute">{{ fr() ? 'Début' : 'Start' }}:</span> <span class="font-semibold text-ink">{{ s.schoolStartTime }}</span></div>
                    <div><span class="text-mute">{{ fr() ? 'Fin' : 'End' }}:</span> <span class="font-semibold text-ink">{{ s.schoolEndTime }}</span></div>
                  </div>
                }
              </bbc-card>

              @if (canWrite) {
                <bbc-card [title]="fr() ? 'Ajouter un jour férié' : 'Add holiday'">
                  <div class="space-y-3">
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Date' : 'Date' }}</span>
                      <input type="date" [(ngModel)]="holidayDraft.date"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Libellé' : 'Label' }}</span>
                      <input [(ngModel)]="holidayDraft.label"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <button (click)="addHoliday()" [disabled]="!holidayDraft.date || !holidayDraft.label.trim() || savingHoliday()"
                      class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-60">
                      <bbc-icon name="plus" [s]="16" /> {{ savingHoliday() ? '…' : (fr() ? 'Ajouter' : 'Add') }}
                    </button>
                  </div>
                </bbc-card>
              }
            </div>
          </div>
        }

        <!-- ===================== DISCIPLINE CATALOG ===================== -->
        @case ('discipline') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-6"
              [title]="fr() ? 'Types d’incident' : 'Incident types'"
              [subtitle]="fr() ? 'Catalogue des types actifs' : 'Active type catalog'">
              <div action class="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                <bbc-icon name="shield" [s]="18" />
              </div>
              @if (catalogTypes().length === 0) {
                <bbc-empty icon="shield" [label]="fr() ? 'Aucun type' : 'No types'" />
              } @else {
                <div class="space-y-1 mb-4">
                  @for (item of catalogTypes(); track item.id) {
                    <div class="flex items-center justify-between py-2 border-b border-slate-50 last:border-0">
                      <div>
                        <div class="text-sm font-semibold text-ink">{{ fr() ? item.labelFr : item.labelEn }}</div>
                        <div class="text-[11px] text-mute font-mono">{{ item.code }}</div>
                      </div>
                      @if (canWrite) {
                        <button (click)="removeCatalog(item)"
                          class="w-8 h-8 rounded-lg text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center">
                          <bbc-icon name="x" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
              @if (canWrite) {
                <div class="pt-3 border-t border-slate-100 space-y-2">
                  <div class="text-xs font-semibold text-mute uppercase tracking-wide">{{ fr() ? 'Ajouter un type' : 'Add type' }}</div>
                  <div class="grid grid-cols-2 gap-2">
                    <input [(ngModel)]="typeDraft.labelFr" [placeholder]="fr() ? 'Libellé FR' : 'FR label'"
                      class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <input [(ngModel)]="typeDraft.labelEn" [placeholder]="fr() ? 'Libellé EN' : 'EN label'"
                      class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </div>
                  <button (click)="addCatalog('type')" [disabled]="!typeDraft.labelFr.trim()"
                    class="inline-flex items-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60">
                    <bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Ajouter' : 'Add' }}
                  </button>
                </div>
              }
            </bbc-card>

            <bbc-card className="col-span-12 lg:col-span-6"
              [title]="fr() ? 'Sanctions' : 'Sanctions'"
              [subtitle]="fr() ? 'Catalogue des sanctions actives' : 'Active sanction catalog'">
              <div action class="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <bbc-icon name="bell" [s]="18" />
              </div>
              @if (catalogSanctions().length === 0) {
                <bbc-empty icon="bell" [label]="fr() ? 'Aucune sanction' : 'No sanctions'" />
              } @else {
                <div class="space-y-1 mb-4">
                  @for (item of catalogSanctions(); track item.id) {
                    <div class="flex items-center justify-between py-2 border-b border-slate-50 last:border-0">
                      <div>
                        <div class="text-sm font-semibold text-ink">{{ fr() ? item.labelFr : item.labelEn }}</div>
                        <div class="text-[11px] text-mute font-mono">{{ item.code }}</div>
                      </div>
                      @if (canWrite) {
                        <button (click)="removeCatalog(item)"
                          class="w-8 h-8 rounded-lg text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center">
                          <bbc-icon name="x" [s]="14" />
                        </button>
                      }
                    </div>
                  }
                </div>
              }
              @if (canWrite) {
                <div class="pt-3 border-t border-slate-100 space-y-2">
                  <div class="text-xs font-semibold text-mute uppercase tracking-wide">{{ fr() ? 'Ajouter une sanction' : 'Add sanction' }}</div>
                  <div class="grid grid-cols-2 gap-2">
                    <input [(ngModel)]="sanctionDraft.labelFr" [placeholder]="fr() ? 'Libellé FR' : 'FR label'"
                      class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <input [(ngModel)]="sanctionDraft.labelEn" [placeholder]="fr() ? 'Libellé EN' : 'EN label'"
                      class="h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </div>
                  <button (click)="addCatalog('sanction')" [disabled]="!sanctionDraft.labelFr.trim()"
                    class="inline-flex items-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60">
                    <bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Ajouter' : 'Add' }}
                  </button>
                </div>
              }
            </bbc-card>
          </div>
        }

        <!-- ===================== ADMINISTRATEURS ===================== -->
        @case ('admins') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-7"
              [title]="fr() ? 'Administrateurs' : 'Administrators'"
              [subtitle]="fr() ? 'L’administrateur principal et ses relais de section' : 'The main administrator and their section deputies'">
              <div action class="w-8 h-8 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center">
                <bbc-icon name="shield" [s]="18" />
              </div>

              @if (admins().length === 0) {
                <bbc-empty [label]="fr() ? 'Aucun administrateur' : 'No administrator'" />
              } @else {
                <div class="space-y-2">
                  @for (a of admins(); track a.userId) {
                    <div class="p-3 rounded-lg border border-slate-100"
                      [class]="a.section ? 'bg-white' : 'bg-brand-50/40'">
                      <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0">
                          <div class="flex items-center gap-2">
                            <span class="font-semibold text-ink truncate">{{ a.displayName }}</span>
                            @if (!a.active) {
                              <span class="px-1.5 py-0.5 text-[10px] font-bold rounded bg-slate-100 text-mute uppercase">
                                {{ fr() ? 'Suspendu' : 'Suspended' }}
                              </span>
                            }
                          </div>
                          <div class="text-xs text-mute mt-0.5 truncate">
                            {{ a.username }}@if (a.email) { · {{ a.email }} }
                          </div>
                          <div class="text-xs font-semibold mt-1"
                            [class]="a.section ? 'text-brand-700' : 'text-gold-600'">
                            {{ a.section ? sectionLabel(a.section) : (fr() ? 'Établissement entier' : 'Whole school') }}
                          </div>
                        </div>

                        @if (a.section) {
                          <div class="flex items-center gap-1.5 shrink-0">
                            <select [ngModel]="a.section" (ngModelChange)="moveAdmin(a, $any($event))"
                              class="h-9 px-2 text-xs rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400">
                              @for (sec of SECTIONS; track sec) {
                                <option [value]="sec">{{ sectionLabel(sec) }}</option>
                              }
                            </select>
                            <button type="button" (click)="resetAdmin(a)" [disabled]="busyAdmin() === a.userId"
                              [title]="fr() ? 'Renvoyer les identifiants' : 'Resend credentials'"
                              class="h-9 px-3 text-xs font-semibold rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-60">
                              {{ fr() ? 'Identifiants' : 'Credentials' }}
                            </button>
                            <button type="button" (click)="toggleAdmin(a)" [disabled]="busyAdmin() === a.userId"
                              class="h-9 px-3 text-xs font-semibold rounded-lg border disabled:opacity-60"
                              [class]="a.active ? 'border-rose-200 text-rose-600 hover:bg-rose-50' : 'border-emerald-200 text-emerald-600 hover:bg-emerald-50'">
                              {{ a.active ? (fr() ? 'Suspendre' : 'Suspend') : (fr() ? 'Rétablir' : 'Restore') }}
                            </button>
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            </bbc-card>

            @if (canWrite) {
              <bbc-card className="col-span-12 lg:col-span-5"
                [title]="fr() ? 'Nommer un administrateur de section' : 'Appoint a section administrator'"
                [subtitle]="fr() ? 'Il administrera son cycle, et lui seul' : 'They will administer their own cycle, and nothing else'">
                <div class="space-y-3">
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Nom complet' : 'Full name' }}</span>
                    <input [(ngModel)]="adminDraft.name"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Section' : 'Section' }}</span>
                    <select [(ngModel)]="adminDraft.section"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400">
                      @for (sec of SECTIONS; track sec) {
                        <option [value]="sec">{{ sectionLabel(sec) }}</option>
                      }
                    </select>
                  </label>
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'E-mail' : 'E-mail' }}</span>
                    <input type="email" [(ngModel)]="adminDraft.email"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <span class="block text-xs text-mute mt-1">
                      {{ fr() ? 'Le mot de passe temporaire n’est transmis que par e-mail.' : 'The temporary password is only ever sent by e-mail.' }}
                    </span>
                  </label>
                  <label class="block">
                    <span class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                    <input [(ngModel)]="adminDraft.phone"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>

                  <button (click)="createAdmin()"
                    [disabled]="!adminDraft.name.trim() || creatingAdmin()"
                    class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-60">
                    <bbc-icon name="plus" [s]="15" />
                    {{ creatingAdmin() ? (fr() ? 'Création…' : 'Creating…') : (fr() ? 'Nommer' : 'Appoint') }}
                  </button>

                  @if (adminMsg(); as m) {
                    <p class="text-sm rounded-lg px-3 py-2"
                      [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ m.text }}</p>
                  }

                  <p class="text-xs text-mute leading-relaxed border-t border-slate-100 pt-3">
                    {{ fr()
                      ? 'Un administrateur de section voit et configure les élèves, classes, notes, personnel et recettes de son seul cycle. Les réglages de l’établissement — rôles, permissions, profil, messagerie, calendrier, clôture d’année — restent les vôtres.'
                      : 'A section administrator sees and configures the students, classes, grades, staff and revenue of their own cycle only. School-wide settings — roles, permissions, profile, e-mail, calendar, year closure — remain yours.' }}
                  </p>
                </div>
              </bbc-card>
            }
          </div>
        }

        <!-- ===================== MESSAGERIE (SMTP) ===================== -->
        @case ('mail') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-7"
              [title]="fr() ? 'Serveur SMTP' : 'SMTP server'"
              [subtitle]="fr() ? 'Envoi des e-mails (notifications, etc.)' : 'Outgoing e-mail (notifications, etc.)'">
              <div action class="w-8 h-8 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center">
                <bbc-icon name="mail" [s]="18" />
              </div>

              <div class="space-y-3">
                <label class="flex items-center gap-2.5 cursor-pointer">
                  <input type="checkbox" [(ngModel)]="mailDraft.enabled" [disabled]="!canWrite"
                    class="w-4 h-4 rounded accent-brand-600" />
                  <span class="text-sm font-semibold text-ink">{{ fr() ? 'Activer l’envoi d’e-mails' : 'Enable e-mail sending' }}</span>
                </label>

                <div class="grid grid-cols-3 gap-3">
                  <div class="col-span-2">
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Hôte' : 'Host' }}</label>
                    <input [(ngModel)]="mailDraft.host" [disabled]="!canWrite" placeholder="smtp.exemple.com"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </div>
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Port' : 'Port' }}</label>
                    <input type="number" [(ngModel)]="mailDraft.port" [disabled]="!canWrite"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Utilisateur' : 'Username' }}</label>
                    <input [(ngModel)]="mailDraft.username" [disabled]="!canWrite" autocomplete="off"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </div>
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Mot de passe' : 'Password' }}</label>
                    <input type="password" [(ngModel)]="mailDraft.password" [disabled]="!canWrite" autocomplete="new-password"
                      [placeholder]="passwordSet() ? '••••••••' : ''"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                    @if (passwordSet()) {
                      <div class="text-[11px] text-mute mt-1">{{ fr() ? 'Laisser vide pour conserver le mot de passe actuel.' : 'Leave empty to keep the current password.' }}</div>
                    }
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Adresse expéditeur' : 'From address' }}</label>
                    <input [(ngModel)]="mailDraft.fromAddress" [disabled]="!canWrite" placeholder="no-reply@exemple.com"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </div>
                  <div>
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Nom expéditeur' : 'From name' }}</label>
                    <input [(ngModel)]="mailDraft.fromName" [disabled]="!canWrite" placeholder="BBC SMS"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-50" />
                  </div>
                </div>

                <label class="flex items-center gap-2.5 cursor-pointer">
                  <input type="checkbox" [(ngModel)]="mailDraft.useTls" [disabled]="!canWrite" class="w-4 h-4 rounded accent-brand-600" />
                  <span class="text-sm text-ink">{{ fr() ? 'TLS (SSL implicite sur 465, STARTTLS sur 587)' : 'TLS (implicit SSL on 465, STARTTLS on 587)' }}</span>
                </label>
                <p class="text-xs text-mute -mt-1">
                  {{ fr() ? 'Le port 465 est utilisé avec SSL implicite. Le port 587 utilise STARTTLS.' : 'Port 465 uses implicit SSL. Port 587 uses STARTTLS.' }}
                </p>

                @if (canWrite) {
                  <div class="flex items-center gap-2 pt-1">
                    <button (click)="saveMail()" [disabled]="savingMail()"
                      class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-60">
                      <bbc-icon name="check" [s]="16" /> {{ savingMail() ? '…' : (fr() ? 'Enregistrer' : 'Save') }}
                    </button>
                  </div>
                }

                @if (mailMsg(); as m) {
                  <div class="text-xs rounded-lg px-3 py-2" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
                }
              </div>
            </bbc-card>

            <bbc-card className="col-span-12 lg:col-span-5"
              [title]="fr() ? 'Notifications' : 'Notifications'"
              [subtitle]="fr() ? 'Événements déclenchant un e-mail' : 'Events that trigger an e-mail'">
              <div action class="w-8 h-8 rounded-lg bg-gold-50 text-gold-500 flex items-center justify-center">
                <bbc-icon name="bell" [s]="18" />
              </div>
              <div class="space-y-4">
                <label class="flex items-start gap-2.5 cursor-pointer">
                  <input type="checkbox" [(ngModel)]="mailDraft.notifyOnUserCreate" [disabled]="!canWrite" class="w-4 h-4 mt-0.5 rounded accent-brand-600" />
                  <span>
                    <span class="text-sm font-semibold text-ink block">{{ fr() ? 'Création d’un utilisateur' : 'User created' }}</span>
                    <span class="text-xs text-mute">{{ fr() ? 'Envoyer un e-mail au nouvel employé (si une adresse est renseignée).' : 'E-mail the new employee (when an address is set).' }}</span>
                  </span>
                </label>

                @if (canWrite) {
                  <div class="border-t border-slate-100 pt-4">
                    <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Tester l’envoi' : 'Test sending' }}</label>
                    <div class="flex items-center gap-2">
                      <input [(ngModel)]="testTo" type="email" placeholder="vous@exemple.com"
                        class="flex-1 h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                      <button (click)="testMail()" [disabled]="testingMail() || !testTo"
                        class="inline-flex items-center gap-2 h-10 px-3 text-sm font-semibold rounded-lg bg-white border border-slate-200 hover:bg-slate-50 disabled:opacity-60">
                        <bbc-icon name="send" [s]="16" /> {{ testingMail() ? '…' : (fr() ? 'Envoyer' : 'Send') }}
                      </button>
                    </div>
                    <div class="text-[11px] text-mute mt-1.5">{{ fr() ? 'Enregistrez d’abord la configuration.' : 'Save the configuration first.' }}</div>
                  </div>
                }
              </div>
            </bbc-card>
          </div>
        }
      }
    </div>
  `,
})
export class SettingsComponent {
  protected i18n = inject(I18nService);
  protected auth = inject(AuthService);
  private api = inject(SettingsApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected matrix = signal<PermissionMatrix | null>(null);
  protected currentUser = this.auth.user;
  /** Faux pour un admin de section : masque les réglages école-entière. */
  protected schoolWide = this.auth.schoolWide;
  protected section = this.auth.section;
  protected tab = signal<SettingsTab>('academic');
  /** Access Control is an administrator-only surface, including its settings tabs. */
  protected canViewAccessControl = computed(() =>
    this.auth.canAction('PERMISSION_VIEW') || this.auth.canAction('ROLE_MANAGE'));
  protected canViewMail = computed(() =>
    this.auth.canAction('MAIL_CONFIG_VIEW') || this.auth.canAction('MAIL_CONFIG_MANAGE'));
  protected activeTab = computed<SettingsTab>(() => {
    const current = this.tab();
    if (!this.canViewAccessControl() && (current === 'perms' || current === 'roles' || current === 'admins')) return 'general';
    if (!this.canViewMail() && current === 'mail') return 'general';
    return current;
  });
  private matrixRequested = false;
  private mailRequested = false;

  /** Enable only the V2 action that governs the active settings surface. */
  protected get canWrite(): boolean {
    switch (this.activeTab()) {
      case 'sessions': return this.auth.canAction('SESSION_MANAGE');
      case 'general': return this.auth.canAction('SCHOOL_PROFILE_MANAGE');
      case 'calendar': return this.auth.canAction('CALENDAR_MANAGE');
      case 'discipline': return this.auth.canAction('DISCIPLINE_CATALOG_MANAGE');
      case 'perms': return this.auth.canAction('PERMISSION_MANAGE');
      case 'roles': return this.auth.canAction('ROLE_MANAGE');
      case 'mail': return this.auth.canAction('MAIL_CONFIG_MANAGE');
      default: return false;
    }
  }

  // School profile
  protected school = signal<SchoolProfileView | null>(null);
  protected schoolDraft: SchoolProfileUpdate = this.emptySchool();
  protected savingSchool = signal(false);
  protected schoolMsg = signal<{ ok: boolean; text: string } | null>(null);

  // Roles
  protected newRole: RoleUpsert = { labelFr: '', labelEn: '' };
  protected creatingRole = signal(false);
  protected editingRole = signal<RoleView | null>(null);
  protected editRoleDraft: RoleUpsert = { labelFr: '', labelEn: '' };
  protected roleMsg = signal<{ ok: boolean; text: string } | null>(null);

  // Holidays
  protected holidays = signal<HolidayView[]>([]);
  protected holidayDraft = { date: '', label: '' };
  protected savingHoliday = signal(false);

  // Discipline catalog
  protected catalog = signal<CatalogItemView[]>([]);
  protected typeDraft = { labelFr: '', labelEn: '' };
  protected sanctionDraft = { labelFr: '', labelEn: '' };

  // Administrateurs
  protected readonly SECTIONS: Section[] = ['maternelle', 'primary', 'secondary'];
  protected admins = signal<AdminView[]>([]);
  protected adminDraft: AdminCreate = { name: '', section: 'primary', email: '', phone: '' };
  protected creatingAdmin = signal(false);
  /** Identifiant de la ligne en cours de traitement — désarme ses seuls boutons. */
  protected busyAdmin = signal<string | null>(null);
  protected adminMsg = signal<{ ok: boolean; text: string } | null>(null);

  // SMTP / mail config
  protected mailDraft: MailConfigUpdate = {
    enabled: false, host: '', port: 587, username: '', password: '',
    fromAddress: '', fromName: '', useTls: true, notifyOnUserCreate: true,
  };
  protected passwordSet = signal(false);
  protected savingMail = signal(false);
  protected testingMail = signal(false);
  protected testTo = '';
  protected mailMsg = signal<{ ok: boolean; text: string } | null>(null);

  protected fr = () => this.i18n.lang() === 'fr';

  /**
   * AcademicSetupComponent loads setup endpoints in its constructor. Keep it
   * out of read-only settings sessions unless the server has granted at least
   * one setup action (including a contextual grant). This prevents ordinary
   * settings readers from generating predictable 403 console/network noise.
   *
   * <p>La scolarité — classes, matières, coefficients — se configure par cycle :
   * elle reste ouverte à l'admin de section, cloisonnée par le serveur. Tout le
   * reste engage l'établissement entier et ne s'affiche que pour l'admin
   * principal ; l'API le refuserait de toute façon.
   */
  protected canViewAcademicSetup = computed(() => [
    'CLASS_MANAGE',
    'SUBJECT_MANAGE',
    'CURRICULUM_MANAGE',
    'CURRICULUM_CLASS_MANAGE',
    'ACADEMIC_ACCESS_DELEGATE',
    'ACADEMIC_ASSESSMENT_MANAGE',
    'DOCUMENT_DESIGN_PUBLISH',
  ].some((action) => {
    const state = this.auth.actionState(action);
    return state === 'ALLOW' || state === 'CONTEXT_REQUIRED';
  }));

  protected tabs = computed(() => {
    const academic = this.canViewAcademicSetup()
      ? [{ id: 'academic', label: this.fr() ? 'Scolarité' : 'Academics' }]
      : [];
    if (!this.schoolWide()) return academic;
    return [
      ...academic,
      { id: 'sessions', label: this.fr() ? 'Années & périodes' : 'Sessions & terms' },
      { id: 'general', label: this.fr() ? 'Général' : 'General' },
      { id: 'calendar', label: this.fr() ? 'Calendrier' : 'Calendar' },
      { id: 'discipline', label: this.fr() ? 'Discipline' : 'Discipline' },
      ...(this.canViewAccessControl() ? [
        { id: 'admins', label: this.fr() ? 'Administrateurs' : 'Administrators' },
        { id: 'perms', label: this.fr() ? 'Permissions' : 'Permissions' },
        { id: 'roles', label: this.fr() ? 'Rôles' : 'Roles' },
      ] : []),
      ...(this.canViewMail() ? [{ id: 'mail', label: this.fr() ? 'Messagerie' : 'E-mail' }] : []),
    ];
  });

  protected catalogTypes = computed(() => this.catalog().filter((c) => c.kind === 'type' && c.active));
  protected catalogSanctions = computed(() => this.catalog().filter((c) => c.kind === 'sanction' && c.active));

  protected readerRows = computed(() => {
    const f = this.fr();
    return [
      { label: f ? 'Statut' : 'Status', value: f ? 'En ligne' : 'Online', online: true },
      { label: f ? 'Emplacement' : 'Location', value: f ? 'Entrée principale' : 'Main gate', online: false },
      { label: f ? 'Modèle' : 'Model', value: 'ZKTeco MultiBio 800', online: false },
      { label: f ? 'Dernière synchro' : 'Last sync', value: f ? 'Il y a 3 min' : '3 min ago', online: false },
    ];
  });

  protected legend = computed(() => {
    const f = this.fr();
    return [
      { level: 'none', label: f ? 'Aucun' : 'None', dot: 'bg-slate-300' },
      { level: 'read', label: f ? 'Lecture' : 'Read', dot: 'bg-amber-500' },
      { level: 'write', label: f ? 'Complet' : 'Write', dot: 'bg-emerald-500' },
    ];
  });

  private readonly NEXT: Record<string, Level> = {
    none: 'read',
    read: 'write',
    write: 'none',
  };

  constructor() {
    // Un admin de section n'a que l'onglet Scolarité : n'appeler que ce qu'il
    // peut lire évite une volée de 403 au chargement de l'écran.
    this.loadSchool();
    if (!this.schoolWide()) return;
    const requestedTab = this.route.snapshot.queryParamMap.get('tab') as SettingsTab | null;
    if (requestedTab && ['academic', 'sessions', 'general', 'perms', 'roles', 'mail', 'calendar', 'discipline', 'admins'].includes(requestedTab)) this.tab.set(requestedTab);
    effect(() => {
      if (this.canViewAccessControl() && !this.matrixRequested) {
        this.matrixRequested = true;
        this.reload();
        this.loadAdmins();
      }
      if (this.canViewMail() && !this.mailRequested) {
        this.mailRequested = true;
        this.loadMail();
      }
    });
    this.loadHolidays();
    this.loadCatalog();
  }

  protected onTab(id: SettingsTab): void {
    if ((id === 'perms' || id === 'roles' || id === 'admins') && !this.canViewAccessControl()) {
      this.tab.set('general');
      return;
    }
    if (id === 'mail' && !this.canViewMail()) {
      this.tab.set('general');
      return;
    }
    this.tab.set(id);
    this.router.navigate([], { relativeTo: this.route, queryParams: { ...this.route.snapshot.queryParams, tab: id }, queryParamsHandling: 'merge' });
  }

  // ---- Administrateurs ------------------------------------------------------

  protected sectionLabel(section: Section): string {
    const fr = this.fr();
    if (section === 'maternelle') return fr ? 'Maternelle' : 'Nursery';
    if (section === 'primary') return fr ? 'Primaire' : 'Primary';
    return fr ? 'Secondaire' : 'Secondary';
  }

  private loadAdmins(): void {
    this.api.listAdmins().subscribe((rows) => this.admins.set(rows));
  }

  protected createAdmin(): void {
    if (!this.adminDraft.name.trim()) return;
    this.creatingAdmin.set(true);
    this.adminMsg.set(null);
    this.api.createAdmin({ ...this.adminDraft, name: this.adminDraft.name.trim() }).subscribe({
      next: (res) => {
        this.creatingAdmin.set(false);
        // Le mot de passe n'est jamais rendu ici : le message dit seulement si
        // l'e-mail est parti, et quoi faire dans le cas contraire.
        this.adminMsg.set({ ok: res.emailSent, text: res.message });
        this.adminDraft = { name: '', section: this.adminDraft.section, email: '', phone: '' };
        this.loadAdmins();
      },
      error: (e) => {
        this.creatingAdmin.set(false);
        this.adminMsg.set({ ok: false, text: this.errorText(e) });
      },
    });
  }

  protected moveAdmin(a: AdminView, section: Section): void {
    if (section === a.section) return;
    this.busyAdmin.set(a.userId);
    this.api.changeAdminSection(a.userId, section).subscribe({
      next: () => { this.busyAdmin.set(null); this.loadAdmins(); },
      error: (e) => {
        this.busyAdmin.set(null);
        this.adminMsg.set({ ok: false, text: this.errorText(e) });
        this.loadAdmins();   // la ligne reprend sa valeur serveur
      },
    });
  }

  protected toggleAdmin(a: AdminView): void {
    this.busyAdmin.set(a.userId);
    this.api.setAdminActive(a.userId, !a.active).subscribe({
      next: () => { this.busyAdmin.set(null); this.loadAdmins(); },
      error: (e) => {
        this.busyAdmin.set(null);
        this.adminMsg.set({ ok: false, text: this.errorText(e) });
      },
    });
  }

  protected resetAdmin(a: AdminView): void {
    this.busyAdmin.set(a.userId);
    this.api.resetAdminCredentials(a.userId).subscribe({
      next: (res) => {
        this.busyAdmin.set(null);
        this.adminMsg.set({ ok: res.emailSent, text: res.message });
      },
      error: (e) => {
        this.busyAdmin.set(null);
        this.adminMsg.set({ ok: false, text: this.errorText(e) });
      },
    });
  }

  private errorText(e: unknown): string {
    const message = (e as { error?: { message?: string } })?.error?.message;
    return message ?? (this.fr() ? 'Opération refusée' : 'Operation refused');
  }

  private reload(): void {
    this.api.getMatrix().subscribe((m) => this.matrix.set(m));
  }

  private emptySchool(): SchoolProfileUpdate {
    return {
      name: '', motto: '', city: '', country: '', address: '', phone: '', email: '',
      currency: 'XAF', authority: '', schoolStartTime: '07:30', schoolEndTime: '15:30',
    };
  }

  private loadSchool(): void {
    this.api.getSchool().subscribe((s) => {
      this.school.set(s);
      this.schoolDraft = {
        name: s.name,
        motto: s.motto ?? '',
        city: s.city ?? '',
        country: s.country ?? '',
        address: s.address ?? '',
        phone: s.phone ?? '',
        email: s.email ?? '',
        currency: s.currency || 'XAF',
        authority: s.authority ?? '',
        schoolStartTime: s.schoolStartTime || '07:30',
        schoolEndTime: s.schoolEndTime || '15:30',
      };
    });
  }

  protected saveSchool(): void {
    if (!this.schoolDraft.name?.trim()) return;
    this.savingSchool.set(true);
    this.schoolMsg.set(null);
    this.api.updateSchool(this.schoolDraft).subscribe({
      next: (s) => {
        this.school.set(s);
        this.savingSchool.set(false);
        this.schoolMsg.set({ ok: true, text: this.fr() ? 'Profil enregistré.' : 'Profile saved.' });
      },
      error: (e) => {
        this.savingSchool.set(false);
        this.schoolMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec de l’enregistrement.' : 'Save failed.') });
      },
    });
  }

  private loadHolidays(): void {
    this.api.listHolidays().subscribe((h) => this.holidays.set(h));
  }

  protected addHoliday(): void {
    const date = this.holidayDraft.date;
    const label = this.holidayDraft.label.trim();
    if (!date || !label) return;
    this.savingHoliday.set(true);
    this.api.addHoliday(date, label).subscribe({
      next: () => {
        this.holidayDraft = { date: '', label: '' };
        this.savingHoliday.set(false);
        this.loadHolidays();
      },
      error: () => this.savingHoliday.set(false),
    });
  }

  protected removeHoliday(h: HolidayView): void {
    this.api.deleteHoliday(h.id).subscribe(() => this.loadHolidays());
  }

  private loadCatalog(): void {
    this.api.listCatalog().subscribe((c) => this.catalog.set(c));
  }

  protected addCatalog(kind: 'type' | 'sanction'): void {
    const draft = kind === 'type' ? this.typeDraft : this.sanctionDraft;
    if (!draft.labelFr.trim()) return;
    const body: CatalogItemUpsert = {
      kind,
      labelFr: draft.labelFr.trim(),
      labelEn: draft.labelEn.trim() || undefined,
    };
    this.api.createCatalog(body).subscribe({
      next: () => {
        if (kind === 'type') this.typeDraft = { labelFr: '', labelEn: '' };
        else this.sanctionDraft = { labelFr: '', labelEn: '' };
        this.loadCatalog();
      },
    });
  }

  protected removeCatalog(item: CatalogItemView): void {
    this.api.deleteCatalog(item.id).subscribe(() => this.loadCatalog());
  }

  protected createRole(): void {
    if (!this.newRole.labelFr.trim()) return;
    this.creatingRole.set(true);
    this.roleMsg.set(null);
    this.api.createRole({
      labelFr: this.newRole.labelFr.trim(),
      labelEn: this.newRole.labelEn?.trim() || undefined,
    }).subscribe({
      next: () => {
        this.newRole = { labelFr: '', labelEn: '' };
        this.creatingRole.set(false);
        this.roleMsg.set({ ok: true, text: this.fr() ? 'Rôle créé.' : 'Role created.' });
        this.reload();
      },
      error: (e) => {
        this.creatingRole.set(false);
        this.roleMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec.' : 'Failed.') });
      },
    });
  }

  protected startRoleEdit(role: RoleView): void {
    this.editingRole.set(role);
    this.editRoleDraft = { labelFr: role.labelFr, labelEn: role.labelEn };
  }

  protected cancelRoleEdit(): void {
    this.editingRole.set(null);
  }

  protected saveRoleEdit(): void {
    const role = this.editingRole();
    if (!role || !this.editRoleDraft.labelFr.trim()) return;
    this.roleMsg.set(null);
    this.api.updateRole(role.code, {
      labelFr: this.editRoleDraft.labelFr.trim(),
      labelEn: this.editRoleDraft.labelEn?.trim() || undefined,
    }).subscribe({
      next: () => {
        this.editingRole.set(null);
        this.roleMsg.set({ ok: true, text: this.fr() ? 'Libellé mis à jour.' : 'Label updated.' });
        this.reload();
      },
      error: (e) => this.roleMsg.set({
        ok: false,
        text: e?.error?.message ?? (this.fr() ? 'Modification impossible.' : 'Update failed.'),
      }),
    });
  }

  protected deleteRole(role: RoleView): void {
    if (role.builtin) return;
    const ok = confirm(this.fr()
      ? `Supprimer le rôle « ${role.labelFr} » ?`
      : `Delete role “${role.labelEn || role.labelFr}”?`);
    if (!ok) return;
    this.api.deleteRole(role.code).subscribe({
      next: () => this.reload(),
      error: (e) => this.roleMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Suppression impossible.' : 'Delete failed.') }),
    });
  }

  private loadMail(): void {
    this.api.getMail().subscribe((c) => {
      this.mailDraft = {
        enabled: c.enabled, host: c.host ?? '', port: c.port, username: c.username ?? '',
        password: '', fromAddress: c.fromAddress ?? '', fromName: c.fromName ?? '',
        useTls: c.useTls, notifyOnUserCreate: c.notifyOnUserCreate,
      };
      this.passwordSet.set(c.passwordSet);
    });
  }

  protected saveMail(): void {
    this.savingMail.set(true);
    this.mailMsg.set(null);
    this.api.updateMail(this.mailDraft).subscribe({
      next: (c) => {
        this.passwordSet.set(c.passwordSet);
        this.mailDraft.password = '';
        this.savingMail.set(false);
        this.mailMsg.set({ ok: true, text: this.fr() ? 'Configuration enregistrée.' : 'Settings saved.' });
      },
      error: (e) => {
        this.savingMail.set(false);
        this.mailMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec de l’enregistrement.' : 'Save failed.') });
      },
    });
  }

  protected testMail(): void {
    if (!this.testTo) return;
    this.testingMail.set(true);
    this.mailMsg.set(null);
    this.api.testMail(this.testTo).subscribe({
      next: () => {
        this.testingMail.set(false);
        this.mailMsg.set({ ok: true, text: (this.fr() ? 'E-mail de test envoyé à ' : 'Test e-mail sent to ') + this.testTo });
      },
      error: (e) => {
        this.testingMail.set(false);
        this.mailMsg.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Échec de l’envoi.' : 'Send failed.') });
      },
    });
  }

  protected levelOf(roleCode: string, module: string): Level {
    const m = this.matrix();
    if (!m) return 'none';
    const row = m.matrix[roleCode];
    return ((row && row[module]) as Level) ?? 'none';
  }

  protected parentBlocked(roleCode: string, module: string): boolean {
    return roleCode === 'parent' && module !== 'parent';
  }

  protected cycle(roleCode: string, module: string): void {
    if (!this.canWrite) return;
    if (roleCode === 'parent' && module !== 'parent') {
      alert(this.fr()
        ? 'Le rôle parent ne peut pas recevoir d’accès aux modules du personnel.'
        : 'The parent role cannot be granted staff module access.');
      return;
    }
    const m = this.matrix();
    if (!m) return;

    const current = this.levelOf(roleCode, module);
    const newLevel = this.NEXT[current] ?? 'none';

    // Optimistic local update (clone to keep the signal immutable).
    const nextMatrix: Record<string, Record<string, string>> = {};
    for (const code of Object.keys(m.matrix)) {
      nextMatrix[code] = { ...m.matrix[code] };
    }
    if (!nextMatrix[roleCode]) nextMatrix[roleCode] = {};
    nextMatrix[roleCode][module] = newLevel;
    this.matrix.set({ ...m, matrix: nextMatrix });

    this.api.update([{ roleCode, module, level: newLevel }]).subscribe({
      next: (updated) => this.matrix.set(updated),
      error: () => this.reload(), // revert to server truth on failure
    });
  }

  protected cellClass(level: Level): string {
    switch (level) {
      case 'write':
        return 'bg-emerald-100 text-emerald-700';
      case 'read':
        return 'bg-amber-100 text-amber-700';
      default:
        return 'bg-slate-100 text-slate-400';
    }
  }

  protected dotClass(level: Level): string {
    switch (level) {
      case 'write':
        return 'bg-emerald-500';
      case 'read':
        return 'bg-amber-500';
      default:
        return 'bg-slate-300';
    }
  }

  protected cellLabel(level: Level): string {
    const f = this.fr();
    switch (level) {
      case 'write':
        return f ? 'Complet' : 'Write';
      case 'read':
        return f ? 'Lecture' : 'Read';
      default:
        return f ? 'Aucun' : 'None';
    }
  }

  protected roleColor(role: RoleView): string {
    return role.builtin
      ? 'bg-brand-100 text-brand-700'
      : 'bg-gold-100 text-gold-600';
  }
}
