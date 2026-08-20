import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ClassRef { id:string; name:string; sectionId:string; subsystem:string; level:string;
  model:'HOMEROOM'|'DEPARTMENTAL'; status:'DRAFT'|'PUBLISHED'; homeroomTeacherId:string|null;
  homeroomTeacherName:string|null; version:number; }
export interface SubjectTeacherView { subjectCode:string; teacherId:string|null; teacherName:string|null;
  teacherCode:string|null; source:string|null; locked:boolean; message:string|null;
  status?:'RESOLVED'|'MISSING'|'AMBIGUOUS'; errorCode?:string|null; assignmentId?:string|null; assignmentVersion?:number; }
export interface PeriodView { id:string; slotIdx:number; label:string; startTime:string; endTime:string; active:boolean; }
export interface SlotView { id:string; dayIdx:number; slotIdx:number; subjectCode:string|null;
  teacherId:string|null; room:string|null; className:string|null; subjectName?:string|null; }
export interface ConflictSlot { classId:string; className:string|null; subjectCode:string|null; room:string|null; }
export interface TeacherConflict { dayIdx:number; slotIdx:number; teacherId:string; teacherName:string|null; slots:ConflictSlot[]; }
export interface TeacherSchedule { teacherId:string; teacherName:string; sessionLabel:string; periods?:PeriodView[]; slots:SlotView[]; }
export interface SlotSaveBody { className:string; dayIdx:number; slotIdx:number; subjectCode?:string; teacherId?:string; room?:string; }
export interface TimetableVersionView { id:string; academicSessionId:string; versionNo:number; status:'DRAFT'|'PUBLISHED'|'ARCHIVED'; effectiveFrom:string; effectiveTo:string|null; timezone:string; copiedFromVersionId:string|null; slotCount:number; classCount:number; version:number; }
export interface TimetableVersionDiff { fromVersionId:string; toVersionId:string; added:number; removed:number; changed:number; changes:string[]; }
export interface TimetableDriftView { slotId:string; classId:string; className:string; subjectCode:string|null; dayIdx:number; slotIdx:number;
  publishedTeacherId:string|null; publishedTeacherName:string|null; currentTeacherId:string|null; currentTeacherName:string|null;
  publishedAssignmentId:string|null; publishedAssignmentVersion:number; currentAssignmentId:string|null; currentAssignmentVersion:number; status:string; message:string; }
export interface TimetableProjectionSlotView { id:string; classId:string; className:string; subjectCode:string|null; teacherId:string|null;
  teacherName:string|null; room:string|null; dayIdx:number; slotIdx:number; occurrenceDate:string; substitutionAction:string|null; substitutionTeacherName:string|null; }
export interface RoomView { id:string; code:string; label:string; capacity:number|null; resourceType:string; active:boolean; version:number; }
export interface RoomAvailabilityView { id:string; roomId:string; dayIdx:number; slotIdx:number; available:boolean; reason:string|null; }
export interface TeacherAvailabilityView { id:string; employeeId:string; dayIdx:number; slotIdx:number; available:boolean; reason:string|null; }
export interface TeacherWorkloadView { id:string|null; employeeId:string; maxSlotsPerDay:number|null; maxSlotsPerWeek:number|null; effectiveFrom:string; effectiveTo:string|null; reason:string|null; version:number; }
export interface TeacherWorkloadUpsert { maxSlotsPerDay:number|null; maxSlotsPerWeek:number|null; effectiveFrom:string; effectiveTo?:string|null; reason?:string|null; version?:number; }
export interface TeacherQualificationView { id:string; employeeId:string; qualificationCode:string; validFrom:string; validTo:string|null; evidenceReference:string|null; version:number; }
export interface TeacherQualificationUpsert { qualificationCode:string; validFrom:string; validTo?:string|null; evidenceReference?:string|null; version?:number; }
export interface SubjectQualificationRequirementView { id:string; academicSessionId:string; subjectCode:string; qualificationCode:string; effectiveFrom:string; effectiveTo:string|null; reason:string|null; version:number; }
export interface SubjectQualificationRequirementUpsert { academicSessionId:string; subjectCode:string; qualificationCode:string; effectiveFrom:string; effectiveTo?:string|null; reason?:string|null; version?:number; }
export interface SubstitutionView { id:string; academicSessionId:string; timetableVersionId:string|null; occurrenceDate:string; classId:string; className:string; subjectCode:string|null; dayIdx:number; slotIdx:number; originalTeacherId:string|null; originalTeacherName:string|null; replacementTeacherId:string|null; replacementTeacherName:string|null; action:'SUBSTITUTE'|'CANCEL'; reason:string; status:'DRAFT'|'APPROVED'|'CANCELLED'; version:number; }

@Injectable({providedIn:'root'})
export class TimetableApi {
  private http=inject(HttpClient); private base=`${environment.apiUrl}/timetable`;
  classes():Observable<ClassRef[]>{return this.http.get<ClassRef[]>(`${this.base}/classes`);}
  subjectTeachers(classId:string):Observable<SubjectTeacherView[]>{return this.http.get<SubjectTeacherView[]>(`${this.base}/classes/${classId}/subject-teachers`);}
  periods():Observable<PeriodView[]>{return this.http.get<PeriodView[]>(`${this.base}/periods`);}
  updatePeriod(p:PeriodView):Observable<PeriodView>{return this.http.put<PeriodView>(`${this.base}/periods/${p.slotIdx}`,{label:p.label,startTime:p.startTime,endTime:p.endTime,active:p.active});}
  rooms():Observable<string[]>{return this.http.get<string[]>(`${this.base}/rooms`);}
  grid(className:string,versionId?:string|null):Observable<SlotView[]>{return this.http.get<SlotView[]>(`${this.base}?className=${encodeURIComponent(className)}${versionId?`&versionId=${encodeURIComponent(versionId)}`:''}`);}
  conflicts():Observable<TeacherConflict[]>{return this.http.get<TeacherConflict[]>(`${this.base}/conflicts`);}
  saveSlot(body:SlotSaveBody):Observable<{slot:SlotView;conflicts:TeacherConflict[]}>{return this.http.put<any>(`${this.base}/slot`,body);}
  deleteSlot(className:string,dayIdx:number,slotIdx:number):Observable<void>{return this.http.delete<void>(`${this.base}?className=${encodeURIComponent(className)}&dayIdx=${dayIdx}&slotIdx=${slotIdx}`);}
  publishClass(classId:string,version:number,reason:string):Observable<ClassRef>{return this.http.post<ClassRef>(`${this.base}/classes/${classId}/publish`,{version,reason});}
  reopenClass(classId:string,version:number,reason:string):Observable<ClassRef>{return this.http.post<ClassRef>(`${this.base}/classes/${classId}/reopen`,{version,reason});}
  mySchedule():Observable<TeacherSchedule>{return this.http.get<TeacherSchedule>(`${this.base}/teachers/me`);}
  teacherSchedule(id:string):Observable<TeacherSchedule>{return this.http.get<TeacherSchedule>(`${this.base}/teachers/${id}`);}
  versions(sessionId:string):Observable<TimetableVersionView[]>{return this.http.get<TimetableVersionView[]>(`${this.base}/versions`,{params:{academicSessionId:sessionId}});}
  publishVersion(id:string,reason:string,version?:number):Observable<TimetableVersionView>{return this.http.post<TimetableVersionView>(`${this.base}/versions/${id}/publish`,{reason,version});}
  reopenVersion(id:string,reason:string,version?:number):Observable<TimetableVersionView>{return this.http.post<TimetableVersionView>(`${this.base}/versions/${id}/reopen`,{reason,version});}
  versionDiff(fromVersionId:string,toVersionId:string):Observable<TimetableVersionDiff>{return this.http.get<TimetableVersionDiff>(`${this.base}/versions/diff`,{params:{fromVersionId,toVersionId}});}
  drift(versionId:string):Observable<TimetableDriftView[]>{return this.http.get<TimetableDriftView[]>(`${this.base}/versions/${versionId}/drift`);}
  master(versionId:string,occurrenceDate?:string):Observable<TimetableProjectionSlotView[]>{return this.http.get<TimetableProjectionSlotView[]>(`${this.base}/versions/${versionId}/master`,{params:occurrenceDate?{occurrenceDate}:{}});}
  exportVersion(versionId:string,format:'csv'|'ics'|'xlsx'|'pdf'='csv'):Observable<Blob>{return this.http.get(`${this.base}/versions/${versionId}/export.${format}`,{responseType:'blob'});}
  substitutions(sessionId:string,date?:string):Observable<SubstitutionView[]>{return this.http.get<SubstitutionView[]>(`${this.base}/substitutions`,{params:{academicSessionId:sessionId,...(date?{occurrenceDate:date}:{})}});}
  roomsV2():Observable<RoomView[]>{return this.http.get<RoomView[]>(`${this.base}/resources/rooms`);}
  roomAvailability(roomId:string):Observable<RoomAvailabilityView[]>{return this.http.get<RoomAvailabilityView[]>(`${this.base}/resources/rooms/${roomId}/availability`);}
  teacherAvailability(teacherId:string):Observable<TeacherAvailabilityView[]>{return this.http.get<TeacherAvailabilityView[]>(`${this.base}/resources/teachers/${teacherId}/availability`);}
  teacherWorkload(teacherId:string):Observable<TeacherWorkloadView[]>{return this.http.get<TeacherWorkloadView[]>(`${this.base}/resources/teachers/${teacherId}/workload`);}
  saveTeacherWorkload(teacherId:string,body:TeacherWorkloadUpsert):Observable<TeacherWorkloadView>{return this.http.put<TeacherWorkloadView>(`${this.base}/resources/teachers/${teacherId}/workload`,body);}
  teacherQualifications(teacherId:string):Observable<TeacherQualificationView[]>{return this.http.get<TeacherQualificationView[]>(`${this.base}/resources/teachers/${teacherId}/qualifications`);}
  saveTeacherQualification(teacherId:string,body:TeacherQualificationUpsert):Observable<TeacherQualificationView>{return this.http.post<TeacherQualificationView>(`${this.base}/resources/teachers/${teacherId}/qualifications`,body);}
  subjectQualificationRequirements(sessionId:string):Observable<SubjectQualificationRequirementView[]>{return this.http.get<SubjectQualificationRequirementView[]>(`${this.base}/resources/subjects/qualification-requirements`,{params:{academicSessionId:sessionId}});}
  saveSubjectQualificationRequirement(body:SubjectQualificationRequirementUpsert):Observable<SubjectQualificationRequirementView>{return this.http.put<SubjectQualificationRequirementView>(`${this.base}/resources/subjects/qualification-requirements`,body);}
}
