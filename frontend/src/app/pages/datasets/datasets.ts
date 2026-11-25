import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { Dataset, DatasetStatus } from '../../core/models/dataset.model';
import { DatasetService } from '../../core/services/dataset.service';
import { NotificationService } from '../../core/services/notification.service';
import { LanguageService } from '../../core/services/language.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';

const POLL_DELAYS_MS = [2000, 3000, 5000, 8000, 10000];
const MAX_POLL_ATTEMPTS = 24; // ~ a few minutes of backoff before giving up

@Component({
  selector: 'app-datasets',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatPaginatorModule,
    TranslocoModule,
    LocalizedDatePipe,
  ],
  templateUrl: './datasets.html',
  styleUrl: './datasets.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Datasets implements OnInit, OnDestroy {
  datasets = signal<Dataset[]>([]);
  total = signal(0);
  page = signal(0);
  pageSize = signal(10);
  selectedFile = signal<File | null>(null);
  isDragging = signal(false);
  isUploading = signal(false);
  private destroyed = false;

  constructor(
    readonly language: LanguageService,
    private readonly datasetService: DatasetService,
    private readonly notification: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly transloco: TranslocoService,
  ) {}

  ngOnInit(): void {
    this.loadDatasets();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
  }

  loadDatasets(event?: PageEvent): void {
    const page = event ? event.pageIndex : this.page();
    const size = event ? event.pageSize : this.pageSize();
    this.datasetService.getPage(page, size).subscribe((result) => {
      this.datasets.set(result.content);
      this.total.set(result.totalElements);
      this.page.set(result.page);
      this.pageSize.set(result.size);
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave(): void {
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.selectedFile.set(file);
    }
  }

  removeSelectedFile(): void {
    this.selectedFile.set(null);
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }

    this.isUploading.set(true);
    this.datasetService.upload(file).subscribe({
      next: () => {
        this.isUploading.set(false);
        this.selectedFile.set(null);
        this.notification.success('datasets.uploadSuccess');
        this.page.set(0);
        this.loadDatasets();
      },
      error: () => {
        this.isUploading.set(false);
      },
    });
  }

  analyze(dataset: Dataset): void {
    this.datasetService.analyze(dataset.id).subscribe({
      next: () => {
        this.notification.success('datasets.analyzeStarted');
        this.setLocalStatus(dataset.id, 'PROCESSING');
        this.pollStatus(dataset.id);
      },
    });
  }

  delete(dataset: Dataset): void {
    this.confirmDialog
      .open({
        title: this.transloco.translate('datasets.deleteConfirmTitle'),
        message: this.transloco.translate('datasets.deleteConfirmMessage', { name: dataset.fileName }),
        danger: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.datasetService.delete(dataset.id).subscribe(() => {
          this.notification.success('datasets.deleteSuccess');
          this.loadDatasets();
        });
      });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private setLocalStatus(id: number, status: DatasetStatus): void {
    this.datasets.update((list) => list.map((d) => (d.id === id ? { ...d, status } : d)));
  }

  /**
   * Backoff polling replaces the original fixed "5 attempts over 15 seconds"
   * design, which silently gave up long before a real Spark job typically
   * finishes and left the UI stuck showing PROCESSING forever.
   */
  private pollStatus(id: number, attempt = 0): void {
    if (this.destroyed || attempt >= MAX_POLL_ATTEMPTS) {
      return;
    }
    const delay = POLL_DELAYS_MS[Math.min(attempt, POLL_DELAYS_MS.length - 1)];

    setTimeout(() => {
      if (this.destroyed) return;
      this.datasetService.getById(id).subscribe((updated) => {
        this.datasets.update((list) => list.map((d) => (d.id === id ? updated : d)));
        if (updated.status === 'PROCESSING' || updated.status === 'PENDING') {
          this.pollStatus(id, attempt + 1);
        }
      });
    }, delay);
  }
}
