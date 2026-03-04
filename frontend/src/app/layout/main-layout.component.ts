import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { OrderService } from '../services/order.service';
import { DashboardStats } from '../models/order.model';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.css'],
})
export class MainLayoutComponent implements OnInit {
  private auth = inject(AuthService);
  private orderService = inject(OrderService);

  stats$: Observable<DashboardStats | null> | null = null;
  ordersToday = 0;
  totalOrders = 0;

  ngOnInit(): void {
    this.stats$ = this.orderService.getDashboardStats();
    this.stats$.subscribe((s) => {
      if (s) {
        this.ordersToday = s.ordersToday;
        this.totalOrders = s.totalOrders;
      }
    });
  }

  logout(): void {
    this.auth.clearToken();
    window.location.href = '/login';
  }

  initial(): string {
    return this.auth.isAuthenticated() ? 'U' : 'O';
  }
}
