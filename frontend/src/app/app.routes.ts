import { Routes } from '@angular/router';
import { authGuard, permissionGuard, scopeGuard } from './core/guards';

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
        canActivate: [permissionGuard('students')],
        loadComponent: () => import('./features/students/students').then((m) => m.StudentsComponent),
      },
      {
        path: 'students/new',
        canActivate: [permissionGuard('students')],
        loadComponent: () => import('./features/students/student-registration').then((m) => m.StudentRegistrationComponent),
      },
      {
        path: 'students/import-family',
        canActivate: [permissionGuard('students')],
        loadComponent: () => import('./features/students/family-import').then((m) => m.FamilyImportComponent),
      },
      {
        path: 'students/:id',
        canActivate: [permissionGuard('students')],
        loadComponent: () => import('./features/students/student-detail').then((m) => m.StudentDetailComponent),
      },
      {
        path: 'journey',
        canActivate: [permissionGuard('journey')],
        loadComponent: () => import('./features/journey/journey').then((m) => m.JourneyComponent),
      },
      {
        path: 'journey/promotions',
        canActivate: [permissionGuard('journey')],
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
        path: 'finance/fee-types',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-fee-types').then((m) => m.FinanceFeeTypesComponent),
      },
      {
        path: 'finance/plans',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-plans').then((m) => m.FinancePlansComponent),
      },
      {
        path: 'finance/charges',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-charges').then((m) => m.FinanceChargesComponent),
      },
      {
        path: 'finance/collections',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-collections').then((m) => m.FinanceCollectionsComponent),
      },
      {
        path: 'finance/documents',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-documents').then((m) => m.FinanceDocumentsComponent),
      },
      {
        path: 'finance/payroll',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-payroll').then((m) => m.FinancePayrollComponent),
      },
      {
        path: 'finance/accounting',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-accounting').then((m) => m.FinanceAccountingComponent),
      },
      {
        path: 'finance/reports',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance-reports').then((m) => m.FinanceReportsComponent),
      },
      {
        path: 'finance',
        canActivate: [permissionGuard('finance')],
        loadComponent: () => import('./features/finance/finance').then((m) => m.FinanceComponent),
      },
      {
        path: 'staff',
        canActivate: [permissionGuard('hr')],
        loadComponent: () => import('./features/staff/staff').then((m) => m.StaffComponent),
      },
      {
        path: 'events',
        canActivate: [permissionGuard('events')],
        loadComponent: () => import('./features/events/events').then((m) => m.EventsComponent),
      },
      {
        path: 'settings',
        canActivate: [permissionGuard('settings')],
        loadComponent: () => import('./features/settings/settings').then((m) => m.SettingsComponent),
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
        path: 'classkit',
        canActivate: [permissionGuard('classkit')],
        loadComponent: () => import('./features/classkit/classkit').then((m) => m.ClasskitComponent),
      },
      {
        path: 'parent',
        loadComponent: () => import('./features/parent/parent').then((m) => m.ParentComponent),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
