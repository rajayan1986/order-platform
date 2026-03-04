import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, of, map } from 'rxjs';
import { Order } from '../models/order.model';
import { CreateOrderRequest, CreateOrderResponse, DashboardStats, ProcessingStats, NotificationStats } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private baseUrl = '/api/orders';

  constructor(private http: HttpClient) {}

  setBaseUrl(url: string): void {
    this.baseUrl = url.replace(/\/$/, '') + '/orders';
  }

  getOrders(page = 0, size = 10): Observable<{ content: Order[]; totalElements: number }> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<{ content: Order[]; totalElements: number }>(this.baseUrl, { params }).pipe(
      catchError(() => of({ content: [], totalElements: 0 }))
    );
  }

  getOrder(id: string): Observable<Order | null> {
    return this.http.get<Order>(`${this.baseUrl}/${id}`).pipe(
      catchError(() => of(null))
    );
  }

  createOrder(request: CreateOrderRequest): Observable<CreateOrderResponse | { error: string }> {
    return this.http.post<CreateOrderResponse>(this.baseUrl, request, { observe: 'response' }).pipe(
      map((res) => (res.status === 202 || res.status === 200) && res.body ? res.body : { error: 'No body' }),
      catchError((err) => {
        const message = err.error?.message || err.statusText || 'Failed to create order';
        return of({ error: message } as { error: string });
      })
    );
  }

  getDashboardStats(): Observable<DashboardStats | null> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/stats`).pipe(
      catchError(() => of(null))
    );
  }

  /** Processing Service stats (saga / orders processed). Via Gateway: /processing/stats */
  getProcessingStats(): Observable<ProcessingStats | null> {
    return this.http.get<ProcessingStats>('/processing/stats').pipe(
      catchError(() => of(null))
    );
  }

  /** Notification Service stats (WebSocket/SSE connections, events). Via Gateway: /notify-api/stats */
  getNotificationStats(): Observable<NotificationStats | null> {
    return this.http.get<NotificationStats>('/notify-api/stats').pipe(
      catchError(() => of(null))
    );
  }
}
