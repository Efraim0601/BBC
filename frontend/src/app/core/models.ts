export type Level = 'none' | 'read' | 'write';

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
  firstName: string;
  lastName: string;
  name: string;
  sex: string;
  dob: string | null;
  className: string;
  subsystem: string;
  level: string;
  parentName: string;
  parentPhone: string;
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
