import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    loadComponent: () => import('./layout/main-layout.component').then(m => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'orders/:id', loadComponent: () => import('./orders/order-detail/order-detail.component').then(m => m.OrderDetailComponent) },
      { path: 'batch', loadComponent: () => import('./batch/batch.component').then(m => m.BatchComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
