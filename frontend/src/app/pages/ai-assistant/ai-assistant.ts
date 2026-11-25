import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnInit, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AiAssistantService } from '../../core/services/ai-assistant.service';
import { NotificationService } from '../../core/services/notification.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { AiChatMessage, AiConversationSummary } from '../../core/models/ai-chat.model';

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslocoModule,
  ],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiAssistant implements OnInit {
  @ViewChild('scrollAnchor') private scrollAnchor?: ElementRef<HTMLDivElement>;

  readonly conversations = signal<AiConversationSummary[]>([]);
  /** Null while composing a brand-new conversation that doesn't exist server-side yet. */
  readonly activeId = signal<number | null>(null);
  readonly messages = signal<AiChatMessage[]>([]);
  readonly isSending = signal(false);
  readonly isLoadingConversation = signal(false);
  readonly editingId = signal<number | null>(null);
  draft = '';
  editTitle = '';

  constructor(
    private readonly aiAssistantService: AiAssistantService,
    private readonly notification: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly transloco: TranslocoService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.aiAssistantService.list().subscribe((conversations) => {
      this.conversations.set(conversations);
      if (conversations.length > 0) {
        this.open(conversations[0].id);
      }
    });
  }

  displayTitle(conversation: AiConversationSummary): string {
    return conversation.title ?? this.transloco.translate('aiAssistant.untitled');
  }

  newConversation(): void {
    if (this.isSending()) {
      return;
    }
    this.activeId.set(null);
    this.messages.set([]);
    this.editingId.set(null);
  }

  open(id: number): void {
    if (this.isSending() || id === this.activeId()) {
      return;
    }
    this.editingId.set(null);
    this.isLoadingConversation.set(true);
    this.aiAssistantService.get(id).subscribe({
      next: (detail) => {
        this.activeId.set(detail.id);
        this.messages.set(detail.messages);
        this.isLoadingConversation.set(false);
        this.scrollToBottom();
      },
      error: () => this.isLoadingConversation.set(false),
    });
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || this.isSending()) {
      return;
    }

    this.draft = '';
    this.isSending.set(true);
    this.messages.update((current) => [...current, { role: 'user', content: text }]);
    this.cdr.markForCheck();
    this.scrollToBottom();

    const id = this.activeId();
    if (id === null) {
      this.aiAssistantService.create().subscribe({
        next: (created) => {
          this.conversations.update((list) => [created, ...list]);
          this.activeId.set(created.id);
          this.deliver(created.id, text);
        },
        error: () => this.abortSend(text),
      });
    } else {
      this.deliver(id, text);
    }
  }

  startRename(conversation: AiConversationSummary, event: Event): void {
    event.stopPropagation();
    this.editTitle = conversation.title ?? '';
    this.editingId.set(conversation.id);
  }

  cancelRename(): void {
    this.editingId.set(null);
  }

  commitRename(conversation: AiConversationSummary): void {
    if (this.editingId() !== conversation.id) {
      return;
    }
    const title = this.editTitle.trim();
    this.editingId.set(null);
    if (!title || title === conversation.title) {
      return;
    }
    this.aiAssistantService.rename(conversation.id, title).subscribe((updated) => {
      this.replaceInList(updated);
      this.notification.success('aiAssistant.renameSuccess');
    });
  }

  remove(conversation: AiConversationSummary, event: Event): void {
    event.stopPropagation();
    this.confirmDialog
      .open({
        title: this.transloco.translate('aiAssistant.deleteConfirmTitle'),
        message: this.transloco.translate('aiAssistant.deleteConfirmMessage', { title: this.displayTitle(conversation) }),
        danger: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.aiAssistantService.delete(conversation.id).subscribe(() => {
          this.conversations.update((list) => list.filter((c) => c.id !== conversation.id));
          this.notification.success('aiAssistant.deleteSuccess');
          if (this.activeId() === conversation.id) {
            this.activeId.set(null);
            this.messages.set([]);
          }
          this.cdr.markForCheck();
        });
      });
  }

  private deliver(id: number, text: string): void {
    this.aiAssistantService.sendMessage(id, text).subscribe({
      next: (response) => {
        this.messages.update((current) => [...current, { role: 'assistant', content: response.reply }]);
        const bumped: AiConversationSummary = { id, title: response.title, updatedAt: new Date().toISOString() };
        this.conversations.update((list) => [bumped, ...list.filter((c) => c.id !== id)]);
        this.isSending.set(false);
        this.scrollToBottom();
      },
      error: () => this.abortSend(text),
    });
  }

  /** Undoes the optimistic user message so a failed send can simply be retried. */
  private abortSend(text: string): void {
    this.messages.update((current) => current.slice(0, -1));
    this.draft = text;
    this.isSending.set(false);
    this.cdr.markForCheck();
  }

  private replaceInList(updated: AiConversationSummary): void {
    this.conversations.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
  }

  private scrollToBottom(): void {
    queueMicrotask(() => {
      this.scrollAnchor?.nativeElement.scrollIntoView?.({ behavior: 'smooth' });
    });
  }
}
