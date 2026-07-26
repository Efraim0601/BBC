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
  allowedParcours: Parcours[]; // empty = all parcours (admin)
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
  user: UserView;
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

export interface PaymentView {
  id: string;
  receiptNo: string;
  studentId: string;
  /** Null when the payment outlives the student record it points at. */
  studentName: string | null;
  matricule: string | null;
  className: string | null;
  amount: number;
  method: string;
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
