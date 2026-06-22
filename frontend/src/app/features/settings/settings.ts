import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SettingsApi, PermissionMatrix, RoleView, MailConfigUpdate } from './settings.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, TabsComponent,
} from '../../core/ui';
import { AcademicSetupComponent } from '../setup/academic-setup';

type Level = 'none' | 'read' | 'write';

@Component({
  selector: 'bbc-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, TabsComponent, AcademicSetupComponent],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('settings')"
        [subtitle]="fr() ? 'Configuration générale de l’établissement' : 'General school configuration'">
        <div right class="flex items-center gap-2">
          @if (currentUser(); as u) {
            <span class="inline-flex items-center gap-1.5 h-9 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-mute">
              <bbc-icon name="shield" [s]="14" /> {{ u.role }}
            </span>
          }
        </div>
      </bbc-page-header>

      <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="tab.set($any($event))" />

      @switch (tab()) {
        <!-- ===================== ACADEMIC SETUP ===================== -->
        @case ('academic') {
          <bbc-academic-setup />
        }

        <!-- ===================== GENERAL ===================== -->
        @case ('general') {
          <div class="grid grid-cols-12 gap-4">
            <bbc-card className="col-span-12 lg:col-span-6" [title]="fr() ? 'Établissement' : 'School'">
              <div action class="w-8 h-8 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center">
                <bbc-icon name="building" [s]="18" />
              </div>
              <div class="space-y-0.5">
                @for (r of schoolRows(); track r.label) {
                  <div class="flex items-center justify-between py-2 border-b border-slate-50 last:border-0">
                    <div class="text-sm text-mute">{{ r.label }}</div>
                    <div class="text-sm font-semibold text-ink">{{ r.value }}</div>
                  </div>
                }
              </div>
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
                            @if (canWrite) {
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
          <bbc-card [title]="fr() ? 'Rôles utilisateurs' : 'User roles'"
            [subtitle]="fr() ? 'Rôles définis dans le système' : 'Roles defined in the system'">
            <div action class="w-8 h-8 rounded-lg bg-violet-50 text-violet-600 flex items-center justify-center">
              <bbc-icon name="users" [s]="18" />
            </div>
            @if (matrix(); as m) {
              <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                @for (role of m.roles; track role.code) {
                  <div class="p-3 rounded-lg" [class]="roleCard(role)">
                    <div class="text-[10px] uppercase tracking-wide font-semibold">
                      {{ fr() ? role.labelFr : role.labelEn }}
                    </div>
                    <div class="text-sm font-bold mt-0.5">
                      {{ role.builtin ? (fr() ? 'Intégré' : 'Built-in') : (fr() ? 'Personnalisé' : 'Custom') }}
                    </div>
                  </div>
                }
              </div>
            } @else {
              <bbc-empty icon="users" [label]="fr() ? 'Aucun rôle' : 'No roles'" />
            }
          </bbc-card>
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
                  <span class="text-sm text-ink">{{ fr() ? 'STARTTLS (chiffrement)' : 'STARTTLS (encryption)' }}</span>
                </label>

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

  protected matrix = signal<PermissionMatrix | null>(null);
  protected canWrite = this.auth.can('settings', 'write');
  protected currentUser = this.auth.user;
  protected tab = signal<'academic' | 'general' | 'perms' | 'roles' | 'mail'>('academic');

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

  protected tabs = computed(() => {
    const t = [
      { id: 'academic', label: this.fr() ? 'Scolarité' : 'Academics' },
      { id: 'general', label: this.fr() ? 'Général' : 'General' },
      { id: 'perms', label: this.fr() ? 'Permissions' : 'Permissions' },
      { id: 'roles', label: this.fr() ? 'Rôles' : 'Roles' },
      { id: 'mail', label: this.fr() ? 'Messagerie' : 'E-mail' },
    ];
    return t;
  });

  protected schoolRows = computed(() => {
    const f = this.fr();
    return [
      { label: f ? 'Nom' : 'Name', value: 'Bayo Bilingual Complex' },
      { label: f ? 'Ville' : 'City', value: 'Maroua, Cameroun' },
      { label: f ? 'Année scolaire' : 'Academic year', value: '2025-2026' },
      { label: f ? 'Devise' : 'Currency', value: 'FCFA (XAF)' },
      { label: f ? 'Langues' : 'Languages', value: 'Français · English' },
    ];
  });

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
    this.reload();
    this.loadMail();
  }

  private reload(): void {
    this.api.getMatrix().subscribe((m) => this.matrix.set(m));
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

  protected cycle(roleCode: string, module: string): void {
    if (!this.canWrite) return;
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

  protected roleCard(role: RoleView): string {
    return role.builtin
      ? 'bg-brand-50 text-brand-700'
      : 'bg-gold-50 text-gold-600';
  }
}
