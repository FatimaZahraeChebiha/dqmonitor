import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoModule } from '@jsverse/transloco';
import { CreateUserRequest, Role } from '../../../core/models/user.model';

@Component({
  selector: 'app-create-user-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    TranslocoModule,
  ],
  templateUrl: './create-user-dialog.html',
  styleUrl: './create-user-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateUserDialog {
  readonly roles: Role[] = ['USER', 'ADMIN'];

  form: CreateUserRequest = { username: '', email: '', password: '', role: 'USER' };

  constructor(private readonly dialogRef: MatDialogRef<CreateUserDialog, CreateUserRequest>) {}

  get isValid(): boolean {
    return !!this.form.username && !!this.form.email && this.form.password.length >= 8;
  }

  save(): void {
    if (!this.isValid) return;
    this.dialogRef.close(this.form);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
