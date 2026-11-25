import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import { ConfirmDialog } from './confirm-dialog';
import { ConfirmDialogData } from './confirm-dialog.model';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  constructor(private readonly dialog: MatDialog) {}

  open(data: ConfirmDialogData): Observable<boolean> {
    const ref = this.dialog.open(ConfirmDialog, { data, width: '440px' });
    return ref.afterClosed().pipe(map((result) => result === true));
  }
}
