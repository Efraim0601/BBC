import { Routes } from '@angular/router';
import { actionGuard, authGuard, contextualActionGuard, parentGuard, permissionGuard, scopeGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'join-staff/:slug',
    loadComponent: () => import('./features/staff-portal/staff-portal').then((m) => m.StaffPortalComponent),
  },
  {
    path: 'parent-invite',
    loadComponent: () => import('./features/parent/parent-account-action').then((m) => m.ParentAccountActionComponent),
    data: { mode: 'invite' },
  },
  {
    path: 'parent-reset',
    loadComponent: () => import('./features/parent/parent-account-action').then((m) => m.ParentAccountActionComponent),
    data: { mode: 'reset' },
  },
  {
    path: 'parcours',
    canActivate: [authGuard],
    loadComponent: () => import('./features/parcours/parcours-picker').then((m) => m.ParcoursPickerComponent),
  },
  {
    path: '',
    canActivate: [authGuard, scopeGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.ShellComponent),
    children: [
      {
        path: 'apps',
        loadComponent: () => import('./layout/apps-home').then((m) => m.AppsHomeComponent),
      },
      {
        path: 'dashboard',
        canActivate: [permissionGuard('dashboard')],
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'students',
        canActivate: [contextualActionGuard('STUDENT_DIRECTORY_VIEW')],
        loadComponent: () => import('./features/students/students').then((m) => m.StudentsComponent),
      },
      {
        path: 'students/new',
        canActivate: [contextualActionGuard('STUDENT_PROFILE_CREATE')],
        loadComponent: () => import('./features/students/student-registration').then((m) => m.StudentRegistrationComponent),
      },
      {
        path: 'students/import-family',
        canActivate: [contextualActionGuard('STUDENT_IMPORT')],
        loadComponent: () => import('./features/students/family-import').then((m) => m.FamilyImportComponent),
      },
      {
        path: 'students/:id',
        canActivate: [contextualActionGuard('STUDENT_DIRECTORY_VIEW')],
        loadComponent: () => import('./features/students/student-detail').then((m) => m.StudentDetailComponent),
      },
      {
        path: 'journey',
        canActivate: [permissionGuard('journey')],
        loadComponent: () => import('./features/journey/journey').then((m) => m.JourneyComponent),
      },
      {
        path: 'promotion',
        canActivate: [permissionGuard('promotion')],
        loadComponent: () => import('./features/promotion/promotion').then((m) => m.PromotionComponent),
      },
      {
        path: 'pathways',
        canActivate: [actionGuard('PROGRESSION_VIEW')],
        loadComponent: () => import('./features/promotion/pathway-selection').then((m) => m.PathwaySelectionComponent),
      },
      {
        path: 'journey/promotions',
        canActivate: [contextualActionGuard('PROGRESSION_VIEW')],
        loadComponent: () => import('./features/journey/promotion-workspace').then((m) => m.PromotionWorkspaceComponent),
      },
      {
        path: 'alerts',
        canActivate: [permissionGuard('alerts')],
        loadComponent: () => import('./features/alerts/alerts').then((m) => m.AlertsComponent),
      },
      {
        path: 'messages',
        canActivate: [permissionGuard('messages')],
        loadComponent: () => import('./features/messages/messages').then((m) => m.MessagesComponent),
      },
      {
        path: 'coursebook',
        canActivate: [permissionGuard('coursebook')],
        loadComponent: () => import('./features/coursebook/coursebook').then((m) => m.CoursebookComponent),
      },
      {
        path: 'health',
        canActivate: [permissionGuard('health')],
        loadComponent: () => import('./features/health/health').then((m) => m.HealthComponent),
      },
      {
        path: 'documents',
        canActivate: [permissionGuard('documents')],
        loadComponent: () => import('./features/documents/documents').then((m) => m.DocumentsComponent),
      },
      {
        path: 'presence',
        canActivate: [permissionGuard('presence')],
        loadComponent: () => import('./features/attendance/attendance').then((m) => m.AttendanceComponent),
      },
      {
        path: 'finance/treasury',
        canActivate: [actionGuard('TREASURY_ACCOUNT_VIEW')],
        loadComponent: () => import('./features/finance/finance-treasury').then((m) => m.FinanceTreasuryComponent),
      },
      {
        path: 'finance/fee-types',
        canActivate: [actionGuard('FEE_TYPE_MANAGE')],
        loadComponent: () => import('./features/finance/finance-fee-types').then((m) => m.FinanceFeeTypesComponent),
      },
      {
        path: 'finance/plans',
        canActivate: [actionGuard('FEE_PLAN_DRAFT')],
        loadComponent: () => import('./features/finance/finance-plans').then((m) => m.FinancePlansComponent),
      },
      {
        path: 'finance/charges',
        canActivate: [actionGuard('CHARGE_PREVIEW')],
        loadComponent: () => import('./features/finance/finance-charges').then((m) => m.FinanceChargesComponent),
      },
      {
        path: 'finance/collections',
        canActivate: [actionGuard('FEE_CONFIGURE')],
        loadComponent: () => import('./features/finance/finance-collections').then((m) => m.FinanceCollectionsComponent),
      },
      {
        path: 'finance/student-accounts',
        canActivate: [actionGuard('FINANCE_STUDENT_ACCOUNT_VIEW')],
        loadComponent: () => import('./features/finance/finance-account').then((m) => m.FinanceAccountComponent),
      },
      {
        path: 'finance/documents',
        canActivate: [actionGuard('FEE_CONFIGURE')],
        loadComponent: () => import('./features/finance/finance-documents').then((m) => m.FinanceDocumentsComponent),
      },
      {
        path: 'finance/payroll',
        canActivate: [actionGuard('PAYROLL_VIEW')],
        loadComponent: () => import('./features/finance/finance-payroll').then((m) => m.FinancePayrollComponent),
      },
      {
        path: 'finance/accounting',
        canActivate: [actionGuard('ACCOUNT_MANAGE')],
        loadComponent: () => import('./features/finance/finance-accounting').then((m) => m.FinanceAccountingComponent),
      },
      {
        path: 'finance/reports',
        canActivate: [actionGuard('FINANCE_REPORT_VIEW')],
        loadComponent: () => import('./features/finance/finance-reports').then((m) => m.FinanceReportsComponent),
      },
      {
        path: 'finance',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance').then((m) => m.FinanceComponent),
      },
      {
        path: 'staff/create',
        canActivate: [actionGuard('HR_MANAGE')],
        data: { staffView: 'create' },
        loadComponent: () => import('./features/staff/staff').then((m) => m.StaffComponent),
      },
      {
        path: 'staff/:employeeId/edit',
        canActivate: [actionGuard('HR_MANAGE')],
        data: { staffView: 'edit' },
        loadComponent: () => import('./features/staff/staff').then((m) => m.StaffComponent),
      },
      {
        path: 'staff/:employeeId',
        canActivate: [contextualActionGuard('HR_VIEW')],
        data: { staffView: 'detail' },
        loadComponent: () => import('./features/staff/staff').then((m) => m.StaffComponent),
      },
      {
        path: 'staff',
        canActivate: [contextualActionGuard('HR_VIEW')],
        data: { staffView: 'list' },
        loadComponent: () => import('./features/staff/staff').then((m) => m.StaffComponent),
      },
      {
        path: 'events',
        canActivate: [actionGuard('EVENTS_VIEW')],
        loadComponent: () => import('./features/events/events').then((m) => m.EventsComponent),
      },
      {
        path: 'settings',
        canActivate: [permissionGuard('settings')],
        loadComponent: () => import('./features/settings/settings').then((m) => m.SettingsComponent),
      },
      {
        path: 'access-control',
        canActivate: [actionGuard('PERMISSION_VIEW')],
        loadComponent: () => import('./features/settings/access-control-workspace').then((m) => m.AccessControlWorkspaceComponent),
      },
      {
        path: 'academic',
        canActivate: [permissionGuard('academic')],
        loadComponent: () => import('./features/academic/academic').then((m) => m.AcademicComponent),
      },
      {
        path: 'timetable',
        canActivate: [permissionGuard('timetable')],
        loadComponent: () => import('./features/timetable/timetable').then((m) => m.TimetableComponent),
      },
      {
        path: 'discipline',
        canActivate: [permissionGuard('discipline')],
        loadComponent: () => import('./features/discipline/discipline').then((m) => m.DisciplineComponent),
      },
      {
        path: 'reports',
        canActivate: [permissionGuard('reports')],
        loadComponent: () => import('./features/reports/reports').then((m) => m.ReportsComponent),
      },
      {
        path: 'library',
        canActivate: [permissionGuard('library')],
        loadComponent: () => import('./features/library/library').then((m) => m.LibraryComponent),
      },
      {
        path: 'classkit',
        canActivate: [permissionGuard('classkit')],
        loadComponent: () => import('./features/classkit/classkit').then((m) => m.ClasskitComponent),
      },
      {
        path: 'parent',
        canActivate: [parentGuard],
        loadComponent: () => import('./features/parent/parent').then((m) => m.ParentComponent),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
