export type Level = 'none' | 'read' | 'write';

/** A parcours the user may access (level × subsystem). */
export interface Parcours {
  level: 'maternelle' | 'primary' | 'secondary';
  subsystem: 'FR' | 'EN';
}

export interface UserView {
  id: string;
  username: string;
  displayName: string;
  initials: string;
  role: string;
  schoolId: string;
  schoolCode: string;
  schoolName: string;
  locale: string;
  permissions: Record<string, Level>;
  modules: string[];
  /** Server-authoritative parcours scope mode. GLOBAL means the user may browse all parcours. */
  parcoursScopeMode?: 'GLOBAL' | 'EXPLICIT' | 'ASSIGNMENT_DERIVED' | 'CHILD_DERIVED' | 'NONE' | string;
  allowedParcours: Parcours[]; // empty = all parcours (admin)
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
  user: UserView;
}

export type ActionEffect = 'ALLOW' | 'DENY' | 'CONTEXT_REQUIRED' | 'INHERIT' | 'LOADING';

export interface ActionCapability {
  actionCode: string;
  labelFr: string;
  labelEn: string;
  effect: ActionEffect | string;
  scopeMode: string;
  source: string;
  requiresContext: boolean;
  riskLevel: string;
}

export interface CapabilityView {
  policyVersion: number;
  parcoursScopeMode: string;
  allowedParcours: string[];
  actions: ActionCapability[];
}

export interface Student {
  id: string;
  matricule: string;
  niu: string | null;
  firstName: string;
  lastName: string;
  name: string;
  sex: string;
  dob: string | null;
  birthplace: string | null;
  repeats: boolean;
  classId: string | null;
  className: string;
  subsystem: string;
  level: string;
  parentName: string;
  parentPhone: string;
  fatherName?: string | null;
  fatherPhone?: string | null;
  fatherEmail?: string | null;
  motherName?: string | null;
  motherPhone?: string | null;
  motherEmail?: string | null;
  guardianName?: string | null;
  guardianPhone?: string | null;
  guardianEmail?: string | null;
  guardianRelation?: string | null;
  photoHue: number;
}

export interface AttendanceView {
  studentId: string;
  matricule: string;
  studentName: string;
  className: string;
  date: string;
  status: 'present' | 'late' | 'absent';
  checkInTime: string | null;
  lateMinutes: number;
  source: string;
}

export interface DailyBoard {
  date: string;
  present: number;
  late: number;
  absent: number;
  records: AttendanceView[];
}

export type RollStatus = 'unmarked' | 'present' | 'absent' | 'late' | 'excused';
export interface AttendancePolicy {
  id: string; level: string; model: 'DAILY' | 'PERIOD'; lateAfterMinutes: number;
  chronicAbsencePercent: number; requireAbsenceReason: boolean;
}
export interface AttendanceClass {
  id: string; name: string; level: string; subsystem: string; model: 'DAILY' | 'PERIOD'; enrolledCount: number;
}
export interface AttendanceSessionSummary {
  id: string; classId: string; className: string; date: string; model: 'DAILY' | 'PERIOD';
  periodKey: string; subjectCode: string | null; status: 'DRAFT' | 'FINALIZED' | 'REOPENED';
  version: number; total: number; marked: number;
}
export interface AttendanceRosterMark {
  studentId: string; matricule: string; studentName: string; status: RollStatus;
  reason: string | null; note: string | null; lateMinutes: number; source: string;
}
export interface AttendanceRoster {
  session: AttendanceSessionSummary;
  marks: AttendanceRosterMark[];
  events: { action: string; actor: string; reason: string | null; occurredAt: string }[];
}
export interface StudentAttendanceAnalytics {
  studentId: string; matricule: string; studentName: string; className: string;
  expected: number; present: number; late: number; absent: number; excused: number;
  unmarked: number; attendancePercent: number;
}
export interface AttendanceAnalytics {
  from: string; to: string; expected: number; present: number; late: number; absent: number;
  excused: number; unmarked: number; attendancePercent: number;
  students: StudentAttendanceAnalytics[];
}
export interface DeviceReconciliation {
  deviceRecordId: string; studentId: string; matricule: string; studentName: string;
  className: string; date: string; status: string; checkInTime: string | null;
  reconciled: boolean; sessionId: string | null;
}
export interface AttendanceDevice {
  id: string; label: string; location: string; model: string; active: boolean; online: boolean;
  lastSeenAt: string | null; minutesSinceLastSeen: number | null;
}

export interface PaymentView {
  id: string;
  receiptNo: string;
  studentId: string;
  /** Null when the payment outlives the student record it points at. */
  studentName: string | null;
  matricule: string | null;
  className: string | null;
  amount: number;
  /** Code du canal : CASH, OM, MOMO, MPGS, TRANSFER… */
  method: string;
  methodLabelFr: string;
  methodLabelEn: string;
  /** Référence de transaction chez l'opérateur, si le canal l'exige. */
  reference: string | null;
  tranche: number | null;
  paidOn: string;
}

export interface FinanceSummary {
  totalRevenue30d: number;
  totalExpense30d: number;
  balance30d: number;
  paymentsCount: number;
  revenueSeries: { date: string; amount: number }[];
}
