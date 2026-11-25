import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { User, UserStats } from '../../core/models/user.model';
import { AuditLog } from '../../core/models/audit-log.model';
import { UserService } from '../../core/services/user.service';
import { AdminService } from '../../core/services/admin.service';
import { AuthService } from '../../core/services/auth.service';
import { AuditLogService } from '../../core/services/audit-log.service';
import { NotificationService } from '../../core/services/notification.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { LanguageService } from '../../core/services/language.service';
import { CreateUserDialog } from './create-user-dialog/create-user-dialog';

const EMPTY_STATS: UserStats = { total: 0, admins: 0, users: 0 };

@Component({
  selector: 'app-administration',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatPaginatorModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './administration.html',
  styleUrl: './administration.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Administration implements OnInit {
  users = signal<User[]>([]);
  usersTotal = signal(0);
  usersPage = signal(0);
  usersPageSize = signal(20);
  stats = signal<UserStats>(EMPTY_STATS);

  auditLogs = signal<AuditLog[]>([]);
  auditLogsTotal = signal(0);
  auditLogsPage = signal(0);
  auditLogsPageSize = signal(25);

  constructor(
    readonly auth: AuthService,
    readonly language: LanguageService,
    private readonly userService: UserService,
    private readonly adminService: AdminService,
    private readonly auditLogService: AuditLogService,
    private readonly notification: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly dialog: MatDialog,
    private readonly transloco: TranslocoService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadStats();
    this.loadAuditLogs();
  }

  load(event?: PageEvent): void {
    const page = event ? event.pageIndex : this.usersPage();
    const size = event ? event.pageSize : this.usersPageSize();
    this.userService.getPage(page, size).subscribe((result) => {
      this.users.set(result.content);
      this.usersTotal.set(result.totalElements);
      this.usersPage.set(result.page);
      this.usersPageSize.set(result.size);
    });
  }

  loadStats(): void {
    this.userService.getStats().subscribe((stats) => this.stats.set(stats));
  }

  loadAuditLogs(event?: PageEvent): void {
    const page = event ? event.pageIndex : this.auditLogsPage();
    const size = event ? event.pageSize : this.auditLogsPageSize();
    this.auditLogService.getPage(page, size).subscribe((result) => {
      this.auditLogs.set(result.content);
      this.auditLogsTotal.set(result.totalElements);
      this.auditLogsPage.set(result.page);
      this.auditLogsPageSize.set(result.size);
    });
  }

  openCreateUser(): void {
    this.dialog
      .open(CreateUserDialog, { width: '440px' })
      .afterClosed()
      .subscribe((request) => {
        if (!request) return;
        this.userService.create(request).subscribe(() => {
          this.notification.success('admin.createUserSuccess');
          this.usersPage.set(0);
          this.load();
          this.loadStats();
        });
      });
  }

  deleteUser(user: User): void {
    this.confirmDialog
      .open({
        title: this.transloco.translate('admin.deleteUserConfirmTitle'),
        message: this.transloco.translate('admin.deleteUserConfirmMessage', { name: user.username }),
        danger: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.userService.delete(user.id).subscribe(() => {
          this.notification.success('admin.deleteUserSuccess');
          this.load();
          this.loadStats();
        });
      });
  }

  resetDatabase(): void {
    this.confirmDialog
      .open({
        title: this.transloco.translate('admin.dangerZone.confirmTitle'),
        message: this.transloco.translate('admin.dangerZone.confirmMessage'),
        danger: true,
        requireTypedConfirmation: 'RESET',
        typedConfirmationPlaceholder: this.transloco.translate('admin.dangerZone.confirmPlaceholder'),
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.adminService.resetDatabase().subscribe(() => {
          this.notification.success('admin.dangerZone.success');
          this.loadAuditLogs();
        });
      });
  }

  auditActionClass(action: AuditLog['action']): string {
    if (action.includes('FAILURE') || action.includes('BLOCKED') || action === 'DATABASE_RESET' || action.includes('DELETED')) {
      return 'error';
    }
    if (action.includes('SUCCESS') || action.includes('CREATED') || action.includes('COMPLETED')) {
      return 'success';
    }
    return 'neutral';
  }
}
