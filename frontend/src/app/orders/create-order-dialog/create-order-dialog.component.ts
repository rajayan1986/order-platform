import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OrderService } from '../../services/order.service';
import { CreateOrderRequest, CreateOrderResponse } from '../../models/order.model';

function uuidv4(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

@Component({
  selector: 'app-create-order-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-order-dialog.component.html',
  styleUrls: ['./create-order-dialog.component.css'],
})
export class CreateOrderDialogComponent {
  private fb = inject(FormBuilder);
  private orderService = inject(OrderService);
  private snackBar = inject(MatSnackBar);
  private dialogRef = inject(MatDialogRef<CreateOrderDialogComponent>);

  form: FormGroup;
  loading = false;
  error = '';

  constructor() {
    this.form = this.fb.nonNullable.group({
      customerId: ['', Validators.required],
      productId: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unitPrice: [0, [Validators.required, Validators.min(0.01)]],
    });
  }

  onSubmit(): void {
    this.error = '';
    if (this.form.invalid) return;
    this.loading = true;
    const { customerId, productId, quantity, unitPrice } = this.form.getRawValue();
    const request: CreateOrderRequest = {
      customerId,
      idempotencyKey: uuidv4(),
      items: [{ productId, quantity, price: unitPrice }],
    };
    this.orderService.createOrder(request).subscribe({
      next: (res: CreateOrderResponse | { error: string }) => {
        if ('error' in res) {
          this.error = res.error;
          this.loading = false;
          return;
        }
        this.loading = false;
        this.snackBar.open(`Order created: ${res.orderId}`, 'Close', { duration: 5000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.error = 'Request failed';
        this.loading = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
