import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { downloadCsv } from '../../core/csv';
import { ClassView, SetupApi } from '../../core/setup.api';
import { I18nService } from '../../core/i18n.service';
import { CardComponent, IconComponent, PageHeaderComponent } from '../../core/ui';
import { FamilyImportRow, FamilyImportView, StudentApi } from './students.api';

@Component({
  selector: 'bbc-family-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CardComponent, IconComponent, PageHeaderComponent],
  template: `
    <div class="fade-in max-w-5xl mx-auto space-y-5">
      <bbc-page-header [title]="fr()?'Import familial sécurisé':'Secure family import'"
        [subtitle]="fr()?'Prévisualiser, corriger puis valider sans doublons':'Preview, correct, then commit without duplicates'">
        <a right routerLink="/students" class="btn-secondary"><bbc-icon name="chevronLeft" [s]="14"/> {{fr()?'Retour':'Back'}}</a>
      </bbc-page-header>
      <bbc-card>
        <div class="grid md:grid-cols-3 gap-4 mb-5">
          <label><span class="label">{{fr()?'Classe cible':'Target class'}}</span><select class="input w-full" [(ngModel)]="classId"><option value="">—</option>@for(c of classes();track c.id){<option [value]="c.id">{{c.name}}</option>}</select></label>
          <label><span class="label">{{fr()?'Relation par défaut':'Default relationship'}}</span><input class="input w-full" [(ngModel)]="relationship"/></label>
          <label><span class="label">{{fr()?'Mode d’accès':'Access mode'}}</span><select class="input w-full" [(ngModel)]="accessMode"><option value="SEND_INVITE">Invitation</option><option value="NO_PORTAL">{{fr()?'Sans portail':'No portal'}}</option></select></label>
        </div>

        <div class="p-4 rounded-xl border border-dashed bg-slate-50 mb-4">
          <div class="flex flex-wrap gap-2 items-center">
            <label class="btn-secondary cursor-pointer"><bbc-icon name="upload" [s]="14"/> {{fr()?'Choisir CSV / Excel':'Choose CSV / Excel'}}<input class="hidden" type="file" accept=".csv,.xlsx,.xls,.xlsm" (change)="onFile($event)"/></label>
            <button class="btn-secondary" type="button" (click)="downloadTemplate()">{{fr()?'Télécharger le modèle':'Download template'}}</button>
            @if(fileName()){<span class="text-sm text-mute">{{fileName()}}</span>}
          </div>
          <p class="text-xs text-mute mt-2">external_key;first_name;last_name;sex;guardian_name;guardian_email;guardian_phone</p>
        </div>

        <label class="label">{{fr()?'Aperçu modifiable':'Editable preview'}}</label>
        <textarea class="input w-full font-mono" rows="10" [(ngModel)]="text" placeholder="EXT-001;Amina;NANA;F;Mme Nana;parent@example.com;+237..."></textarea>
        <div class="flex justify-end mt-4"><button class="btn-primary" [disabled]="!classId||!text.trim()||working()" (click)="dryRun()">{{fr()?'Prévisualiser l’import':'Preview import'}}</button></div>

        @if(result();as r){
          <div class="mt-6 space-y-3">
            <div class="grid grid-cols-4 gap-3"><div class="stat"><b>{{r.totalRows}}</b><span>Total</span></div><div class="stat"><b>{{r.validRows}}</b><span>{{fr()?'Valides':'Valid'}}</span></div><div class="stat"><b>{{r.createdRows}}</b><span>{{fr()?'Créés':'Created'}}</span></div><div class="stat"><b>{{r.failedRows}}</b><span>{{fr()?'Erreurs':'Errors'}}</span></div></div>
            <div class="border rounded-xl overflow-hidden">@for(row of r.rows;track row.externalKey){<div class="grid grid-cols-[4rem_1fr_7rem_2fr] gap-2 p-2 border-t text-xs"><span>#{{row.rowNumber}}</span><b>{{row.studentName}}</b><span>{{row.outcome}}</span><span>{{row.message}}</span></div>}</div>
            <div class="flex justify-between gap-2"><button class="btn-secondary" type="button" (click)="downloadResults()">{{fr()?'Télécharger le rapport':'Download report'}}</button>
            @if(r.status==='VALIDATED'){<button class="btn-primary" [disabled]="working()" (click)="commit()">{{fr()?'Valider l’import':'Commit import'}}</button>}</div>
            @if(r.status==='VALIDATED'){<div class="p-3 rounded-xl bg-blue-50 text-blue-800 text-sm">{{fr()?'La prévisualisation n’a créé aucun élève. Seules les lignes valides seront exécutées.':'Preview created no students. Only valid rows will be executed.'}}</div>}
            @else{<div class="p-3 rounded-xl bg-emerald-50 text-emerald-800">{{fr()?'Import terminé. Une relance ne dupliquera pas les lignes déjà validées.':'Import completed. Retrying will not duplicate committed rows.'}}</div>}
          </div>
        }
        @if(error()){<div class="mt-3 p-3 bg-rose-50 text-rose-700 rounded-xl">{{error()}}</div>}
      </bbc-card>
    </div>`,
  styles: [`.stat{display:flex;flex-direction:column;padding:.75rem;background:#f8fafc;border-radius:.75rem}.stat b{font-size:1.25rem}.stat span{font-size:.7rem;color:#64748b}`],
})
export class FamilyImportComponent {
  private api = inject(StudentApi);
  private setup = inject(SetupApi);
  private i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected classes = signal<ClassView[]>([]);
  protected result = signal<FamilyImportView | null>(null);
  protected working = signal(false);
  protected error = signal<string | null>(null);
  protected fileName = signal('');
  protected classId = '';
  protected relationship = 'GUARDIAN';
  protected accessMode = 'SEND_INVITE';
  protected text = '';

  constructor() { this.setup.listClasses().subscribe(c => this.classes.set(c)); }

  protected async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.error.set(null);
    this.result.set(null);
    try {
      const XLSX = await import('xlsx');
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
      const sheet = workbook.Sheets[workbook.SheetNames[0]];
      if (!sheet) throw new Error('empty');
      this.text = XLSX.utils.sheet_to_csv(sheet, { FS: ';', RS: '\n' });
      this.fileName.set(file.name);
    } catch {
      this.error.set(this.fr() ? 'Fichier illisible. Utilisez le modèle CSV ou Excel.' : 'Unreadable file. Use the CSV or Excel template.');
    } finally { input.value = ''; }
  }

  protected downloadTemplate(): void {
    downloadCsv('modele-import-familles.csv', ['external_key','first_name','last_name','sex','guardian_name','guardian_email','guardian_phone'], [['EXT-001','Amina','NANA','F','Mme Nana','parent@example.com','+237...']]);
  }

  protected downloadResults(): void {
    const result = this.result();
    if (!result) return;
    const safe = (value: string) => /^[=+\-@]/.test(value) ? `'${value}` : value;
    downloadCsv(`rapport-import-familles-${result.jobId}.csv`, ['row','external_key','student','outcome','message'], result.rows.map(row => [row.rowNumber,safe(row.externalKey),safe(row.studentName),row.outcome,safe(row.message)]));
  }

  private rows(): FamilyImportRow[] {
    const lines = this.text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    if (lines.length && /external[_ ]?key|clé/i.test(lines[0])) lines.shift();
    return lines.map(line => {
      const [externalKey, firstName, lastName, sex, displayName, email, phone] = line.split(';').map(value => value.replace(/^"|"$/g, '').trim());
      return { externalKey, firstName, lastName, sex, classId: this.classId, guardians: [{ displayName, email, phone, relationshipType: this.relationship, accessMode: this.accessMode }] };
    });
  }

  protected dryRun(): void {
    const rows = this.rows();
    if (!rows.length) { this.error.set(this.fr() ? 'Aucune ligne exploitable.' : 'No usable row.'); return; }
    this.working.set(true); this.error.set(null);
    this.api.familyImportDryRun(rows, this.fileName() || 'family-import.csv').subscribe({ next: result => { this.working.set(false); this.result.set(result); }, error: e => { this.working.set(false); this.error.set(e.error?.message || 'Import impossible'); } });
  }

  protected commit(): void {
    const id = this.result()?.jobId;
    if (!id) return;
    this.working.set(true); this.error.set(null);
    this.api.familyImportCommit(id).subscribe({ next: result => { this.working.set(false); this.result.set(result); }, error: e => { this.working.set(false); this.error.set(e.error?.message || 'Import impossible'); } });
  }
}
