import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./pages/register/register').then((m) => m.Register) },
  {
    path: 'forgot-password',
    loadComponent: () => import('./pages/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./pages/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: '',
    loadComponent: () => import('./layout/main-layout/main-layout').then((m) => m.MainLayout),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard) },
      { path: 'datasets', loadComponent: () => import('./pages/datasets/datasets').then((m) => m.Datasets) },
      { path: 'reports', loadComponent: () => import('./pages/reports/reports').then((m) => m.Reports) },
      {
        // Must come before 'reports/:id' - otherwise the router matches
        // "compare" as the :id parameter instead of this dedicated route.
        path: 'reports/compare',
        loadComponent: () =>
          import('./pages/reports/reports-compare/reports-compare').then((m) => m.ReportsCompare),
      },
      {
        path: 'reports/:id',
        loadComponent: () =>
          import('./pages/reports/report-detail/report-detail').then((m) => m.ReportDetail),
      },
      {
        path: 'reports/:id/anomalies',
        loadComponent: () =>
          import('./pages/reports/anomaly-explorer/anomaly-explorer').then((m) => m.AnomalyExplorer),
      },
      {
        path: 'validation-rules',
        loadComponent: () => import('./pages/validation-rules/validation-rules').then((m) => m.ValidationRules),
      },
      {
        path: 'ai-assistant',
        loadComponent: () => import('./pages/ai-assistant/ai-assistant').then((m) => m.AiAssistant),
      },
      {
        path: 'administration',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/administration/administration').then((m) => m.Administration),
      },
      { path: 'settings', loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings) },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
