export interface Order {
  orderId: string;
  status: string;
  customerId: string;
  totalAmount: number;
  items: OrderItem[];
  statusHistory: StatusHistoryEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderItem {
  id: string;
  productId: string;
  quantity: number;
  unitPrice: number;
}

export interface StatusHistoryEntry {
  fromStatus: string | null;
  toStatus: string;
  changedAt: string;
}

export interface CreateOrderRequest {
  customerId: string;
  idempotencyKey: string;
  items: { productId: string; quantity: number; price: number }[];
}

export interface CreateOrderResponse {
  orderId: string;
  status: string;
}

export interface OrderStatusUpdate {
  orderId: string;
  fromStatus?: string;
  toStatus: string;
  timestamp?: string;
  [key: string]: unknown;
}

export interface DashboardStats {
  totalOrders: number;
  ordersToday: number;
  pendingCount: number;
  processingCount: number;
  shippedCount: number;
  byStatus: Record<string, number>;
}

/** Processing Service dashboard stats (saga / orders processed). */
export interface ProcessingStats {
  ordersProcessedToday: number;
  pendingCount: number;
  processingCount: number;
  shippedCount: number;
}

/** Notification Service dashboard stats (live connections, events). */
export interface NotificationStats {
  connectedWebSockets: number;
  connectedSse: number;
  eventsDeliveredTotal: number;
}
