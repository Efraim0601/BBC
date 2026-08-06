import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClassView, SetupApi } from '../../core/setup.api';
import { I18nService } from '../../core/i18n.service';
import { CardComponent, IconComponent, PageHeaderComponent } from '../../core/ui';
import { GuardianInput, GuardianSearchView, StudentApi, StudentUpsert } from './students.api';

@Component({
  selector: 'bbc-student-registration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CardComponent, IconComponent, PageHeaderComponent],
  template: `
    <div class="fade-in max-w-5xl mx-auto space-y-5">
      <bbc-page-header [title]="fr()?'Inscrire un nouvel élève':'Register a new student'"
        [subtitle]="fr()?'Élève, classe et famille dans une seule opération':'Student, placement and family in one operation'">
        <a right routerLink="/students" class="btn-secondary"><bbc-icon name="chevronLeft" [s]="14"/> {{fr()?'Annuler':'Cancel'}}</a>
      </bbc-page-header>

      <bbc-card>
        <div class="flex gap-2 mb-7">
          @for(label of labels();track $index){
            <div class="flex-1"><div class="h-1.5 rounded-full" [class]="$index<=step()?'bg-brand-600':'bg-slate-200'"></div><div class="text-[11px] mt-1" [class]="$index===step()?'font-bold text-brand-700':'text-mute'">{{$index+1}}. {{label}}</div></div>
          }
        </div>

        @if(attemptedCurrent() && !validStep()){
          <div role="alert" class="mb-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-700">
            {{fr()?'Veuillez corriger les champs indiqués en rouge avant de continuer.':'Correct the fields marked in red before continuing.'}}
          </div>
        }

        @if(step()===0){
          <section class="space-y-4">
            <div><h2 class="text-lg font-bold">{{fr()?'Identité de l’élève':'Student identity'}}</h2><p class="text-sm text-mute mt-1">{{fr()?'Les champs marqués comme obligatoires doivent être renseignés.':'Fields marked as required must be completed.'}}</p></div>
            <div class="grid md:grid-cols-2 gap-4">
              <label><span class="label">{{fr()?'Nom':'Last name'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><input class="input w-full" [(ngModel)]="student.lastName" [class.input-error]="attemptedCurrent()&&!student.lastName.trim()" [attr.aria-invalid]="attemptedCurrent()&&!student.lastName.trim()"/>@if(attemptedCurrent()&&!student.lastName.trim()){<span class="field-error">{{fr()?'Le nom de l’élève est obligatoire.':'Student last name is required.'}}</span>}</label>
              <label><span class="label">{{fr()?'Prénom':'First name'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><input class="input w-full" [(ngModel)]="student.firstName" [class.input-error]="attemptedCurrent()&&!student.firstName.trim()" [attr.aria-invalid]="attemptedCurrent()&&!student.firstName.trim()"/>@if(attemptedCurrent()&&!student.firstName.trim()){<span class="field-error">{{fr()?'Le prénom de l’élève est obligatoire.':'Student first name is required.'}}</span>}</label>
              <label><span class="label">{{fr()?'Sexe':'Sex'}}</span><select class="input w-full" [(ngModel)]="student.sex"><option value="M">{{fr()?'Masculin':'Male'}}</option><option value="F">{{fr()?'Féminin':'Female'}}</option></select></label>
              <label><span class="label">{{fr()?'Date de naissance':'Date of birth'}}</span><input type="date" class="input w-full" [(ngModel)]="student.dob"/></label>
              <label><span class="label">NIU</span><input class="input w-full" [(ngModel)]="student.niu"/></label>
              <label><span class="label">{{fr()?'Lieu de naissance':'Birthplace'}}</span><input class="input w-full" [(ngModel)]="student.birthplace"/></label>
            </div>
          </section>
        }

        @if(step()===1){
          <section class="space-y-4">
            <h2 class="text-lg font-bold">{{fr()?'Placement scolaire':'School placement'}}</h2>
            <label class="block"><span class="label">{{fr()?'Classe actuelle':'Current class'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><select class="input w-full" [(ngModel)]="student.classId" [class.input-error]="attemptedCurrent()&&!student.classId" [attr.aria-invalid]="attemptedCurrent()&&!student.classId"><option [ngValue]="null">{{fr()?'Sélectionner une classe':'Select a class'}}</option>@for(c of classes();track c.id){<option [value]="c.id">{{c.name}} · {{c.subsystem}} · {{c.level}}</option>}</select>@if(attemptedCurrent()&&!student.classId){<span class="field-error">{{fr()?'Sélectionnez la classe actuelle de l’élève.':'Select the student’s current class.'}}</span>}</label>
            <div class="p-3 rounded-xl bg-blue-50 text-blue-800 text-sm">{{fr()?'L’inscription est automatiquement liée à la session académique courante.':'Enrollment is automatically linked to the current academic session.'}}</div>
          </section>
        }

        @if(step()===2){
          <section class="space-y-4">
            <div class="flex justify-between items-center"><div><h2 class="text-lg font-bold">{{fr()?'Parents et tuteurs':'Parents and guardians'}}</h2><p class="text-sm text-mute mt-1">{{fr()?'Recherchez un parent existant pour éviter les doublons, ou renseignez un nouvel adulte.':'Search for an existing parent to avoid duplicates, or enter a new adult.'}}</p></div><button class="btn-secondary" type="button" (click)="addGuardian()"><bbc-icon name="plus" [s]="14"/> {{fr()?'Ajouter un adulte':'Add adult'}}</button></div>
            @for(g of guardians;track $index){
              <div class="p-5 border border-slate-200 bg-slate-50/60 rounded-xl space-y-4">
                <div class="flex justify-between"><b>{{fr()?'Adulte':'Adult'}} {{$index+1}}</b>@if(guardians.length>1){<button type="button" class="text-rose-600 text-sm font-semibold" (click)="removeGuardian($index)">{{fr()?'Retirer':'Remove'}}</button>}</div>
                <div><span class="label">{{fr()?'Rechercher un parent existant':'Search existing guardian'}}</span><div class="flex gap-2"><input class="input flex-1" [(ngModel)]="queries[$index]" [placeholder]="fr()?'Nom, e-mail ou téléphone':'Name, email or phone'"/><button type="button" class="btn-secondary" (click)="search($index)">{{fr()?'Rechercher':'Search'}}</button></div></div>
                @for(r of searchResults[$index]||[];track r.id){<button type="button" class="w-full text-left p-3 bg-white border border-slate-200 hover:border-brand-300 rounded-lg" (click)="useExisting($index,r)"><b>{{r.displayName}}</b><div class="text-xs text-mute">{{r.maskedEmail||'—'}} · {{r.maskedPhone||'—'}} · {{r.linkedChildren}} {{fr()?'enfant(s)':'child(ren)'}}</div></button>}
                <div class="grid md:grid-cols-2 gap-4">
                  <label><span class="label">{{fr()?'Nom complet':'Full name'}}<span class="required-mark">*</span></span><input class="input w-full" [(ngModel)]="g.displayName" [class.input-error]="attemptedCurrent()&&!g.displayName.trim()" [attr.aria-invalid]="attemptedCurrent()&&!g.displayName.trim()"/>@if(attemptedCurrent()&&!g.displayName.trim()){<span class="field-error">{{fr()?'Le nom du parent ou tuteur est obligatoire.':'Guardian name is required.'}}</span>}</label>
                  <label><span class="label">{{fr()?'Lien avec l’élève':'Relationship to student'}}<span class="required-mark">*</span></span><input class="input w-full" [(ngModel)]="g.relationshipType" [placeholder]="fr()?'Ex. Mère, père, tuteur':'E.g. Mother, father, guardian'" [class.input-error]="attemptedCurrent()&&!g.relationshipType.trim()" [attr.aria-invalid]="attemptedCurrent()&&!g.relationshipType.trim()"/>@if(attemptedCurrent()&&!g.relationshipType.trim()){<span class="field-error">{{fr()?'Précisez le lien avec l’élève.':'Relationship to the student is required.'}}</span>}</label>
                  <label><span class="label">E-mail</span><input class="input w-full" type="email" [(ngModel)]="g.email" placeholder="parent@example.com"/></label>
                  <label><span class="label">{{fr()?'Téléphone':'Phone'}}</span><input class="input w-full" [(ngModel)]="g.phone" placeholder="+237 …"/></label>
                </div>
              </div>
            }
          </section>
        }

        @if(step()===3){
          <section class="space-y-4">
            <div><h2 class="text-lg font-bold">{{fr()?'Accès au portail parent':'Parent portal access'}}</h2><p class="text-sm text-mute mt-1">{{fr()?'Choisissez comment chaque adulte accédera au portail.':'Choose how each adult will access the portal.'}}</p></div>
            @for(g of guardians;track $index){
              <div class="p-5 border border-slate-200 bg-slate-50/60 rounded-xl space-y-4">
                <b>{{g.displayName||('Parent '+($index+1))}}</b>
                <label><span class="label">{{fr()?'Mode d’accès':'Access mode'}}<span class="required-mark">*</span></span><select class="input w-full" [(ngModel)]="g.accessMode"><option value="SEND_INVITE">{{fr()?'Envoyer une invitation par e-mail':'Send email invitation'}}</option><option value="CREATE_ACCOUNT">{{fr()?'Créer maintenant avec mot de passe':'Create now with password'}}</option><option value="NO_PORTAL">{{fr()?'Aucun accès portail':'No portal access'}}</option></select></label>
                @if(g.accessMode!=='NO_PORTAL'){
                  <label><span class="label">E-mail<span class="required-mark">*</span><span class="required-hint">{{fr()?'Requis pour l’accès':'Required for access'}}</span></span><input class="input w-full" type="email" [(ngModel)]="g.email" placeholder="parent@example.com" [class.input-error]="attemptedCurrent()&&emailInvalid(g)" [attr.aria-invalid]="attemptedCurrent()&&emailInvalid(g)"/>@if(attemptedCurrent()&&emailInvalid(g)){<span class="field-error">{{!g.email?.trim()?(fr()?'L’e-mail est obligatoire pour donner accès au portail.':'Email is required for portal access.'):(fr()?'Saisissez une adresse e-mail valide.':'Enter a valid email address.')}}</span>}</label>
                }
                @if(g.accessMode==='CREATE_ACCOUNT'){
                  <label><span class="label">{{fr()?'Mot de passe initial':'Initial password'}}<span class="required-mark">*</span></span><input type="password" class="input w-full" [(ngModel)]="g.initialPassword" [class.input-error]="attemptedCurrent()&&!passwordValid(g.initialPassword)" [attr.aria-invalid]="attemptedCurrent()&&!passwordValid(g.initialPassword)"/> <span class="field-help">{{fr()?'Au moins 8 caractères, une lettre et un chiffre.':'At least 8 characters, one letter and one number.'}}</span>@if(attemptedCurrent()&&!passwordValid(g.initialPassword)){<span class="field-error">{{fr()?'Le mot de passe ne respecte pas les règles indiquées.':'Password does not meet the stated rules.'}}</span>}</label>
                }
              </div>
            }
          </section>
        }

        @if(step()===4){
          <section class="space-y-4"><h2 class="text-lg font-bold">{{fr()?'Vérification':'Review'}}</h2><div class="grid md:grid-cols-2 gap-4"><div class="detail-field"><div class="meta">{{fr()?'Élève':'Student'}}</div><b>{{student.lastName}} {{student.firstName}}</b><div>{{className()}}</div></div><div class="detail-field"><div class="meta">{{fr()?'Famille':'Family'}}</div>@for(g of guardians;track $index){<div><b>{{g.displayName}}</b> · {{g.relationshipType}} · {{g.accessMode}}</div>}</div></div><div class="p-3 bg-emerald-50 text-emerald-800 rounded-xl text-sm">{{fr()?'La validation crée l’élève, son inscription et tous les liens familiaux dans une seule transaction. En cas d’erreur, rien n’est conservé.':'Confirmation creates the student, enrollment and all family links in one transaction. On error, nothing is retained.'}}</div></section>
        }

        @if(step()===5&&result()){<section class="text-center py-8"><div class="w-14 h-14 mx-auto rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center"><bbc-icon name="check" [s]="26"/></div><h2 class="text-xl font-bold mt-3">{{fr()?'Inscription terminée':'Registration complete'}}</h2><div class="font-mono mt-2">{{result()!.student.matricule}}</div><p class="text-sm text-mute mt-2">{{fr()?'Les invitations demandées ont été préparées. Aucun mot de passe n’est affiché ou conservé en clair.':'Requested invitations were prepared. No password is displayed or stored in plaintext.'}}</p><button class="btn-primary mt-5" (click)="router.navigate(['/students',result()!.student.id])">{{fr()?'Ouvrir la fiche élève':'Open student profile'}}</button></section>}
        @if(error()){<div class="mt-4 p-3 border border-rose-200 bg-rose-50 text-rose-700 rounded-xl">{{error()}}</div>}

        @if(step()<5){<div class="flex justify-between mt-8 pt-5 border-t"><button class="btn-secondary" [disabled]="step()===0" (click)="previous()">{{fr()?'Précédent':'Previous'}}</button>@if(step()<4){<button class="btn-primary" (click)="next()">{{fr()?'Continuer':'Continue'}}</button>}@else{<button class="btn-primary" [disabled]="saving()" (click)="submit()">{{saving()?(fr()?'Création…':'Creating…'):(fr()?'Confirmer l’inscription':'Confirm registration')}}</button>}</div>}
      </bbc-card>
    </div>`,
})
export class StudentRegistrationComponent {
  private api=inject(StudentApi); private setup=inject(SetupApi); private cdr=inject(ChangeDetectorRef); protected router=inject(Router); private i18n=inject(I18nService);
  protected fr=()=>this.i18n.lang()==='fr'; protected step=signal(0); protected attemptedStep=signal<number|null>(null); protected classes=signal<ClassView[]>([]); protected saving=signal(false); protected error=signal<string|null>(null); protected result=signal<any|null>(null);
  protected labels=()=>this.fr()?['Élève','Classe','Famille','Accès','Vérifier']:['Student','Class','Family','Access','Review'];
  protected student:StudentUpsert={firstName:'',lastName:'',niu:'',sex:'M',dob:null,birthplace:'',repeats:false,classId:null,parentName:'',parentPhone:''};
  protected guardians:GuardianInput[]=[this.blankGuardian()]; protected queries:string[]=['']; protected searchResults:GuardianSearchView[][]=[[]];

  constructor(){this.setup.listClasses().subscribe(c=>this.classes.set(c));}
  protected attemptedCurrent(){return this.attemptedStep()===this.step();}
  protected next(){this.attemptedStep.set(this.step());if(!this.validStep())return;this.attemptedStep.set(null);this.step.update(value=>value+1);}
  protected previous(){this.attemptedStep.set(null);this.error.set(null);this.step.update(value=>Math.max(0,value-1));}
  protected addGuardian(){this.guardians.push(this.blankGuardian());this.queries.push('');this.searchResults.push([]);}
  protected removeGuardian(i:number){this.guardians.splice(i,1);this.queries.splice(i,1);this.searchResults.splice(i,1);}
  protected search(i:number){const q=this.queries[i]?.trim();if(!q||q.length<3){this.error.set(this.fr()?'Saisissez au moins 3 caractères pour rechercher un parent.':'Enter at least 3 characters to search.');return;}this.error.set(null);this.api.searchGuardians(q).subscribe({next:r=>{this.searchResults[i]=r;this.cdr.markForCheck()},error:e=>this.error.set(e.error?.message)});}
  protected useExisting(i:number,r:GuardianSearchView){this.guardians[i].guardianId=r.id;this.guardians[i].displayName=r.displayName;this.guardians[i].accessMode='NO_PORTAL';this.searchResults[i]=[];this.cdr.markForCheck();}
  protected emailInvalid(g:GuardianInput){const email=g.email?.trim()||'';return !email||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);}
  protected passwordValid(password?:string|null){return !!password&&password.length>=8&&/[A-Za-z]/.test(password)&&/\d/.test(password);}
  protected validStep(){switch(this.step()){case 0:return !!this.student.firstName.trim()&&!!this.student.lastName.trim();case 1:return !!this.student.classId;case 2:return this.guardians.every(g=>!!g.displayName.trim()&&!!g.relationshipType.trim());case 3:return this.guardians.every(g=>g.accessMode==='NO_PORTAL'||!this.emailInvalid(g))&&this.guardians.every(g=>g.accessMode!=='CREATE_ACCOUNT'||this.passwordValid(g.initialPassword));default:return true;}}
  protected className(){return this.classes().find(c=>c.id===this.student.classId)?.name||'—';}
  protected submit(){this.saving.set(true);this.error.set(null);this.api.register({student:this.student,guardians:this.guardians}).subscribe({next:r=>{this.saving.set(false);this.result.set(r);this.step.set(5)},error:e=>{this.saving.set(false);this.error.set(e.error?.message||'Inscription impossible')}});}
  private blankGuardian():GuardianInput{return{displayName:'',email:'',phone:'',relationshipType:'GUARDIAN',accessMode:'SEND_INVITE',legalGuardian:true,pickupAuthorized:true,receivesAcademic:true,receivesAttendance:true,portalAccess:true};}
}
