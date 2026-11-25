import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslocoService } from '@jsverse/transloco';

/**
 * MatPaginator's labels ("Items per page", "of", next/previous tooltips) come
 * from MatPaginatorIntl, which Angular Material otherwise hardcodes in English
 * regardless of the app's active language - every other string in this app
 * goes through Transloco, so the paginator was a visible gap. Re-reads the
 * translations on every language change and calls `changes.next()`, which is
 * exactly the extension point MatPaginatorIntl is designed for.
 */
@Injectable({ providedIn: 'root' })
export class TranslocoPaginatorIntl extends MatPaginatorIntl {
  constructor(private readonly transloco: TranslocoService) {
    super();
    this.updateLabels();
    this.transloco.langChanges$.subscribe(() => this.updateLabels());
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return this.transloco.translate('paginator.rangeLabel', { start: 0, end: 0, length });
    }
    const start = page * pageSize + 1;
    const end = Math.min(start + pageSize - 1, length);
    return this.transloco.translate('paginator.rangeLabel', { start, end, length });
  };

  private updateLabels(): void {
    this.itemsPerPageLabel = this.transloco.translate('paginator.itemsPerPage');
    this.nextPageLabel = this.transloco.translate('paginator.nextPage');
    this.previousPageLabel = this.transloco.translate('paginator.previousPage');
    this.firstPageLabel = this.transloco.translate('paginator.firstPage');
    this.lastPageLabel = this.transloco.translate('paginator.lastPage');
    this.changes.next();
  }
}
