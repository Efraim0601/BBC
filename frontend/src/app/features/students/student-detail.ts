import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Student } from '../../core/models';
import { ClassView } from '../../core/setup.api';
import { I18nService } from '../../core/i18n.service';
import { AuthService } from '../../core/auth.service';
import { PhotoApi } from '../../core/photo.api';
import { AvatarComponent, CardComponent, IconComponent, PageHeaderComponent } from '../../core/ui';
import { StudentEnrollmentPanelComponent } from './student-enrollment-panel';
import { GuardianAccessMode, GuardianInput, GuardianRelationshipView, GuardianSearchView, StudentApi, StudentUpsert } from './students.api';
import { formatStudentDate, maskStudentDateInput, parseStudentDate } from './student-date';

@Component({
  selector: 'bbc-student-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, AvatarComponent, CardComponent, IconComponent, PageHeaderComponent, StudentEnrollmentPanelComponent],
  template: `
    <div class="fade-in max-w-6xl mx-auto space-y-5">
      <bbc-page-header [title]="student()?.name || (fr()?'Fiche élève':'Student profile')" [subtitle]="student()?.matricule || ''">
        <div right class="flex gap-2"><a routerLink="/students" class="btn-secondary"><bbc-icon name="chevronLeft" [s]="15"/> {{fr()?'Liste des élèves':'Student list'}}</a></div>
      </bbc-page-header>

      @if(error()){<div role="alert" class="p-4 rounded-xl border border-rose-200 bg-rose-50 text-rose-700">{{error()}}</div>}
      @if(notice()){<div aria-live="polite" class="p-4 rounded-xl border border-emerald-200 bg-emerald-50 text-emerald-800">{{notice()}}</div>}

      @if(student();as s){
        <bbc-card>
          <div class="flex flex-wrap items-center gap-4">
            <bbc-avatar [name]="s.name" [hue]="s.photoHue" [size]="72" [photoUrl]="photo()"/>
            <div class="flex-1 min-w-48"><div class="text-2xl font-bold text-ink">{{s.name}}</div><div class="text-sm text-mute">{{s.className||'—'}} · {{s.subsystem||'—'}} · {{s.level||'—'}}</div></div>
            @if(canEditProfile()){<button class="btn-secondary" (click)="openEdit(s)"><bbc-icon name="edit" [s]="14"/> {{fr()?'Modifier la fiche':'Edit profile'}}</button>}
          </div>
          <div class="grid sm:grid-cols-2 md:grid-cols-4 gap-3 mt-6 text-sm">
            <div class="detail-field"><span class="meta">NIU</span><div class="font-semibold mt-1">{{s.niu||'—'}}</div></div>
            <div class="detail-field"><span class="meta">{{fr()?'Naissance':'Birth date'}}</span><div class="font-semibold mt-1">{{formatStudentDate(s.dob)||'—'}}</div></div>
            <div class="detail-field"><span class="meta">{{fr()?'Lieu de naissance':'Birthplace'}}</span><div class="font-semibold mt-1">{{s.birthplace||'—'}}</div></div>
            <div class="detail-field"><span class="meta">{{fr()?'Sexe':'Sex'}}</span><div class="font-semibold mt-1">{{s.sex||'—'}}</div></div>
          </div>
        </bbc-card>

        @if(canViewEnrollment()){<bbc-student-enrollment-panel [student]="s" [classes]="classes()"/>}

        @if(canViewGuardians()){
        <bbc-card [title]="fr()?'Famille et accès parent':'Family and parent access'">
          <div class="flex flex-wrap justify-between items-center gap-3 mb-4"><p class="text-sm text-mute">{{fr()?'Chaque adulte possède ses propres droits pour cet élève.':'Each adult has individual permissions for this student.'}}</p>@if(canManageGuardians()){<button class="btn-primary" (click)="openAdd()"><bbc-icon name="plus" [s]="14"/> {{fr()?'Ajouter un parent':'Add guardian'}}</button>}</div>
          @for(g of guardians();track g.relationshipId){
            <div class="p-5 mb-3 rounded-xl border border-slate-200 bg-slate-50/60">
              <div class="flex flex-wrap items-start gap-3"><div class="flex-1 min-w-48"><div class="font-bold text-base">{{g.displayName}}</div><div class="text-xs text-mute mt-1">{{g.relationshipType}} · {{g.email||g.phone||'—'}} · {{g.accountStatus}}</div></div>
                @if(canManageGuardians() && g.invitationStatus==='PENDING'){<button class="btn-secondary" (click)="resend(g)">{{fr()?'Renvoyer invitation':'Resend invite'}}</button>}
                @if(canManageGuardians() && (!g.email || g.accountStatus==='NO_PORTAL')){<button class="btn-secondary" (click)="openPortalAccess(g)">{{fr()?'Ajouter e-mail / activer portail':'Add email / enable portal'}}</button>}
                @if(canManageGuardians()){<button class="min-h-10 px-3 text-rose-700 text-xs font-bold border border-rose-200 bg-white rounded-lg hover:bg-rose-50" (click)="end(g)">{{fr()?'Terminer le lien':'End link'}}</button>}
              </div>
              <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-2 mt-4 text-xs">
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.legalGuardian" [disabled]="!canManageGuardians()"/> {{fr()?'Responsable légal':'Legal guardian'}}</label>
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.pickupAuthorized" [disabled]="!canManageGuardians()"/> {{fr()?'Autorisé à récupérer':'Pickup authorized'}}</label>
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.financeResponsible" [disabled]="!canManageGuardians()"/> {{fr()?'Responsable financier':'Finance responsible'}}</label>
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.receivesAcademic" [disabled]="!canManageGuardians()"/> {{fr()?'Notes et bulletins':'Academics'}}</label>
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.receivesAttendance" [disabled]="!canManageGuardians()"/> {{fr()?'Présences':'Attendance'}}</label>
                <label class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-2.5"><input type="checkbox" [(ngModel)]="g.receivesFinance" [disabled]="!canManageGuardians()"/> {{fr()?'Finances':'Finance'}}</label>
              </div>
              @if(canManageGuardians()){<div class="text-right mt-3"><button class="btn-secondary" (click)="savePermissions(g)">{{fr()?'Enregistrer les droits':'Save permissions'}}</button></div>}
            </div>
          } @empty {<div class="p-4 border border-amber-200 bg-amber-50 rounded-xl text-amber-800">{{fr()?'Aucun parent lié. Utilisez « Ajouter un parent » pour créer le premier lien.':'No guardian linked. Use “Add guardian” to create the first link.'}}</div>}
        </bbc-card>
        }
      }

      @if(adding() && canManageGuardians()){
        <div class="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
          <form novalidate (ngSubmit)="addGuardian()" class="bg-white rounded-2xl shadow-xl border border-slate-200 p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto space-y-5">
            <div class="flex justify-between gap-3"><div><h2 class="text-lg font-bold">{{fr()?'Ajouter ou retrouver un parent':'Add or find guardian'}}</h2><p class="text-sm text-mute mt-1">{{fr()?'Recherchez d’abord un compte existant, ou complétez les informations ci-dessous.':'Search for an existing account first, or complete the information below.'}}</p></div><button type="button" class="text-2xl text-slate-500" (click)="closeAdd()">×</button></div>

            <label><span class="label">{{fr()?'Rechercher un parent existant':'Search existing guardian'}}</span><div class="flex gap-2"><input [(ngModel)]="searchQ" name="search" class="input flex-1" [placeholder]="fr()?'Nom, e-mail ou téléphone':'Name, email or phone'"/><button type="button" class="btn-secondary" (click)="searchGuardian()">{{fr()?'Rechercher':'Search'}}</button></div><span class="field-help">{{fr()?'Minimum 3 caractères.':'At least 3 characters.'}}</span></label>
            @for(r of results();track r.id){<button type="button" class="w-full text-left p-3 border border-slate-200 bg-slate-50 hover:border-brand-300 rounded-lg" (click)="selectExisting(r)"><b>{{r.displayName}}</b><div class="text-xs text-mute">{{r.maskedEmail||'—'}} · {{r.maskedPhone||'—'}} · {{r.linkedChildren}} {{fr()?'enfant(s)':'child(ren)'}}</div></button>}

            @if(addAttempted()&&!addValid()){<div role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-700">{{fr()?'Corrigez les champs en rouge avant d’ajouter le parent.':'Correct the fields in red before adding the guardian.'}}</div>}
            <div class="grid sm:grid-cols-2 gap-4">
              <label><span class="label">{{fr()?'Nom complet':'Full name'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><input [(ngModel)]="draft.displayName" name="name" class="input w-full" [class.input-error]="addAttempted()&&!draft.displayName.trim()" [attr.aria-invalid]="addAttempted()&&!draft.displayName.trim()"/>@if(addAttempted()&&!draft.displayName.trim()){<span class="field-error">{{fr()?'Le nom du parent est obligatoire.':'Guardian name is required.'}}</span>}</label>
              <label><span class="label">{{fr()?'Lien avec l’élève':'Relationship to student'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><input [(ngModel)]="draft.relationshipType" name="rel" class="input w-full" [placeholder]="fr()?'Ex. Mère, père, tuteur':'E.g. Mother, father, guardian'" [class.input-error]="addAttempted()&&!draft.relationshipType.trim()" [attr.aria-invalid]="addAttempted()&&!draft.relationshipType.trim()"/>@if(addAttempted()&&!draft.relationshipType.trim()){<span class="field-error">{{fr()?'Précisez le lien avec l’élève.':'Relationship is required.'}}</span>}</label>
              <label><span class="label">E-mail@if(draft.accessMode!=='NO_PORTAL'){<span class="required-mark">*</span>}</span><input [(ngModel)]="draft.email" name="email" type="email" class="input w-full" placeholder="parent@example.com" [class.input-error]="addAttempted()&&emailRequiredInvalid()" [attr.aria-invalid]="addAttempted()&&emailRequiredInvalid()"/>@if(addAttempted()&&emailRequiredInvalid()){<span class="field-error">{{!draft.email?.trim()?(fr()?'L’e-mail est obligatoire pour l’accès portail.':'Email is required for portal access.'):(fr()?'Saisissez une adresse e-mail valide.':'Enter a valid email address.')}}</span>}</label>
              <label><span class="label">{{fr()?'Téléphone':'Phone'}}</span><input [(ngModel)]="draft.phone" name="phone" class="input w-full" placeholder="+237 …"/></label>
            </div>
            <label><span class="label">{{fr()?'Mode d’accès':'Access mode'}}<span class="required-mark">*</span></span><select [(ngModel)]="draft.accessMode" name="mode" class="input w-full"><option value="SEND_INVITE">{{fr()?'Envoyer une invitation sécurisée':'Send secure invitation'}}</option><option value="CREATE_ACCOUNT">{{fr()?'Créer avec mot de passe initial':'Create with initial password'}}</option><option value="NO_PORTAL">{{fr()?'Contact sans accès portail':'Contact without portal access'}}</option></select></label>
            @if(draft.accessMode==='CREATE_ACCOUNT'){<label><span class="label">{{fr()?'Mot de passe initial':'Initial password'}}<span class="required-mark">*</span></span><input [(ngModel)]="draft.initialPassword" name="password" type="password" class="input w-full" [class.input-error]="addAttempted()&&!passwordValid(draft.initialPassword)" [attr.aria-invalid]="addAttempted()&&!passwordValid(draft.initialPassword)"/><span class="field-help">{{fr()?'Au moins 8 caractères, une lettre et un chiffre.':'At least 8 characters, one letter and one number.'}}</span>@if(addAttempted()&&!passwordValid(draft.initialPassword)){<span class="field-error">{{fr()?'Le mot de passe ne respecte pas les règles indiquées.':'Password does not meet the stated rules.'}}</span>}</label>}
            @if(formError()){<div role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{{formError()}}</div>}
            <div class="flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="closeAdd()">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="saving()">{{saving()?(fr()?'Enregistrement…':'Saving…'):(fr()?'Lier le parent':'Link guardian')}}</button></div>
          </form>
        </div>
      }

      @if(allowedPortalTarget();as target){
        <div class="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
          <form novalidate (ngSubmit)="savePortalAccess()" class="bg-white rounded-2xl shadow-xl border border-slate-200 p-6 w-full max-w-lg space-y-5">
            <div class="flex justify-between gap-3"><div><h2 class="text-lg font-bold">{{fr()?'Ajouter un e-mail au parent':'Add an email to the guardian'}}</h2><p class="text-sm text-mute mt-1">{{target.displayName}} · {{fr()?'Vous pourrez activer le portail maintenant ou plus tard.':'Enable portal access now or leave it disabled.'}}</p></div><button type="button" class="text-2xl text-slate-500" (click)="closePortalAccess()">×</button></div>
            <label><span class="label">E-mail@if(portalMode!=='NO_PORTAL'){<span class="required-mark">*</span>} <span class="text-mute">({{fr()?'facultatif sans portail':'optional without portal'}})</span></span><input [(ngModel)]="portalEmail" name="portalEmail" type="email" class="input w-full" placeholder="parent@example.com" [class.input-error]="portalAttempted()&&portalEmailInvalid()" [attr.aria-invalid]="portalAttempted()&&portalEmailInvalid()"/>@if(portalAttempted()&&portalEmailInvalid()){<span class="field-error">{{!portalEmail.trim()?(fr()?'Ajoutez un e-mail pour activer le portail.':'Add an email to enable portal access.'):(fr()?'Saisissez une adresse e-mail valide.':'Enter a valid email address.')}}</span>}</label>
            <label><span class="label">{{fr()?'Mode d’accès':'Access mode'}}<span class="required-mark">*</span></span><select [(ngModel)]="portalMode" name="portalMode" class="input w-full"><option value="SEND_INVITE">{{fr()?'Envoyer une invitation sécurisée':'Send secure invitation'}}</option><option value="CREATE_ACCOUNT">{{fr()?'Créer avec mot de passe initial':'Create with initial password'}}</option><option value="NO_PORTAL">{{fr()?'Contact sans accès portail':'Contact without portal access'}}</option></select></label>
            @if(portalMode==='CREATE_ACCOUNT'){
              <label><span class="label">{{fr()?'Mot de passe initial':'Initial password'}}<span class="required-mark">*</span></span><input [(ngModel)]="portalPassword" name="portalPassword" type="password" class="input w-full" [class.input-error]="portalAttempted()&&!passwordValid(portalPassword)" [attr.aria-invalid]="portalAttempted()&&!passwordValid(portalPassword)"/><span class="field-help">{{fr()?'Au moins 8 caractères, une lettre et un chiffre.':'At least 8 characters, one letter and one number.'}}</span>@if(portalAttempted()&&!passwordValid(portalPassword)){<span class="field-error">{{fr()?'Le mot de passe ne respecte pas les règles indiquées.':'Password does not meet the stated rules.'}}</span>}</label>
            }
            @if(formError()){<div role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{{formError()}}</div>}
            <div class="flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="closePortalAccess()">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="saving()">{{saving()?(fr()?'Enregistrement…':'Saving…'):(fr()?'Enregistrer':'Save')}}</button></div>
          </form>
        </div>
      }

      @if(editing() && canEditProfile()){
        <div class="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
          <form novalidate (ngSubmit)="saveStudent()" class="bg-white rounded-2xl shadow-xl border border-slate-200 p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto space-y-5">
            <div class="flex justify-between"><div><h2 class="text-lg font-bold">{{fr()?'Modifier la fiche élève':'Edit student profile'}}</h2><p class="text-sm text-mute mt-1">{{fr()?'Les champs obligatoires sont clairement indiqués.':'Required fields are clearly marked.'}}</p></div><button type="button" class="text-2xl text-slate-500" (click)="editing.set(false)">×</button></div>
            @if(editAttempted()&&!editValid()){<div role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-700">{{fr()?'Corrigez les champs en rouge avant d’enregistrer.':'Correct the fields in red before saving.'}}</div>}
            <div class="grid sm:grid-cols-2 gap-4">
              <label><span class="label">{{fr()?'Nom':'Last name'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><input class="input w-full" [(ngModel)]="editDraft.lastName" name="last" [class.input-error]="editAttempted()&&!editDraft.lastName.trim()" [attr.aria-invalid]="editAttempted()&&!editDraft.lastName.trim()"/>@if(editAttempted()&&!editDraft.lastName.trim()){<span class="field-error">{{fr()?'Le nom est obligatoire.':'Last name is required.'}}</span>}</label>
              <label><span class="label">{{fr()?'Prénom':'First name'}} <span class="text-mute">({{fr()?'facultatif':'optional'}})</span></span><input class="input w-full" [(ngModel)]="editDraft.firstName" name="first"/></label>
              <label><span class="label">NIU</span><input class="input w-full" [(ngModel)]="editDraft.niu" name="niu"/></label>
              <label><span class="label">{{fr()?'Classe':'Class'}}</span><select class="input w-full" [(ngModel)]="editDraft.classId" name="class"><option [ngValue]="null">{{fr()?'Aucune classe':'No class'}}</option>@for(c of classes();track c.id){<option [value]="c.id">{{c.name}}</option>}</select></label>
              <label><span class="label">{{fr()?'Date de naissance':'Birth date'}}</span><div class="flex items-center gap-2"><input type="text" class="input w-full" [ngModel]="editDobText" (ngModelChange)="onEditDobTextChange($event)" name="dobText" inputmode="numeric" placeholder="DD/MM/YYYY" [class.input-error]="editAttempted()&&editDobInvalid()"/><button type="button" class="btn-secondary shrink-0 px-3" [attr.aria-label]="fr()?'Ouvrir le calendrier':'Open calendar'" (click)="openEditDobCalendar(editDobPicker)"><bbc-icon name="calendar" [s]="16"/></button><input #editDobPicker type="date" class="sr-only" tabindex="-1" aria-hidden="true" [value]="editDraft.dob||''" (change)="onEditDobCalendarChange($any($event.target).value)"/></div>@if(editAttempted()&&editDobInvalid()){<span class="field-error">{{fr()?'Utilisez le format JJ/MM/AAAA.':'Use DD/MM/YYYY format.'}}</span>}</label>
              <label><span class="label">{{fr()?'Lieu de naissance':'Birthplace'}}</span><input class="input w-full" [(ngModel)]="editDraft.birthplace" name="birthplace"/></label>
            </div>
            <div class="flex justify-end gap-2"><button type="button" class="btn-secondary" (click)="editing.set(false)">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="saving()">{{saving()?(fr()?'Enregistrement…':'Saving…'):(fr()?'Enregistrer':'Save')}}</button></div>
          </form>
        </div>
      }

      @if(allowedEndTarget();as target){
        <div class="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4"><div class="bg-white rounded-2xl shadow-xl border border-slate-200 p-6 w-full max-w-md space-y-4">
          <h2 class="text-lg font-bold">{{fr()?'Terminer cette relation familiale ?':'End this family relationship?'}}</h2>
          <p class="text-sm text-mute">{{fr()?'Le parent perdra immédiatement l’accès à cet élève. Son compte restera actif s’il est encore lié à un autre enfant. Cette action sera auditée.':'The guardian immediately loses access to this student. Their account remains active if linked to another child. This action is audited.'}}</p>
          <label><span class="label">{{fr()?'Motif':'Reason'}}<span class="required-mark">*</span><span class="required-hint">{{fr()?'Obligatoire':'Required'}}</span></span><textarea class="input w-full" rows="3" [(ngModel)]="endReason" [class.input-error]="endAttempted()&&!endReason.trim()" [attr.aria-invalid]="endAttempted()&&!endReason.trim()"></textarea>@if(endAttempted()&&!endReason.trim()){<span class="field-error">{{fr()?'Indiquez le motif avant de confirmer.':'Enter a reason before confirming.'}}</span>}</label>
          <div class="flex justify-end gap-2"><button class="btn-secondary" (click)="cancelEnd()">{{fr()?'Annuler':'Cancel'}}</button><button class="btn-primary" [disabled]="saving()" (click)="confirmEnd()">{{fr()?'Confirmer la fin du lien':'Confirm ending link'}}</button></div>
        </div></div>
      }
    </div>`,
})
export class StudentDetailComponent {
  private api=inject(StudentApi); private route=inject(ActivatedRoute); private photoApi=inject(PhotoApi); private auth=inject(AuthService); protected i18n=inject(I18nService);
  protected fr=()=>this.i18n.lang()==='fr'; protected student=signal<Student|null>(null); protected guardians=signal<GuardianRelationshipView[]>([]); protected classes=signal<ClassView[]>([]); protected photo=signal<string|null>(null); protected error=signal<string|null>(null); protected notice=signal<string|null>(null);
  protected adding=signal(false); protected editing=signal(false); protected saving=signal(false); protected addAttempted=signal(false); protected editAttempted=signal(false); protected endAttempted=signal(false); protected portalAttempted=signal(false); protected formError=signal<string|null>(null); protected results=signal<GuardianSearchView[]>([]); protected searchQ='';
  protected draft:GuardianInput=this.blank(); protected editDraft:StudentUpsert={firstName:'',lastName:'',sex:'M',repeats:false,classId:null}; protected editDobText=''; protected endTarget=signal<GuardianRelationshipView|null>(null); protected portalTarget=signal<GuardianRelationshipView|null>(null); protected portalEmail=''; protected portalMode:GuardianAccessMode='SEND_INVITE'; protected portalPassword=''; protected endReason=''; private id=this.route.snapshot.paramMap.get('id')!;
  protected formatStudentDate=formatStudentDate;

  private actionAvailable(code:string){return ['ALLOW','CONTEXT_REQUIRED'].includes(this.auth.actionState(code));}
  protected canEditProfile=()=>this.actionAvailable('STUDENT_PROFILE_EDIT');
  protected canViewEnrollment=()=>this.actionAvailable('ENROLLMENT_VIEW');
  protected canViewGuardians=()=>this.actionAvailable('GUARDIAN_VIEW')||this.actionAvailable('GUARDIAN_LINK_MANAGE');
  protected canManageGuardians=()=>this.actionAvailable('GUARDIAN_LINK_MANAGE');
  protected allowedPortalTarget=()=>this.canManageGuardians()?this.portalTarget():null;
  protected allowedEndTarget=()=>this.canManageGuardians()?this.endTarget():null;

  constructor(){this.reload();this.api.listClassOptions().subscribe({next:c=>this.classes.set(c),error:()=>this.classes.set([])});this.photoApi.load('students',this.id).subscribe(p=>this.photo.set(p));}
  private reload(){this.api.get(this.id).subscribe({next:s=>this.student.set(s),error:e=>this.error.set(e.error?.message||'Élève introuvable')});this.api.guardians(this.id).subscribe({next:g=>this.guardians.set(g),error:()=>this.guardians.set([])});}
  protected openAdd(){this.draft=this.blank();this.searchQ='';this.results.set([]);this.formError.set(null);this.addAttempted.set(false);this.adding.set(true);}
  protected closeAdd(){this.adding.set(false);this.addAttempted.set(false);this.formError.set(null);}
  protected searchGuardian(){if(this.searchQ.trim().length<3){this.formError.set(this.fr()?'Saisissez au moins 3 caractères pour rechercher un parent.':'Enter at least 3 characters to search.');return;}this.formError.set(null);this.api.searchGuardians(this.searchQ).subscribe({next:r=>this.results.set(r),error:e=>this.formError.set(e.error?.message)});}
  protected selectExisting(r:GuardianSearchView){this.draft.guardianId=r.id;this.draft.displayName=r.displayName;this.draft.accessMode='NO_PORTAL';this.results.set([]);this.formError.set(null);}
  protected emailRequiredInvalid(){if(this.draft.accessMode==='NO_PORTAL')return false;const email=this.draft.email?.trim()||'';return !email||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);}
  protected passwordValid(password?:string|null){return !!password&&password.length>=8&&/[A-Za-z]/.test(password)&&/\d/.test(password);}
  protected addValid(){return !!this.draft.displayName.trim()&&!!this.draft.relationshipType.trim()&&!this.emailRequiredInvalid()&&(this.draft.accessMode!=='CREATE_ACCOUNT'||this.passwordValid(this.draft.initialPassword));}
  protected addGuardian(){this.addAttempted.set(true);this.formError.set(null);if(!this.addValid())return;this.saving.set(true);this.api.addGuardian(this.id,this.draft).subscribe({next:()=>{this.saving.set(false);this.closeAdd();this.draft=this.blank();this.notice.set(this.fr()?'Le parent a été lié avec succès.':'Guardian linked successfully.');this.reload();},error:e=>{this.saving.set(false);this.formError.set(e.error?.message||'Erreur')}});}
  protected openPortalAccess(g:GuardianRelationshipView){this.portalTarget.set(g);this.portalEmail=g.email||'';this.portalMode='SEND_INVITE';this.portalPassword='';this.portalAttempted.set(false);this.formError.set(null);}
  protected closePortalAccess(){this.portalTarget.set(null);this.portalAttempted.set(false);this.portalEmail='';this.portalPassword='';this.formError.set(null);}
  protected portalEmailInvalid(){if(this.portalMode==='NO_PORTAL')return false;const email=this.portalEmail.trim();return !email||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);}
  protected savePortalAccess(){this.portalAttempted.set(true);this.formError.set(null);const target=this.portalTarget();if(!target||this.portalEmailInvalid()||(this.portalMode==='CREATE_ACCOUNT'&&!this.passwordValid(this.portalPassword)))return;this.saving.set(true);this.api.updateGuardianPortalAccess(this.id,target.guardianId,{email:this.portalEmail.trim()||null,accessMode:this.portalMode,initialPassword:this.portalPassword||null}).subscribe({next:()=>{this.saving.set(false);this.closePortalAccess();this.notice.set(this.fr()?'L’accès parent a été mis à jour.':'Parent access updated.');this.reload();},error:e=>{this.saving.set(false);this.formError.set(e.error?.message||'Erreur')}});}
  protected savePermissions(g:GuardianRelationshipView){this.notice.set(null);this.api.updateRelationship(g.relationshipId,{...g,effectiveFrom:g.effectiveFrom}).subscribe({next:()=>{this.notice.set(this.fr()?'Les droits du parent ont été enregistrés.':'Guardian permissions saved.');this.reload();},error:e=>this.error.set(e.error?.message)});}
  protected end(g:GuardianRelationshipView){this.endTarget.set(g);this.endReason='';this.endAttempted.set(false);}
  protected cancelEnd(){this.endTarget.set(null);this.endReason='';this.endAttempted.set(false);}
  protected confirmEnd(){this.endAttempted.set(true);const g=this.endTarget();if(!g||!this.endReason.trim())return;this.saving.set(true);this.api.endRelationship(g.relationshipId,this.endReason.trim()).subscribe({next:()=>{this.saving.set(false);this.cancelEnd();this.notice.set(this.fr()?'La relation familiale a été terminée.':'Family relationship ended.');this.reload()},error:e=>{this.saving.set(false);this.error.set(e.error?.message)}});}
  protected resend(g:GuardianRelationshipView){this.api.resendInvite(g.guardianId).subscribe({next:()=>{this.notice.set(this.fr()?'Une nouvelle invitation a été envoyée.':'A new invitation was sent.');this.reload();},error:e=>this.error.set(e.error?.message)});}
  protected openEdit(s:Student){this.editDraft={firstName:s.firstName,lastName:s.lastName,niu:s.niu,sex:s.sex||'M',dob:s.dob,birthplace:s.birthplace,repeats:s.repeats,classId:s.classId,parentName:s.parentName,parentPhone:s.parentPhone,fatherName:s.fatherName,fatherPhone:s.fatherPhone,fatherEmail:s.fatherEmail,motherName:s.motherName,motherPhone:s.motherPhone,motherEmail:s.motherEmail,guardianName:s.guardianName,guardianPhone:s.guardianPhone,guardianEmail:s.guardianEmail,guardianRelation:s.guardianRelation};this.editDobText=formatStudentDate(s.dob);this.editAttempted.set(false);this.editing.set(true);}
  protected onEditDobTextChange(value:string){this.editDobText=maskStudentDateInput(value,value.length<this.editDobText.length);this.editDraft.dob=parseStudentDate(this.editDobText);}
  protected onEditDobCalendarChange(value:string){this.editDraft.dob=value||null;this.editDobText=formatStudentDate(value);}
  protected openEditDobCalendar(input:HTMLInputElement){if('showPicker' in input&&typeof input.showPicker==='function'){input.showPicker();return;}input.click();}
  protected editDobInvalid(){return !!this.editDobText.trim()&&!this.editDraft.dob;}
  protected editValid(){return !!this.editDraft.lastName.trim()&&!this.editDobInvalid();}
  protected saveStudent(){this.editAttempted.set(true);if(!this.editValid())return;this.saving.set(true);this.api.update(this.id,this.editDraft).subscribe({next:()=>{this.saving.set(false);this.editing.set(false);this.notice.set(this.fr()?'La fiche élève a été mise à jour.':'Student profile updated.');this.reload()},error:e=>{this.saving.set(false);this.error.set(e.error?.message)}});}
  private blank():GuardianInput{return{displayName:'',email:'',phone:'',relationshipType:'GUARDIAN',accessMode:'NO_PORTAL',legalGuardian:true,pickupAuthorized:true,receivesAcademic:true,receivesAttendance:true,portalAccess:false};}
}
