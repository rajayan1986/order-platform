import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../services/auth.service';

const AUTH_URL = '/api/auth/token';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  error = '';

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.nonNullable.group({
      username: ['', Validators.required],
      password: ['', Validators.required],
    });
  }

  onSubmit(): void {
    this.error = '';
    if (this.form.invalid) return;
    this.loading = true;
    const { username, password } = this.form.getRawValue();
    this.http
      .post<{ token: string }>(AUTH_URL, { username, password })
      .subscribe({
        next: (res) => {
          const token = res?.token;
          if (!token) {
            this.loading = false;
            this.error = 'Invalid response from server (no token). Check CORS if using a different origin.';
            return;
          }
          this.auth.setToken(token);
          this.loading = false;
          this.router.navigate(['/dashboard']);
        },
        error: () => {
          this.loading = false;
          this.error = 'Invalid username or password';
        },
        complete: () => {
          this.loading = false;
        },
      });
  }
}
