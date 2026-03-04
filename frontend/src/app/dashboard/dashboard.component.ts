import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subject, interval, takeUntil } from 'rxjs';
import { OrderService } from '../services/order.service';
import { WebSocketService } from '../services/websocket.service';
import { Order } from '../models/order.model';
import { DashboardStats, ProcessingStats, NotificationStats } from '../models/order.model';
import { CreateOrderDialogComponent } from '../orders/create-order-dialog/create-order-dialog.component';
import { MatDialog } from '@angular/material/dialog';

const PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10000; // 10s so e2e-flow.sh ingestion shows up sooner
const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];

type StatusFilter = 'all' | 'PENDING' | 'processing' | 'SHIPPED';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  private orderService = inject(OrderService);
  private ws = inject(WebSocketService);
  private dialog = inject(MatDialog);
  private destroy$ = new Subject<void>();

  orders: (Order & { updated?: boolean })[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = PAGE_SIZE;
  pageSizeOptions = PAGE_SIZE_OPTIONS;
  loading = false;
  statusFilter: StatusFilter = 'all';
  stats: DashboardStats | null = null;
  processingStats: ProcessingStats | null = null;
  notificationStats: NotificationStats | null = null;
  streamEvents: string[] = [];

  ngOnInit(): void {
    this.ws.connect();
    this.loadPage();
    this.loadStats();
    interval(REFRESH_INTERVAL_MS).pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadPage();
      this.loadStats();
    });
    // Real-time refresh via WebSocket when orders are created or status changes (e.g. bulk ingestion)
    const unsubRefresh = this.ws.subscribeDashboardRefresh(() => {
      this.loadPage();
      this.loadStats();
    });
    this.destroy$.subscribe(() => unsubRefresh());
    // Refetch when user returns to tab (e.g. after running e2e-flow.sh)
    if (typeof document !== 'undefined' && document.addEventListener) {
      const onVisibility = () => {
        if (document.visibilityState === 'visible') {
          this.loadPage();
          this.loadStats();
        }
      };
      document.addEventListener('visibilitychange', onVisibility);
      this.destroy$.subscribe(() => {
        document.removeEventListener('visibilitychange', onVisibility);
      });
    }
    this.ws.statusUpdates.pipe(takeUntil(this.destroy$)).subscribe((update) => {
      const row = this.orders.find((r) => r.orderId === update.orderId);
      if (row) {
        row.status = update.toStatus ?? row.status;
        (row as { updated?: boolean }).updated = true;
        this.streamEvents = [`${update.orderId} → ${update.toStatus}`, ...this.streamEvents.slice(0, 19)];
        setTimeout(() => {
          (row as { updated?: boolean }).updated = false;
        }, 800);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadPage(): void {
    this.loading = true;
    this.orderService.getOrders(this.pageIndex, this.pageSize).subscribe((res) => {
      this.orders = (res.content || []) as (Order & { updated?: boolean })[];
      this.totalElements = res.totalElements ?? 0;
      // Clamp pageIndex if current page is beyond last page (e.g. after data changed)
      const totalPages = Math.max(1, Math.ceil(this.totalElements / this.pageSize));
      if (this.pageIndex >= totalPages && totalPages > 0) {
        this.pageIndex = totalPages - 1;
        this.loadPage();
        return;
      }
      this.loading = false;
    });
  }

  loadStats(): void {
    this.orderService.getDashboardStats().subscribe((s) => (this.stats = s));
    this.orderService.getProcessingStats().subscribe((s) => (this.processingStats = s));
    this.orderService.getNotificationStats().subscribe((s) => (this.notificationStats = s));
  }

  get filteredOrders(): (Order & { updated?: boolean })[] {
    if (this.statusFilter === 'all') return this.orders;
    if (this.statusFilter === 'PENDING') return this.orders.filter((o) => o.status === 'PENDING');
    if (this.statusFilter === 'SHIPPED') return this.orders.filter((o) => o.status === 'SHIPPED');
    if (this.statusFilter === 'processing') {
      return this.orders.filter((o) =>
        ['PAYMENT_VALIDATED', 'COMPLIANCE_CHECKED', 'APPROVED'].includes(o.status)
      );
    }
    return this.orders;
  }

  setFilter(f: StatusFilter): void {
    this.statusFilter = f;
  }

  onPrev(): void {
    if (this.pageIndex > 0) {
      this.pageIndex--;
      this.loadPage();
    }
  }

  onNext(): void {
    if ((this.pageIndex + 1) * this.pageSize < this.totalElements) {
      this.pageIndex++;
      this.loadPage();
    }
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalElements / this.pageSize));
  }

  get rangeStart(): number {
    if (this.totalElements === 0) return 0;
    return this.pageIndex * this.pageSize + 1;
  }

  get rangeEnd(): number {
    const end = (this.pageIndex + 1) * this.pageSize;
    return Math.min(end, this.totalElements);
  }

  onPageSizeChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const newSize = Number(select?.value) || this.pageSize;
    if (newSize !== this.pageSize) {
      this.pageSize = newSize;
      this.pageIndex = 0;
      this.loadPage();
    }
  }

  goToFirst(): void {
    if (this.pageIndex !== 0) {
      this.pageIndex = 0;
      this.loadPage();
    }
  }

  goToLast(): void {
    const last = this.totalPages - 1;
    if (last >= 0 && this.pageIndex !== last) {
      this.pageIndex = last;
      this.loadPage();
    }
  }

  openCreateOrder(): void {
    const dlg = this.dialog.open(CreateOrderDialogComponent, {
      width: '460px',
      panelClass: 'tracknexus-dialog',
    });
    dlg.afterClosed().subscribe((created) => {
      if (created) {
        this.loadPage();
        this.loadStats();
      }
    });
  }

  statusClass(status: string): string {
    return `status-${status}`;
  }

  pipelineStages(): { name: string; count: number; pct: number; color: string }[] {
    if (!this.stats) return [];
    const total = this.stats.totalOrders || 1;
    return [
      { name: 'Order Ingestion', count: this.stats.totalOrders, pct: 100, color: 'var(--accent)' },
      { name: 'Pending', count: this.stats.pendingCount, pct: (this.stats.pendingCount / total) * 100, color: 'var(--warn)' },
      { name: 'Processing', count: this.stats.processingCount, pct: (this.stats.processingCount / total) * 100, color: 'var(--accent)' },
      { name: 'Shipped', count: this.stats.shippedCount, pct: (this.stats.shippedCount / total) * 100, color: 'var(--accent3)' },
    ];
  }
}
