import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { BulletinLine, BulletinView } from './academic.api';

/** Official secondary report-card preview shared by FR and EN parcours. */
@Component({
  selector: 'bbc-secondary-bulletin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="sheet bg-white text-black mx-auto" aria-label="Official secondary report card">
      <header class="official-header">
        <div class="authority">
          <b>{{ fr() ? 'RÉPUBLIQUE DU CAMEROUN' : 'REPUBLIC OF CAMEROON' }}</b>
          <i>{{ fr() ? 'Paix-Travail-Patrie' : 'Peace-Work-Fatherland' }}</i><span>***********</span>
          <b>{{ fr() ? 'MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES' : 'MINISTRY OF SECONDARY EDUCATION' }}</b><span>***********</span>
          <b>{{ fr() ? 'DÉLÉGATION RÉGIONALE' : 'REGIONAL DELEGATION' }}</b><span>***********</span>
          <b>{{ fr() ? 'DÉLÉGATION DÉPARTEMENTALE' : 'DIVISIONAL DELEGATION' }}</b>
        </div>
        <div class="school"><div class="crest">BBC</div><b>{{ schoolName() }}</b><small>{{ fr() ? 'Établissement secondaire bilingue' : 'Bilingual secondary school' }}</small></div>
        <div class="authority">
          <b>{{ fr() ? 'REPUBLIC OF CAMEROON' : 'RÉPUBLIQUE DU CAMEROUN' }}</b>
          <i>{{ fr() ? 'Peace-Work-Fatherland' : 'Paix-Travail-Patrie' }}</i><span>***********</span>
          <b>{{ fr() ? 'MINISTRY OF SECONDARY EDUCATION' : 'MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES' }}</b><span>***********</span>
          <b>{{ fr() ? 'REGIONAL DELEGATION' : 'DÉLÉGATION RÉGIONALE' }}</b><span>***********</span>
          <b>{{ fr() ? 'DIVISIONAL DELEGATION' : 'DÉLÉGATION DÉPARTEMENTALE' }}</b>
        </div>
      </header>

      <div class="title"><b>{{ title() }}</b><em>{{ fr() ? 'Année scolaire' : 'School year' }} {{ view().schoolYear || '-' }}</em></div>
      <table class="identity">
        <tr><td colspan="3"><b>{{ fr() ? "Nom et Prénoms de l'élève" : 'Name of Student' }} :</b> {{ view().studentName }}</td><td><b>{{ fr() ? 'Classe' : 'Class' }} :</b> {{ view().className }}</td></tr>
        <tr><td colspan="2"><b>{{ fr() ? 'Date et lieu de Naissance' : 'Date and place of birth' }} :</b> {{ birth() }}</td><td><b>{{ fr() ? 'Genre' : 'Gender' }} :</b> {{ view().sex || '-' }}</td><td><b>{{ fr() ? 'Effectif' : 'Class enrolment' }} :</b> {{ view().classSize }}</td></tr>
        <tr><td colspan="2"><b>{{ fr() ? 'Identifiant unique' : 'Unique identification number' }} :</b> {{ view().matricule || '-' }}</td><td><b>{{ fr() ? 'Redoublant(e)' : 'Repeater' }} :</b> {{ view().repeater ? (fr() ? 'Oui' : 'Yes') : (fr() ? 'Non' : 'No') }}</td><td><b>{{ fr() ? 'Professeur principal' : 'Class master' }} :</b> {{ view().classMasterName || '-' }}</td></tr>
        <tr><td colspan="4"><b>{{ fr() ? 'Noms et contacts des Parents / Tuteurs' : "Parent's / Guardian's name and contact" }} :</b> {{ view().parentContact || '-' }}</td></tr>
      </table>

      @if (annual()) {
        <table class="results annual"><thead><tr>
          <th>{{ fr() ? "Matières et nom de l'enseignant" : "Subject and Teacher's Names" }}</th>
          @for (code of annualColumns(); track code) { <th>{{ periodLabel(code) }}</th> }
          <th>{{ fr() ? 'Moy' : 'AV' }}</th><th>Coef.</th><th>Prod</th><th>{{ fr() ? 'Cote' : 'Grade' }}</th><th>[Min - Max]</th><th>{{ fr() ? "Appréciations et Visa de l'enseignant" : "Remarks and Teacher's signature" }}</th>
        </tr></thead><tbody>@for (line of view().lines; track line.subjectCode; let i = $index) {
          <tr><td><b>{{ line.subjectLabel }}</b><small>{{ line.teacherName || '—' }}</small></td>
            @for (code of annualColumns(); track code) { <td>{{ mark(periodMark(line, code)) }}</td> }
            <td><b>{{ mark(line.mark) }}</b></td><td>{{ line.coef }}</td><td><b>{{ mark(line.weighted) }}</b></td><td><b>{{ grade(line.mark) }}</b></td><td>—</td><td>{{ line.teacherRemark || appreciation(line.mark) }}</td></tr>
            @if (groupAtEnd(i); as group) { <tr class="group-total"><td colspan="4">{{ fr() ? 'TOTAL' : 'GROUP TOTAL' }} {{ group.label || group.code }}</td><td>{{ mark(group.average) }}</td><td>{{ group.coefficient }}</td><td>{{ mark(group.total) }}</td><td>{{ grade(group.average) }}</td><td>-</td><td></td></tr> }
        }</tbody></table>
      } @else {
        <table class="results term"><thead><tr><th>{{ fr() ? "Matières et nom de l'enseignant" : 'Subject and teacher' }}</th><th>{{ fr() ? 'Compétences évaluées' : 'Competencies evaluated' }}</th><th>N/20</th><th>M/20</th><th>Coef.</th><th>Prod</th><th>{{ fr() ? 'Cote' : 'Grade' }}</th><th>[Min - Max]</th><th>{{ fr() ? 'Appréciations et Visa' : 'Remarks and signature' }}</th></tr></thead>
          <tbody>@for (line of view().lines; track line.subjectCode; let i = $index) {<tr><td><b>{{ line.subjectLabel }}</b><small>{{ line.teacherName || '—' }}</small></td>
            <td><div class="competencies">@for (item of competencyRows(line); track item.label) { <span>{{ item.label }}</span> }</div></td>
            <td><div class="competencies centered">@for (item of competencyRows(line); track item.label) { <span>{{ mark(item.mark) }}</span> }</div></td>
            <td><b>{{ mark(line.mark) }}</b></td><td>{{ line.coef }}</td><td>{{ mark(line.weighted) }}</td><td><b>{{ grade(line.mark) }}</b></td><td>—</td><td>{{ line.teacherRemark || appreciation(line.mark) }}</td></tr>
            @if (groupAtEnd(i); as group) { <tr class="group-total"><td colspan="3">{{ fr() ? 'TOTAL' : 'GROUP TOTAL' }} {{ group.label || group.code }}</td><td>{{ mark(group.average) }}</td><td>{{ group.coefficient }}</td><td>{{ mark(group.total) }}</td><td>{{ grade(group.average) }}</td><td>-</td><td></td></tr> }
          }</tbody>
        </table>
      }

      <table class="total"><tr><th>TOTAL</th><td>{{ totalCoefficient() }}</td><td>{{ mark(totalWeighted()) }}</td><th>{{ fr() ? 'Moyenne' : 'Student average' }} : {{ mark(view().average) }}</th></tr></table>
      @if (annual()) { <div class="term-recap"><b>{{ fr() ? 'RAPPEL DES MOYENNES TRIMESTRIELLES' : 'TERM AVERAGES' }}</b>@for (code of annualColumns(); track code) { <span>{{ periodLabel(code) }} : {{ mark(classPeriodAverage(code)) }}</span> }</div> }
      <table class="summary"><tr><th>{{ fr() ? 'Discipline' : 'Discipline' }}</th><th>{{ fr() ? "Travail de l'élève" : 'Student performance' }}</th><th>{{ fr() ? 'Profil de la classe' : 'Class profile' }}</th></tr>
        <tr><td>{{ fr() ? 'Absences non justifiées' : 'Unjustified absences' }} : {{ view().attendance?.unjustifiedAbsenceHours ?? 0 }} h</td><td>{{ fr() ? 'Total général' : 'Total score' }} : {{ mark(totalWeighted()) }}</td><td>{{ fr() ? 'Moyenne générale' : 'Class average' }} : {{ mark(view().classAverage) }}</td></tr>
        <tr><td>{{ fr() ? 'Retards' : 'Lateness' }} : {{ view().attendance?.lateMinutes ?? 0 }} min</td><td>{{ fr() ? 'Moyenne' : 'Average' }} : {{ mark(view().average) }}</td><td>{{ fr() ? 'Rang' : 'Rank' }} : {{ view().rank || '—' }}/{{ view().classSize }}</td></tr>
        <tr><td>{{ fr() ? 'Exclusion' : 'Suspension' }} : {{ view().conduct?.exclusionDays ?? 0 }} j</td><td>{{ fr() ? 'Cote' : 'Grade' }} : {{ grade(view().average) }}</td><td>{{ fr() ? 'Taux de réussite' : 'Success rate' }} : —</td></tr>
      </table>
      <div class="signatures"><div><b>{{ fr() ? "Appréciation du travail de l'élève" : 'Remarks on student performance' }}</b></div><div>{{ fr() ? 'Visa du parent / Tuteur' : "Parent's / Guardian's signature" }}</div><div>{{ fr() ? 'Nom et visa du professeur principal' : "Class master's signature" }}</div><div>{{ fr() ? 'Fait à MAROUA, le' : 'At MAROUA, on' }}<br />{{ fr() ? "Le Chef d'établissement" : 'THE PRINCIPAL' }}</div></div>
    </article>
  `,
  styles: [`
    .sheet{max-width:900px;padding:14px;font-family:Arial,sans-serif;font-size:10px}.official-header{display:grid;grid-template-columns:1fr 150px 1fr;gap:8px;text-align:center;align-items:start}.authority{display:flex;flex-direction:column;line-height:1.25}.authority b{font-size:9px}.authority i{font-size:8px}.authority span{font-size:7px}.school{display:flex;flex-direction:column;align-items:center;gap:3px}.school b{font-size:11px;text-transform:uppercase}.school small{font-size:7px}.crest{width:48px;height:48px;border:2px solid #164e99;border-radius:50%;display:grid;place-items:center;color:#164e99;font-weight:900}.title{text-align:center;margin:8px 0 5px;font-size:16px;line-height:1.05;text-transform:uppercase}.title em{display:block;font-size:14px;font-weight:400;text-transform:none}table{width:100%;border-collapse:collapse}th,td{border:1px solid #111;padding:2px 3px;vertical-align:middle}.identity td{height:19px}.results{font-size:8px}.results thead{background:#d0d0d0}.results th{text-align:center}.results td{text-align:center}.results td:first-child,.results td:nth-child(2),.results td:last-child{text-align:left}.results td:first-child{width:15%}.results td:first-child small{display:block;font-size:7px}.results .group-total{background:#e5e7eb;font-weight:700}.annual td:last-child{width:15%}.term td:nth-child(2){width:31%}.competencies{display:flex;flex-direction:column}.competencies span+span{border-top:1px solid #999;margin-top:2px;padding-top:2px}.centered{text-align:center}.total{background:#d0d0d0;font-size:10px}.total th:first-child{width:55%}.term-recap{display:flex;border:1px solid #111;border-top:0;justify-content:space-around;padding:3px;background:#ddd}.summary th{background:#eee}.summary td{width:33.333%}.signatures{display:grid;grid-template-columns:1.7fr .8fr 1.1fr 1.2fr;min-height:82px}.signatures>div{border:1px solid #111;border-top:0;padding:5px;text-align:center}.signatures>div:first-child{text-align:left}@media print{.sheet{max-width:100%;padding:0}.official-header{break-inside:avoid}}
  `],
})
export class SecondaryBulletinComponent {
  private i18n=inject(I18nService); private auth=inject(AuthService);
  readonly view=input.required<BulletinView>();
  protected fr=()=>this.i18n.lang()==='fr';
  protected schoolName=computed(()=>this.auth.user()?.schoolName||'Bayo Bilingual Complex');
  protected annual=computed(()=>this.view().product==='ANNUAL'||this.view().reportingPeriodType==='ANNUAL_RESULT');
  protected title=computed(()=>{const b=this.view();if(this.annual())return this.fr()?'BULLETIN SCOLAIRE ANNUEL':'ANNUAL REPORT SHEET';if(b.reportingPeriodType==='TERM_RESULT')return this.fr()?`BULLETIN SCOLAIRE - ${b.reportingPeriodLabel}`:`${b.reportingPeriodLabel} PROGRESS RECORD`;return this.fr()?`BULLETIN DE NOTES - ${b.reportingPeriodLabel}`:`${b.reportingPeriodLabel} PROGRESS RECORD`;});
  protected annualColumns=computed(()=>{const all=Array.from(new Set(this.view().lines.flatMap(l=>(l.periodMarks??[]).map(p=>p.periodCode))));const terms=all.filter(code=>/^T[1-3](?:_RESULT)?$/i.test(code)||/TRIM/i.test(code));return (terms.length?terms:all).sort((a,b)=>this.periodRank(a)-this.periodRank(b)).slice(0,3);});
  protected competencyRows(line:BulletinLine){if(line.assessments?.length)return line.assessments.map(a=>({label:a.label||a.code,mark:a.mark}));if(line.periodMarks?.length)return line.periodMarks.map(p=>({label:p.periodCode,mark:p.mark}));return [{label:'—',mark:null}];}
  protected periodMark(line:BulletinLine,code:string){return line.periodMarks?.find(p=>p.periodCode===code)?.mark??null;}
  protected birth(){return [this.view().birthDate,this.view().birthPlace].filter(Boolean).join(' / ')||'-';}
  protected mark(v:number|null|undefined){return v==null?'—':Number(v).toFixed(2);}
  protected grade(v:number|null|undefined){if(v==null)return '—';if(v>=18)return 'A+';if(v>=16)return 'A';if(v>=14)return 'B+';if(v>=12)return 'B';if(v>=10)return 'C+';return v>=8?'C':'D';}
  protected appreciation(v:number|null|undefined){if(v==null)return '';return v>=18?'CTBA':v>=16?'CBA':v>=14?'CA':v>=10?'CMA':'CNA';}
  protected totalCoefficient=computed(()=>this.view().lines.reduce((s,l)=>s+l.coef,0));
  protected totalWeighted=computed(()=>this.view().lines.reduce((s,l)=>s+(l.weighted??0),0));
  protected classPeriodAverage(code:string){const marks=this.view().lines.map(l=>this.periodMark(l,code)).filter((v):v is number=>v!=null);return marks.length?marks.reduce((a,b)=>a+b,0)/marks.length:null;}
  protected groupAtEnd(index:number){const line=this.view().lines[index];if(!line?.subjectGroupCode)return null;const next=this.view().lines[index+1];if(next?.subjectGroupCode===line.subjectGroupCode)return null;return this.view().groupStats?.find(group=>group.code===line.subjectGroupCode)??null;}
  protected periodLabel(code:string){const match=code.match(/[1-3]/);return match?`T${match[0]}`:code.replace(/_RESULT$/i,'');}
  private periodRank(code:string){const match=code.match(/[1-6]/);return match?Number(match[0]):99;}
}
