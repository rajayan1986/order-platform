import { Component, OnInit, OnDestroy, input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../services/order.service';
import { WebSocketService } from '../../services/websocket.service';
import { Order, OrderStatusUpdate } from '../../models/order.model';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './order-detail.component.html',
  styleUrls: ['./order-detail.component.css'],
})
export class OrderDetailComponent implements OnInit, OnDestroy {
  id = input.required<string>();
  private orderService = inject(OrderService);
  private ws = inject(WebSocketService);

  order: Order | null = null;
  loading = true;
  private unsubscribeWs: (() => void) | null = null;

  ngOnInit(): void {
    const orderId = this.id();
    this.orderService.getOrder(orderId).subscribe((o: Order | null) => {
      this.order = o;
      this.loading = false;
    });
    this.unsubscribeWs = this.ws.subscribeToOrder(orderId, (update: OrderStatusUpdate) => {
      if (this.order && update.orderId === this.order.orderId) {
        this.order = { ...this.order, status: update.toStatus ?? this.order.status };
      }
    });
  }

  ngOnDestroy(): void {
    this.unsubscribeWs?.();
  }

  statusClass(status: string): string {
    return `status-${status}`;
  }
}
