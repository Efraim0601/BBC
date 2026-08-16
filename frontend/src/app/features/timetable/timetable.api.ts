import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ClassRef { id:string; name:string; sectionId:string; subsystem:string; level:string;
  model:'HOMEROOM'|'DEPARTMENTAL'; status:'DRAFT'|'PUBLISHED'; homeroomTeacherId:string|null;
  homeroomTeacherName:string|null; version:number; }
export interface SubjectTeacherView { subjectCode:string; teacherId:string|null; teacherName:string|null;
  teacherCode:string|null; source:string|null; locked:boolean; message:string|null; }
export interface PeriodView { id:string; slotIdx:number; label:string; startTime:string; endTime:string; active:boolean; }
export interface SlotView { id:string; dayIdx:number; slotIdx:number; subjectCode:string|null;
  teacherId:string|null; room:string|null; className:string|null; }
export interface ConflictSlot { classId:string; className:string|null; subjectCode:string|null; room:string|null; }
export interface TeacherConflict { dayIdx:number; slotIdx:number; teacherId:string; teacherName:string|null; slots:ConflictSlot[]; }
export interface TeacherSchedule { teacherId:string; teacherName:string; sessionLabel:string; slots:SlotView[]; }
export interface SlotSaveBody { className:string; dayIdx:number; slotIdx:number; subjectCode?:string; teacherId?:string; room?:string; }

@Injectable({providedIn:'root'})
export class TimetableApi {
  private http=inject(HttpClient); private base=`${environment.apiUrl}/timetable`;
  classes():Observable<ClassRef[]>{return this.http.get<ClassRef[]>(`${this.base}/classes`);}
  subjectTeachers(classId:string):Observable<SubjectTeacherView[]>{return this.http.get<SubjectTeacherView[]>(`${this.base}/classes/${classId}/subject-teachers`);}
  periods():Observable<PeriodView[]>{return this.http.get<PeriodView[]>(`${this.base}/periods`);}
  updatePeriod(p:PeriodView):Observable<PeriodView>{return this.http.put<PeriodView>(`${this.base}/periods/${p.slotIdx}`,{label:p.label,startTime:p.startTime,endTime:p.endTime,active:p.active});}
  rooms():Observable<string[]>{return this.http.get<string[]>(`${this.base}/rooms`);}
  grid(className:string):Observable<SlotView[]>{return this.http.get<SlotView[]>(`${this.base}?className=${encodeURIComponent(className)}`);}
  conflicts():Observable<TeacherConflict[]>{return this.http.get<TeacherConflict[]>(`${this.base}/conflicts`);}
  saveSlot(body:SlotSaveBody):Observable<{slot:SlotView;conflicts:TeacherConflict[]}>{return this.http.put<any>(`${this.base}/slot`,body);}
  deleteSlot(className:string,dayIdx:number,slotIdx:number):Observable<void>{return this.http.delete<void>(`${this.base}?className=${encodeURIComponent(className)}&dayIdx=${dayIdx}&slotIdx=${slotIdx}`);}
  configure(classId:string,homeroomTeacherId:string|null,version:number):Observable<ClassRef>{return this.http.put<ClassRef>(`${this.base}/classes/${classId}/config`,{homeroomTeacherId,version});}
  assignTeacher(classId:string,teacherId:string,subjectCodes:string[]):Observable<void>{return this.http.put<void>(`${this.base}/classes/${classId}/teachers/${teacherId}`,{subjectCodes});}
  publish(classId:string,version:number):Observable<ClassRef>{return this.http.post<ClassRef>(`${this.base}/classes/${classId}/publish`,{version,reason:'Publication du planning'});}
  reopen(classId:string,version:number,reason:string):Observable<ClassRef>{return this.http.post<ClassRef>(`${this.base}/classes/${classId}/reopen`,{version,reason});}
  mySchedule():Observable<TeacherSchedule>{return this.http.get<TeacherSchedule>(`${this.base}/teachers/me`);}
  teacherSchedule(id:string):Observable<TeacherSchedule>{return this.http.get<TeacherSchedule>(`${this.base}/teachers/${id}`);}
}
