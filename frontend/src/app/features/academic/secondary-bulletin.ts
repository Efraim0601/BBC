import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { SchoolService } from '../../core/school.service';
import { BulletinLine, BulletinView } from './academic.api';

/** Official secondary report-card family. Its language follows the class subsystem. */
@Component({
  selector: 'bbc-secondary-bulletin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="report-page" [attr.lang]="isFrench() ? 'fr' : 'en'">
      <header class="institutional-header">
        <section class="authority-block">
          <strong>{{ leftAuthority().country }}</strong><em>{{ leftAuthority().motto }}</em><span>***********</span>
          <strong>{{ leftAuthority().ministry }}</strong><span>***********</span>
          <strong>{{ leftAuthority().regional }}</strong><span>***********</span>
          <span>{{ leftAuthority().divisional }}</span><span>***********</span>
          <strong>{{ schoolName() }}</strong><span>{{ addressLine(leftAuthority().french) }}</span>
        </section>
        <section class="school-mark">
          <img src="bbc-logo.png" alt="" /><strong>{{ schoolName() }}</strong>
          <span>{{ isFrench() ? 'Immatriculation' : 'Registration' }} : {{ schoolCode() }}</span>
        </section>
        <section class="authority-block">
          <strong>{{ rightAuthority().country }}</strong><em>{{ rightAuthority().motto }}</em><span>***********</span>
          <strong>{{ rightAuthority().ministry }}</strong><span>***********</span>
          <strong>{{ rightAuthority().regional }}</strong><span>***********</span>
          <span>{{ rightAuthority().divisional }}</span><span>***********</span>
          <strong>{{ schoolName() }}</strong><span>{{ addressLine(rightAuthority().french) }}</span>
        </section>
      </header>

      <div class="document-title"><strong>{{ reportTitle() }}</strong><em>{{ isFrench() ? 'Année Scolaire' : 'School Year' }} {{ view().schoolYear || '-' }}</em></div>

      <table class="identity-table"><tbody>
        <tr><th>{{ isFrench() ? "Nom et Prénoms de l'élève" : 'Name of Student' }} :</th><td colspan="3" class="identity-value">{{ view().studentName }}</td><th>{{ isFrench() ? 'Classe' : 'Class' }} :</th><td class="identity-value">{{ view().className }}</td></tr>
        <tr><th>{{ isFrench() ? 'Date et lieu de Naissance' : 'Date and place of birth' }} :</th><td colspan="2" class="identity-value">{{ birth() }}</td><th>{{ isFrench() ? 'Genre' : 'Gender' }} :</th><td class="identity-value center">{{ view().sex || '-' }}</td><th>{{ isFrench() ? 'Effectif' : 'Class enrolment' }} :</th><td class="identity-value center">{{ view().classSize }}</td></tr>
        <tr><th>{{ isFrench() ? 'Identifiant unique' : 'Unique identification number' }} :</th><td class="identity-value">{{ view().matricule || '-' }}</td><th>{{ isFrench() ? 'Redoublant(e)' : 'Repeater' }} :</th><td class="identity-value center">{{ yesNo(view().repeater) }}</td><th>{{ isFrench() ? 'Professeur principal' : 'Class master' }} :</th><td class="identity-value">{{ view().classMasterName || '-' }}</td></tr>
        <tr><th>{{ isFrench() ? 'Noms et contacts des Parents / Tuteurs' : "Parent's/Guardian's name and contact" }} :</th><td colspan="5" class="identity-value">{{ view().parentContact || '-' }}</td></tr>
      </tbody></table>

      @if (annual()) {
        <table class="marks-table annual-table">
          <colgroup><col class="annual-subject" /><col class="annual-term" /><col class="annual-term" /><col class="annual-term" /><col class="annual-average" /><col class="annual-coef" /><col class="annual-product" /><col class="annual-grade" /><col class="annual-range" /><col class="annual-remark" /></colgroup>
          <thead><tr><th>{{ isFrench() ? "Matières et nom de l'enseignant" : "Subject and Teacher's Names" }}</th><th>{{ isFrench() ? 'TRIM 1' : '1st Term' }}</th><th>{{ isFrench() ? 'TRIM 2' : '2nd Term' }}</th><th>{{ isFrench() ? 'TRIM 3' : '3rd Term' }}</th><th>{{ isFrench() ? 'Moy' : 'AV' }}</th><th>{{ isFrench() ? 'Coéf.' : 'Coef.' }}</th><th>Prod</th><th>{{ isFrench() ? 'Côte' : 'Grade' }}</th><th>[Min - Max]</th><th>{{ isFrench() ? "Appréciations et Visa de l'enseignant" : "Remarks and Teacher's signature" }}</th></tr></thead>
          <tbody>
            @for (line of view().lines; track line.subjectCode) {
              <tr><td class="subject-cell"><strong>{{ line.subjectLabel }}</strong><span>{{ line.teacherName || '-' }}</span></td>
                @for (period of annualPeriods(); track period) { <td>{{ mark(periodMark(line, period)) }}</td> }
                @for (missing of missingAnnualColumns(); track missing) { <td>-</td> }
                <td class="strong-cell">{{ mark(line.mark) }}</td><td>{{ line.coef }}</td><td class="strong-cell">{{ mark(line.weighted) }}</td><td class="strong-cell">{{ grade(line.mark) }}</td><td>{{ range() }}</td><td class="remark-cell">{{ remark(line) }}</td></tr>
            }
            <tr class="total-row"><th colspan="5">TOTAL</th><td>{{ totalCoefficient() }}</td><td>{{ mark(totalWeighted()) }}</td><th colspan="3">{{ isFrench() ? 'Moyenne' : 'STUDENT AVERAGE' }} : {{ mark(view().average) }}</th></tr>
          </tbody>
        </table>
        <div class="term-recap"><strong>{{ isFrench() ? 'RAPPEL DES MOYENNES TRIMESTRIELLES:' : 'TERM AVERAGES' }}</strong>
          @for (period of annualPeriods(); track period; let i = $index) { <span>{{ termName(i + 1) }} : {{ mark(periodAverage(period)) }}</span> }
          @for (missing of missingAnnualColumns(); track missing; let i = $index) { <span>{{ termName(annualPeriods().length + i + 1) }} : -</span> }
        </div>
      } @else {
        <table class="marks-table term-table">
          <colgroup><col class="term-subject" /><col class="term-competency" /><col class="term-mark" /><col class="term-average" /><col class="term-coef" /><col class="term-product" /><col class="term-grade" /><col class="term-range" /><col class="term-remark" /></colgroup>
          <thead><tr><th>{{ isFrench() ? "Matières et nom de l'enseignant" : 'Subject and Teacher' }}</th><th>{{ isFrench() ? 'Compétences évaluées' : 'COMPETENCIES EVALUATED' }}</th><th>{{ isFrench() ? 'N/20' : 'MK/20' }}</th><th>{{ isFrench() ? 'M/20' : 'AV/20' }}</th><th>{{ isFrench() ? 'Coéf.' : 'Coef.' }}</th><th>Prod</th><th>{{ isFrench() ? 'Côte' : 'Grade' }}</th><th>[Min - Max]</th><th>{{ isFrench() ? 'Appréciations et Visa' : 'Remarks and signature' }}</th></tr></thead>
          <tbody>
            @for (line of view().lines; track line.subjectCode) {
              <tr><td class="subject-cell"><strong>{{ line.subjectLabel }}</strong><span>{{ line.teacherName || '-' }}</span></td><td class="stack-cell">@for (item of competencyRows(line); track $index) { <span>{{ item.label }}</span> }</td><td class="stack-cell centered-stack">@for (item of competencyRows(line); track $index) { <span>{{ mark(item.mark) }}</span> }</td><td class="strong-cell">{{ mark(line.mark) }}</td><td>{{ line.coef }}</td><td>{{ mark(line.weighted) }}</td><td class="strong-cell">{{ grade(line.mark) }}</td><td>{{ range() }}</td><td class="remark-cell">{{ remark(line) }}</td></tr>
            }
            <tr class="total-row"><th colspan="2">TOTAL</th><td>{{ totalCoefficient() }}</td><td>{{ mark(totalWeighted()) }}</td><th colspan="5">{{ isFrench() ? 'Moyenne' : 'Average' }} : {{ mark(view().average) }}</th></tr>
          </tbody>
        </table>
      }

      <table class="summary-table"><thead><tr><th>{{ isFrench() ? 'Discipline' : 'Discipline' }}</th><th>{{ isFrench() ? "Travail de l'élève" : 'Student performance' }}</th><th>{{ isFrench() ? 'Profil de la classe' : 'Class Profile' }}</th></tr></thead><tbody><tr>
        <td><div class="summary-grid"><span>{{ isFrench() ? 'Absences (heurs) non J.' : 'Absences (hours) non J.' }}</span><b>{{ absenceHours() }}</b><span>{{ isFrench() ? 'Absences (heurs) J.' : 'Absences (hours) J.' }}</span><b>{{ justifiedHours() }}</b><span>{{ isFrench() ? 'Retards (heurs)' : 'Lateness (hours)' }}</span><b>{{ lateHours() }}</b><span>{{ isFrench() ? 'Consignes (heurs)' : 'Punishment (hours)' }}</span><b>0</b><span>{{ isFrench() ? 'Avertissement de Conduite' : 'Conduct Warning' }}</span><b>{{ flag(view().conduct?.conductWarning) }}</b><span>{{ isFrench() ? 'Blâme de Conduite' : 'Reprimand' }}</span><b>{{ flag(view().conduct?.conductBlame) }}</b><span>{{ isFrench() ? 'Exclusion (jours)' : 'Suspension (days)' }}</span><b>{{ view().conduct?.exclusionDays ?? 0 }}</b><span>{{ isFrench() ? 'Exclusion définitive' : 'Dismissed' }}</span><b>-</b></div></td>
        <td><div class="summary-grid"><span>{{ isFrench() ? 'TOTAL GÉNÉRAL' : 'TOTAL SCORE' }}</span><b>{{ mark(totalWeighted()) }}</b><span>COEF</span><b>{{ totalCoefficient() }}</b><span>{{ isFrench() ? 'MOYENNE' : annual() ? 'Annual Average' : 'Average' }}</span><b>{{ mark(view().average) }}</b><span>{{ isFrench() ? 'CÔTE' : 'Grade' }}</span><b>{{ grade(view().average) }}</b><span>{{ isFrench() ? 'DÉCISION DU CONSEIL' : 'Class Council Decision' }}</span><b>{{ councilDecision() }}</b></div></td>
        <td><div class="summary-grid"><span>{{ isFrench() ? 'Moyenne Générale' : 'Class Average' }}</span><b>{{ mark(view().classAverage) }}</b><span>{{ isFrench() ? 'Rang' : 'Rank' }}</span><b>{{ view().rank || '-' }}/{{ view().classSize }}</b><span>{{ isFrench() ? 'Nombre de moyennes' : 'Number passed' }}</span><b>{{ view().successCount ?? '-' }}</b><span>{{ isFrench() ? 'Taux de réussite' : 'Success rate (%)' }}</span><b>{{ percent(view().successRate) }}</b></div></td>
      </tr></tbody></table>

      <section class="signature-grid"><div class="work-remark"><strong>{{ isFrench() ? "Appréciation du travail de l'élève (points forts et points à améliorer)" : 'Remarks on student performance' }}</strong><span>{{ view().generalAppreciation || '' }}</span></div><div>{{ isFrench() ? 'Visa du parent / Tuteur' : "Parent's/Guardian's signature" }}</div><div>{{ isFrench() ? 'Nom et visa du professeur principal' : "Class master's signature" }}</div><div>{{ isFrench() ? 'Fait à' : 'At' }} {{ city() || 'MAROUA' }}, {{ isFrench() ? 'le' : 'on' }}<br /><strong>{{ isFrench() ? "Le Chef d'établissement" : 'THE PRINCIPAL' }}</strong></div></section>
      <footer class="document-footer"><div class="qr-placeholder" aria-label="QR verification code"><span>BBC</span><span>VERIFY</span></div><p>{{ footerLegend() }}</p></footer>
    </article>
  `,
  styles: [`
    :host{display:block;color:#050505}.report-page{box-sizing:border-box;width:100%;min-width:820px;max-width:920px;margin:0 auto;background:#fff;padding:24px 28px 20px;font-family:"Arial Narrow",Arial,sans-serif;font-size:10px;line-height:1.08;color:#050505}.institutional-header{display:grid;grid-template-columns:1fr 150px 1fr;gap:12px;align-items:start;text-align:center}.authority-block{display:flex;flex-direction:column;align-items:center;line-height:1.13}.authority-block strong{font-size:10px}.authority-block em{font-size:9px;font-weight:700}.authority-block span{font-size:8px}.school-mark{display:flex;flex-direction:column;align-items:center;gap:2px;font-size:8px}.school-mark img{width:64px;height:64px;object-fit:contain}.school-mark strong{font-size:8px;text-transform:uppercase}.document-title{text-align:center;margin:7px 0 5px;line-height:.95}.document-title strong{display:block;font-size:17px;text-transform:uppercase}.document-title em{display:block;font-size:16px;font-weight:400}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border:1px solid #111;padding:2px 3px;vertical-align:middle}.identity-table{font-size:10px}.identity-table th{width:auto;text-align:left;font-weight:400;white-space:nowrap}.identity-table .identity-value{font-size:11px;font-weight:700}.identity-table .center{text-align:center}.marks-table{font-size:8px;line-height:1.08}.marks-table thead th{background:#c9c9c9;text-align:center;font-weight:800;padding:4px 2px}.marks-table td{text-align:center;padding:2px}.marks-table .subject-cell{text-align:left}.subject-cell strong,.subject-cell span{display:block}.subject-cell strong{font-weight:500}.subject-cell span{font-size:7px;text-transform:uppercase}.remark-cell{text-align:left!important;font-size:7px}.strong-cell{font-weight:800}.stack-cell{padding:0!important;text-align:left!important}.stack-cell span{display:flex;align-items:center;min-height:16px;padding:1px 3px}.stack-cell span+span{border-top:1px solid #777}.centered-stack span{justify-content:center}.total-row th,.total-row td{background:#bfbfbf;font-size:10px;font-weight:800;text-align:center;padding:4px}.annual-subject{width:27%}.annual-term{width:6.6%}.annual-average{width:8%}.annual-coef{width:5%}.annual-product{width:7%}.annual-grade{width:6%}.annual-range{width:9%}.annual-remark{width:18.2%}.term-subject{width:14%}.term-competency{width:46%}.term-mark{width:4.7%}.term-average{width:5.4%}.term-coef{width:4%}.term-product{width:5%}.term-grade{width:4.6%}.term-range{width:7%}.term-remark{width:9.3%}.term-recap{display:grid;grid-template-columns:1.35fr repeat(3,1fr);border:1px solid #111;border-top:0;background:#d4d4d4;text-align:center;font-size:8px;font-weight:700}.term-recap>*{padding:3px;border-right:1px solid #111}.term-recap>*:last-child{border-right:0}.summary-table{font-size:8px}.summary-table>thead th{background:#e7e7e7;font-size:10px;text-align:center}.summary-table>tbody>tr>td{width:33.333%;padding:0;vertical-align:top}.summary-grid{display:grid;grid-template-columns:1fr 45px}.summary-grid span,.summary-grid b{min-height:17px;padding:2px 3px;border-bottom:1px solid #777}.summary-grid span:nth-last-child(-n+2),.summary-grid b:nth-last-child(-n+2){border-bottom:0}.summary-grid b{border-left:1px solid #777;text-align:center}.signature-grid{display:grid;grid-template-columns:1.8fr .75fr 1fr 1.1fr;min-height:92px}.signature-grid>div{border:1px solid #111;border-top:0;padding:4px;text-align:center}.signature-grid .work-remark{text-align:left}.work-remark strong,.work-remark span{display:block}.document-footer{display:flex;align-items:flex-end;gap:14px;min-height:70px;padding-top:7px}.qr-placeholder{width:54px;height:54px;border:4px dotted #111;display:flex;flex-direction:column;justify-content:center;align-items:center;font-size:7px;font-weight:900}.document-footer p{flex:1;margin:0;text-align:center;color:#555;font-size:7px}@media print{.report-page{min-width:0;max-width:none;width:210mm;min-height:297mm;padding:9mm 10mm;font-size:8pt}.institutional-header{break-inside:avoid}.marks-table,.summary-table,.signature-grid{break-inside:avoid}.qr-placeholder{border-style:solid}}
  `],
})
export class SecondaryBulletinComponent {
  private readonly auth = inject(AuthService);
  private readonly school = inject(SchoolService);
  readonly view = input.required<BulletinView>();
  constructor() { this.school.ensureLoaded(); }

  protected isFrench = computed(() => (this.view().subsystem ?? '').toUpperCase() !== 'EN');
  protected annual = computed(() => this.view().product === 'ANNUAL' || this.view().reportingPeriodType === 'ANNUAL_RESULT');
  protected schoolName = computed(() => this.school.profile()?.name || this.auth.user()?.schoolName || 'Bayo Bilingual Complex');
  protected schoolCode = computed(() => this.school.profile()?.code || this.auth.user()?.schoolCode || '-');
  protected city = computed(() => this.school.profile()?.city || '');
  protected totalCoefficient = computed(() => this.view().lines.reduce((sum, line) => sum + line.coef, 0));
  protected totalWeighted = computed(() => this.view().lines.reduce((sum, line) => sum + (line.weighted ?? 0), 0));
  protected annualPeriods = computed(() => { const codes = Array.from(new Set(this.view().lines.flatMap((line) => (line.periodMarks ?? []).map((mark) => mark.periodCode)))); const terms = codes.filter((code) => /^T[1-3](?:_RESULT)?$/i.test(code) || /TRIM/i.test(code)); return (terms.length ? terms : codes).sort((a, b) => this.periodRank(a) - this.periodRank(b)).slice(0, 3); });
  protected missingAnnualColumns = computed(() => Array.from({ length: Math.max(0, 3 - this.annualPeriods().length) }, (_, index) => index));
  protected leftAuthority = computed(() => this.authority(this.isFrench()));
  protected rightAuthority = computed(() => this.authority(!this.isFrench()));

  protected reportTitle(): string { const period = this.periodNumber(); if (this.annual()) return this.isFrench() ? 'BULLETIN SCOLAIRE ANNUEL' : 'ANNUAL REPORT SHEET'; if (this.view().reportingPeriodType === 'TERM_RESULT' || this.view().product === 'TERM') return this.isFrench() ? `BULLETIN SCOLAIRE DU ${period === 1 ? '1er' : period + 'e'} TRIMESTRE` : `${this.ordinal(period).toUpperCase()} TERM PROGRESS RECORD`; return this.isFrench() ? `BULLETIN DE NOTES DE LA SÉQUENCE ${period}` : `SEQUENCE ${period} PROGRESS RECORD`; }
  protected addressLine(french: boolean): string { const p = this.school.profile(); const address = [p?.address, p?.city].filter(Boolean).join(' - '); const phone = p?.phone ? `${french ? 'Tél' : 'Tel'} : ${p.phone}` : ''; return [address, phone].filter(Boolean).join(' - ') || (p?.city || 'MAROUA'); }
  protected birth(): string { const birthDate = this.view().birthDate; const date = birthDate ? this.formatDate(birthDate) : null; return [date, this.view().birthPlace].filter(Boolean).join(` ${this.isFrench() ? 'à' : 'at'} `) || '-'; }
  protected yesNo(value: boolean | undefined): string { return value ? (this.isFrench() ? 'Oui' : 'Yes') : (this.isFrench() ? 'Non' : 'No'); }
  protected flag(value: boolean | undefined): string { return value ? 'X' : '-'; }
  protected mark(value: number | null | undefined): string { return value == null || !Number.isFinite(value) ? '-' : Number(value.toFixed(2)).toString(); }
  protected percent(value: number | null | undefined): string { return value == null ? '-' : `${this.mark(value)}%`; }
  protected range(): string { return '[- - -]'; }
  protected grade(value: number | null | undefined): string { if (value == null) return '-'; if (value >= 18) return 'A+'; if (value >= 16) return 'A'; if (value >= 14) return 'B+'; if (value >= 12) return 'B'; if (value >= 10) return 'C+'; if (value >= 8) return 'C'; return 'D'; }
  protected remark(line: BulletinLine): string { if (line.teacherRemark?.trim()) return line.teacherRemark; const value = line.mark; if (value == null) return ''; if (this.isFrench()) return value >= 16 ? 'CTBA' : value >= 14 ? 'CBA' : value >= 12 ? 'CA' : value >= 10 ? 'CMA' : 'CNA'; return value >= 16 ? 'CVWA' : value >= 14 ? 'CWA' : value >= 12 ? 'CAA' : value >= 10 ? 'CAA' : 'CNA'; }
  protected competencyRows(line: BulletinLine): Array<{ label: string; mark: number | null }> { if (line.assessments?.length) return line.assessments.map((item) => ({ label: item.label || item.code, mark: item.mark })); if (line.periodMarks?.length) return line.periodMarks.map((item) => ({ label: item.periodCode, mark: item.mark })); return [{ label: '-', mark: null }]; }
  protected periodMark(line: BulletinLine, code: string): number | null { return line.periodMarks?.find((period) => period.periodCode === code)?.mark ?? null; }
  protected periodAverage(code: string): number | null { const marks = this.view().lines.map((line) => this.periodMark(line, code)).filter((value): value is number => value != null); if (!marks.length) return null; const weighted = this.view().lines.reduce((sum, line) => sum + ((this.periodMark(line, code) ?? 0) * line.coef), 0); return weighted / Math.max(1, this.totalCoefficient()); }
  protected termName(number: number): string { return this.isFrench() ? `${number}${number === 1 ? 'er' : 'e'} Trimestre` : `${this.ordinal(number)} Term`; }
  protected absenceHours(): string { return this.mark(this.view().attendance?.unjustifiedAbsenceHours ?? 0); }
  protected justifiedHours(): string { return this.mark(this.view().attendance?.justifiedAbsenceHours ?? 0); }
  protected lateHours(): string { return this.mark((this.view().attendance?.lateMinutes ?? 0) / 60); }
  protected councilDecision(): string { return this.view().conduct?.decisionCode?.replaceAll('_', ' ') || '-'; }
  protected footerLegend(): string { return this.isFrench() ? 'CTBA : Compétences très bien acquises, CBA : Compétences bien acquises, CA : Compétences acquises, CMA : Compétences moyennement acquises, CNA : Compétences non acquises.' : 'CVWA: Competencies very well acquired, CWA: Competencies well acquired, CAA: Competencies acquired, CNA: Competencies not acquired.'; }
  private authority(french: boolean) { return french ? { french: true, country: 'RÉPUBLIQUE DU CAMEROUN', motto: 'Paix-Travail-Patrie', ministry: 'MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES', regional: "DÉLÉGATION RÉGIONALE DE L'EXTRÊME-NORD", divisional: 'DÉLÉGATION DÉPARTEMENTALE DU DIAMARÉ' } : { french: false, country: 'REPUBLIC OF CAMEROON', motto: 'Peace-Work-Fatherland', ministry: 'MINISTRY OF SECONDARY EDUCATION', regional: 'REGIONAL DELEGATION OF FAR-NORTH', divisional: 'DIVISIONAL DELEGATION OF DIAMARE' }; }
  private periodNumber(): number { const source = `${this.view().reportingPeriodCode ?? ''} ${this.view().reportingPeriodLabel ?? ''}`; const match = source.match(/[1-6]/); return match ? Number(match[0]) : this.view().sequence || 1; }
  private periodRank(code: string): number { const match = code.match(/[1-6]/); return match ? Number(match[0]) : 99; }
  private ordinal(value: number): string { return value === 1 ? 'First' : value === 2 ? 'Second' : value === 3 ? 'Third' : `${value}th`; }
  private formatDate(value: string): string { const date = new Date(`${value}T00:00:00`); if (Number.isNaN(date.getTime())) return value; return new Intl.DateTimeFormat(this.isFrench() ? 'fr-FR' : 'en-GB', { day: '2-digit', month: 'long', year: 'numeric' }).format(date); }
}
