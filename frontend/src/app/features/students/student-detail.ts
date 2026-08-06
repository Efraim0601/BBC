import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Student } from '../../core/models';
import { SetupApi, ClassView } from '../../core/setup.api';
import { I18nService } from '../../core/i18n.service';
import { PhotoApi } from '../../core/photo.api';
import { AvatarComponent, CardComponent, IconComponent, PageHeaderComponent } from '../../core/ui';
import { StudentEnrollmentPanelComponent } from './student-enrollment-panel';
import { GuardianInput, GuardianRelationshipView, GuardianSearchView, StudentApi, StudentUpsert } from './students.api';

@Component({selector:'bbc-student-detail',standalone:true,changeDetection:ChangeDetectionStrategy.OnPush,
  imports:[FormsModule,RouterLink,AvatarComponent,CardComponent,IconComponent,PageHeaderComponent,StudentEnrollmentPanelComponent],
  template:`
  <div class="fade-in max-w-6xl mx-auto space-y-5">
    <bbc-page-header [title]="student()?.name || (fr()?'Fiche élève':'Student profile')" [subtitle]="student()?.matricule || ''">
      <div right class="flex gap-2"><a routerLink="/students" class="btn-secondary"><bbc-icon name="chevronLeft" [s]="15"/> {{fr()?'Liste des élèves':'Student list'}}</a></div>
    </bbc-page-header>
    @if(error()){<div class="p-4 rounded-xl bg-rose-50 text-rose-700">{{error()}}</div>}
    @if(student();as s){
      <bbc-card>
        <div class="flex items-center gap-4">
          <bbc-avatar [name]="s.name" [hue]="s.photoHue" [size]="72" [photoUrl]="photo()"/>
          <div class="flex-1"><div class="text-2xl font-bold text-ink">{{s.name}}</div><div class="text-sm text-mute">{{s.className||'—'}} · {{s.subsystem||'—'}} · {{s.level||'—'}}</div></div>
          <button class="btn-secondary" (click)="openEdit(s)"><bbc-icon name="edit" [s]="14"/> {{fr()?'Modifier la fiche':'Edit profile'}}</button>
        </div>
        <div class="grid md:grid-cols-4 gap-4 mt-6 text-sm">
          <div><span class="meta">NIU</span><div class="font-semibold">{{s.niu||'—'}}</div></div>
          <div><span class="meta">{{fr()?'Naissance':'Birth date'}}</span><div class="font-semibold">{{s.dob||'—'}}</div></div>
          <div><span class="meta">{{fr()?'Lieu':'Place'}}</span><div class="font-semibold">{{s.birthplace||'—'}}</div></div>
          <div><span class="meta">{{fr()?'Sexe':'Sex'}}</span><div class="font-semibold">{{s.sex||'—'}}</div></div>
        </div>
      </bbc-card>
      <bbc-student-enrollment-panel [student]="s" [classes]="classes()"/>
      <bbc-card [title]="fr()?'Famille et accès parent':'Family and parent access'">
        <div class="flex justify-between items-center mb-4"><p class="text-sm text-mute">{{fr()?'Chaque adulte possède ses propres droits pour cet élève.':'Each adult has individual permissions for this student.'}}</p><button class="btn-primary" (click)="adding.set(true)"><bbc-icon name="plus" [s]="14"/> {{fr()?'Ajouter un parent':'Add guardian'}}</button></div>
        @for(g of guardians();track g.relationshipId){
          <div class="p-4 mb-3 rounded-xl border border-slate-200">
            <div class="flex items-start gap-3"><div class="flex-1"><div class="font-bold">{{g.displayName}}</div><div class="text-xs text-mute">{{g.relationshipType}} · {{g.email||g.phone||'—'}} · {{g.accountStatus}}</div></div>
              @if(g.invitationStatus==='PENDING'){<button class="btn-secondary" (click)="resend(g)">{{fr()?'Renvoyer invitation':'Resend invite'}}</button>}
              <button class="text-rose-600 text-xs" (click)="end(g)">{{fr()?'Terminer le lien':'End link'}}</button>
            </div>
            <div class="grid sm:grid-cols-3 gap-2 mt-3 text-xs">
              <label><input type="checkbox" [(ngModel)]="g.legalGuardian"/> {{fr()?'Responsable légal':'Legal guardian'}}</label>
              <label><input type="checkbox" [(ngModel)]="g.pickupAuthorized"/> {{fr()?'Autorisé à récupérer':'Pickup authorized'}}</label>
              <label><input type="checkbox" [(ngModel)]="g.financeResponsible"/> {{fr()?'Responsable financier':'Finance responsible'}}</label>
              <label><input type="checkbox" [(ngModel)]="g.receivesAcademic"/> {{fr()?'Notes/bulletins':'Academics'}}</label>
              <label><input type="checkbox" [(ngModel)]="g.receivesAttendance"/> {{fr()?'Présences':'Attendance'}}</label>
              <label><input type="checkbox" [(ngModel)]="g.receivesFinance"/> {{fr()?'Finances':'Finance'}}</label>
            </div><div class="text-right mt-2"><button class="btn-secondary" (click)="savePermissions(g)">{{fr()?'Enregistrer les droits':'Save permissions'}}</button></div>
          </div>
        } @empty {<div class="p-4 bg-amber-50 rounded-xl text-amber-800">{{fr()?'Aucun parent lié.':'No guardian linked.'}}</div>}
      </bbc-card>
    }
    @if(adding()){
      <div class="fixed inset-0 bg-slate-900/40 z-50 flex items-center justify-center p-4"><form (ngSubmit)="addGuardian()" class="bg-white rounded-2xl shadow-xl p-6 w-full max-w-xl space-y-4">
        <div class="flex justify-between"><h2 class="text-lg font-bold">{{fr()?'Ajouter ou retrouver un parent':'Add or find guardian'}}</h2><button type="button" (click)="adding.set(false)">×</button></div>
        <div class="flex gap-2"><input [(ngModel)]="searchQ" name="search" class="input flex-1" [placeholder]="fr()?'E-mail, téléphone ou nom':'Email, phone or name'"/><button type="button" class="btn-secondary" (click)="searchGuardian()">{{fr()?'Rechercher':'Search'}}</button></div>
        @for(r of results();track r.id){<button type="button" class="w-full text-left p-3 border rounded-lg" (click)="selectExisting(r)"><b>{{r.displayName}}</b><div class="text-xs text-mute">{{r.maskedEmail}} · {{r.maskedPhone}} · {{r.linkedChildren}} {{fr()?'enfant(s)':'child(ren)'}}</div></button>}
        <div class="grid sm:grid-cols-2 gap-3"><input [(ngModel)]="draft.displayName" name="name" required class="input" [placeholder]="fr()?'Nom complet':'Full name'"/><input [(ngModel)]="draft.relationshipType" name="rel" required class="input" [placeholder]="fr()?'Mère, père, tuteur…':'Mother, father, guardian…'"/><input [(ngModel)]="draft.email" name="email" type="email" class="input" placeholder="E-mail"/><input [(ngModel)]="draft.phone" name="phone" class="input" [placeholder]="fr()?'Téléphone':'Phone'"/></div>
        <select [(ngModel)]="draft.accessMode" name="mode" class="input w-full"><option value="SEND_INVITE">{{fr()?'Envoyer une invitation sécurisée':'Send secure invitation'}}</option><option value="CREATE_ACCOUNT">{{fr()?'Créer avec mot de passe initial':'Create with initial password'}}</option><option value="NO_PORTAL">{{fr()?'Contact sans accès portail':'Contact without portal access'}}</option></select>
        @if(draft.accessMode==='CREATE_ACCOUNT'){<input [(ngModel)]="draft.initialPassword" name="password" type="password" class="input w-full" [placeholder]="fr()?'8 caractères, lettre et chiffre':'8 characters, letter and number'"/>}
        @if(formError()){<div class="text-sm text-rose-600">{{formError()}}</div>}
        <div class="flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="adding.set(false)">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="saving()">{{saving()?(fr()?'Enregistrement…':'Saving…'):(fr()?'Lier le parent':'Link guardian')}}</button></div>
      </form></div>
    }
    @if(editing()){
      <div class="fixed inset-0 bg-slate-900/40 z-50 flex items-center justify-center p-4"><form (ngSubmit)="saveStudent()" class="bg-white rounded-2xl shadow-xl p-6 w-full max-w-2xl space-y-4">
        <div class="flex justify-between"><h2 class="text-lg font-bold">{{fr()?'Modifier la fiche élève':'Edit student profile'}}</h2><button type="button" (click)="editing.set(false)">×</button></div>
        <div class="grid sm:grid-cols-2 gap-3"><label><span class="label">{{fr()?'Nom':'Last name'}}</span><input class="input w-full" [(ngModel)]="editDraft.lastName" name="last" required/></label><label><span class="label">{{fr()?'Prénom':'First name'}}</span><input class="input w-full" [(ngModel)]="editDraft.firstName" name="first" required/></label><label><span class="label">NIU</span><input class="input w-full" [(ngModel)]="editDraft.niu" name="niu"/></label><label><span class="label">{{fr()?'Classe':'Class'}}</span><select class="input w-full" [(ngModel)]="editDraft.classId" name="class"><option [ngValue]="null">—</option>@for(c of classes();track c.id){<option [value]="c.id">{{c.name}}</option>}</select></label><label><span class="label">{{fr()?'Date de naissance':'Birth date'}}</span><input type="date" class="input w-full" [(ngModel)]="editDraft.dob" name="dob"/></label><label><span class="label">{{fr()?'Lieu de naissance':'Birthplace'}}</span><input class="input w-full" [(ngModel)]="editDraft.birthplace" name="birthplace"/></label></div>
        <div class="flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="editing.set(false)">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary">{{fr()?'Enregistrer':'Save'}}</button></div>
      </form></div>
    }
    @if(endTarget();as target){
      <div class="fixed inset-0 bg-slate-900/40 z-50 flex items-center justify-center p-4"><div class="bg-white rounded-2xl shadow-xl p-6 w-full max-w-md space-y-4">
        <h2 class="text-lg font-bold">{{fr()?'Terminer cette relation familiale ?':'End this family relationship?'}}</h2>
        <p class="text-sm text-mute">{{fr()?'Le parent perdra immédiatement l’accès à cet élève. Son compte restera actif s’il est encore lié à un autre enfant. Cette action sera auditée.':'The guardian immediately loses access to this student. Their account remains active if linked to another child. This action is audited.'}}</p>
        <textarea class="input w-full" rows="3" [(ngModel)]="endReason" [placeholder]="fr()?'Motif obligatoire':'Required reason'"></textarea>
        <div class="flex justify-end gap-2"><button class="btn-secondary" (click)="endTarget.set(null);endReason=''">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="!endReason.trim()" (click)="confirmEnd()">{{fr()?'Confirmer la fin du lien':'Confirm ending link'}}</button></div>
      </div></div>
    }
  </div>`})
export class StudentDetailComponent{
  private api=inject(StudentApi);private setup=inject(SetupApi);private route=inject(ActivatedRoute);private photoApi=inject(PhotoApi);protected i18n=inject(I18nService);
  protected fr=()=>this.i18n.lang()==='fr';protected student=signal<Student|null>(null);protected guardians=signal<GuardianRelationshipView[]>([]);protected classes=signal<ClassView[]>([]);protected photo=signal<string|null>(null);protected error=signal<string|null>(null);protected adding=signal(false);protected editing=signal(false);protected saving=signal(false);protected formError=signal<string|null>(null);protected results=signal<GuardianSearchView[]>([]);protected searchQ='';
  protected draft:GuardianInput=this.blank();protected editDraft:StudentUpsert={firstName:'',lastName:'',sex:'M',repeats:false,classId:null};protected endTarget=signal<GuardianRelationshipView|null>(null);protected endReason='';private id=this.route.snapshot.paramMap.get('id')!;
  constructor(){this.reload();this.setup.listClasses().subscribe(c=>this.classes.set(c));this.photoApi.load('students',this.id).subscribe(p=>this.photo.set(p));}
  private reload(){this.api.get(this.id).subscribe({next:s=>this.student.set(s),error:e=>this.error.set(e.error?.message||'Élève introuvable')});this.api.guardians(this.id).subscribe(g=>this.guardians.set(g));}
  protected searchGuardian(){if(this.searchQ.trim().length<3)return;this.api.searchGuardians(this.searchQ).subscribe({next:r=>this.results.set(r),error:e=>this.formError.set(e.error?.message)});}
  protected selectExisting(r:GuardianSearchView){this.draft.guardianId=r.id;this.draft.displayName=r.displayName;this.draft.accessMode='NO_PORTAL';this.results.set([]);}
  protected addGuardian(){this.saving.set(true);this.formError.set(null);this.api.addGuardian(this.id,this.draft).subscribe({next:()=>{this.saving.set(false);this.adding.set(false);this.draft=this.blank();this.reload();},error:e=>{this.saving.set(false);this.formError.set(e.error?.message||'Erreur')}});}
  protected savePermissions(g:GuardianRelationshipView){this.api.updateRelationship(g.relationshipId,{...g,effectiveFrom:g.effectiveFrom}).subscribe({next:()=>this.reload(),error:e=>this.error.set(e.error?.message)});}
  protected end(g:GuardianRelationshipView){this.endTarget.set(g);this.endReason='';}
  protected confirmEnd(){const g=this.endTarget();if(!g||!this.endReason.trim())return;this.api.endRelationship(g.relationshipId,this.endReason.trim()).subscribe({next:()=>{this.endTarget.set(null);this.endReason='';this.reload()},error:e=>this.error.set(e.error?.message)});}
  protected resend(g:GuardianRelationshipView){this.api.resendInvite(g.guardianId).subscribe({next:()=>this.reload(),error:e=>this.error.set(e.error?.message)});}
  protected openEdit(s:Student){this.editDraft={firstName:s.firstName,lastName:s.lastName,niu:s.niu,sex:s.sex||'M',dob:s.dob,birthplace:s.birthplace,repeats:s.repeats,classId:s.classId,parentName:s.parentName,parentPhone:s.parentPhone,fatherName:s.fatherName,fatherPhone:s.fatherPhone,fatherEmail:s.fatherEmail,motherName:s.motherName,motherPhone:s.motherPhone,motherEmail:s.motherEmail,guardianName:s.guardianName,guardianPhone:s.guardianPhone,guardianEmail:s.guardianEmail,guardianRelation:s.guardianRelation};this.editing.set(true);}
  protected saveStudent(){this.api.update(this.id,this.editDraft).subscribe({next:()=>{this.editing.set(false);this.reload()},error:e=>this.error.set(e.error?.message)});}
  private blank():GuardianInput{return{displayName:'',email:'',phone:'',relationshipType:'GUARDIAN',accessMode:'SEND_INVITE',legalGuardian:true,pickupAuthorized:true,receivesAcademic:true,receivesAttendance:true,portalAccess:true};}
}
