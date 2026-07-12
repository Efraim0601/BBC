import { Routes } from '@angular/router';
import { authGuard, permissionGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
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
        path: 'journey',
        canActivate: [permissionGuard('journey')],
        loadComponent: () => import('./features/journey/journey').then((m) => m.JourneyComponent),
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
        path: 'parent',
        loadComponent: () => import('./features/parent/parent').then((m) => m.ParentComponent),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
