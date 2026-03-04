import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { OrderStatusUpdate } from '../models/order.model';

const RECONNECT_DELAY_MS = 5000;

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private readonly statusUpdates$ = new Subject<OrderStatusUpdate>();
  private connected = false;
  private wsBaseUrl = '';

  setWsBaseUrl(url: string): void {
    this.wsBaseUrl = url.replace(/\/$/, '') + '/ws';
  }

  get statusUpdates(): Observable<OrderStatusUpdate> {
    return this.statusUpdates$.asObservable();
  }

  connect(): void {
    if (this.client?.active) return;
    const url = this.wsBaseUrl || `${this.getWindowOrigin()}/ws`;
    this.client = new Client({
      webSocketFactory: () => new SockJS(url) as unknown as WebSocket,
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        this.connected = true;
      },
      onDisconnect: () => {
        this.connected = false;
      },
    });
    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.connected = false;
  }

  /**
   * Subscribe to dashboard refresh events (order.created / order.status.updated).
   * Callback is invoked when the backend signals that stats/order list should be refetched.
   * Use a debounced callback to avoid excessive refetches during bulk ingestion.
   */
  subscribeDashboardRefresh(callback: () => void): () => void {
    if (!this.client) this.connect();
    const dest = '/topic/dashboard/refresh';
    let subscriptionId: string | null = null;

    const trySubscribe = () => {
      if (!this.client?.connected) {
        setTimeout(trySubscribe, 200);
        return;
      }
      subscriptionId = this.client.subscribe(dest, () => {
        callback();
      }).id;
    };

    trySubscribe();

    return () => {
      if (subscriptionId && this.client?.connected) {
        this.client.unsubscribe(subscriptionId);
      }
    };
  }

  subscribeToOrder(orderId: string, callback: (update: OrderStatusUpdate) => void): () => void {
    if (!this.client) this.connect();
    const dest = `/topic/orders/${orderId}`;
    let subscriptionId: string | null = null;

    const trySubscribe = () => {
      if (!this.client?.connected) {
        setTimeout(trySubscribe, 200);
        return;
      }
      subscriptionId = this.client.subscribe(dest, (msg) => {
        try {
          const body = JSON.parse(msg.body) as OrderStatusUpdate;
          this.statusUpdates$.next(body);
          callback(body);
        } catch {
          // ignore parse errors
        }
      }).id;
    };

    trySubscribe();

    return () => {
      if (subscriptionId && this.client?.connected) {
        this.client.unsubscribe(subscriptionId);
      }
    };
  }

  isConnected(): boolean {
    return this.connected;
  }

  private getWindowOrigin(): string {
    if (typeof window !== 'undefined' && window.location) {
      const { protocol, hostname, port } = window.location;
      const p = port && port !== '80' && port !== '443' ? `:${port}` : '';
      return `${protocol}//${hostname}${p}`;
    }
    return 'http://localhost:8089';
  }
}
