import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AiAssistant } from './ai-assistant';
import { environment } from '../../../environments/environment';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { NotificationService } from '../../core/services/notification.service';

const BASE = `${environment.apiUrl}/api/ai/conversations`;

describe('AiAssistant', () => {
  let component: AiAssistant;
  let fixture: ComponentFixture<AiAssistant>;
  let httpMock: HttpTestingController;
  let confirmOpen: ReturnType<typeof vi.fn>;
  let notificationSuccess: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    confirmOpen = vi.fn().mockReturnValue(of(true));
    notificationSuccess = vi.fn();

    await TestBed.configureTestingModule({
      imports: [AiAssistant, getTranslocoTestingModule()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ConfirmDialogService, useValue: { open: confirmOpen } },
        { provide: NotificationService, useValue: { success: notificationSuccess, error: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AiAssistant);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function init(conversations: unknown[] = []): void {
    fixture.detectChanges();
    httpMock.expectOne(BASE).flush(conversations);
  }

  it('should create', () => {
    init();
    expect(component).toBeTruthy();
  });

  it('opens the most recent stored conversation on load', () => {
    init([
      { id: 2, title: 'Recent', updatedAt: '2026-09-24T10:00:00' },
      { id: 1, title: 'Old', updatedAt: '2026-09-23T10:00:00' },
    ]);

    httpMock.expectOne(`${BASE}/2`).flush({
      id: 2,
      title: 'Recent',
      messages: [
        { role: 'user', content: 'Q' },
        { role: 'assistant', content: 'A' },
      ],
    });

    expect(component.activeId()).toBe(2);
    expect(component.messages().length).toBe(2);
  });

  it('creates a conversation on the first message, then sends it and lists it first', () => {
    init();
    component.draft = 'How is data quality overall?';

    component.send();

    expect(component.messages()).toEqual([{ role: 'user', content: 'How is data quality overall?' }]);
    expect(component.isSending()).toBe(true);
    expect(component.draft).toBe('');

    const create = httpMock.expectOne(BASE);
    expect(create.request.method).toBe('POST');
    create.flush({ id: 5, title: null, updatedAt: '2026-09-24T10:00:00' });

    const send = httpMock.expectOne(`${BASE}/5/messages`);
    expect(send.request.body).toEqual({ message: 'How is data quality overall?' });
    send.flush({ reply: 'Quality looks good.', title: 'How is data quality overall?' });

    expect(component.isSending()).toBe(false);
    expect(component.activeId()).toBe(5);
    expect(component.messages()).toEqual([
      { role: 'user', content: 'How is data quality overall?' },
      { role: 'assistant', content: 'Quality looks good.' },
    ]);
    expect(component.conversations()[0].title).toBe('How is data quality overall?');
  });

  it('does not send an empty or whitespace-only draft', () => {
    init();
    component.draft = '   ';

    component.send();

    expect(component.messages()).toEqual([]);
    httpMock.expectNone(BASE);
  });

  it('restores the draft and removes the optimistic message when sending fails', () => {
    init([{ id: 1, title: 'T', updatedAt: '2026-09-24T10:00:00' }]);
    httpMock.expectOne(`${BASE}/1`).flush({ id: 1, title: 'T', messages: [] });
    component.draft = 'Will fail';

    component.send();
    httpMock.expectOne(`${BASE}/1/messages`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(component.messages()).toEqual([]);
    expect(component.draft).toBe('Will fail');
    expect(component.isSending()).toBe(false);
  });

  it('starts a fresh conversation without touching the stored ones', () => {
    init([{ id: 1, title: 'T', updatedAt: '2026-09-24T10:00:00' }]);
    httpMock.expectOne(`${BASE}/1`).flush({ id: 1, title: 'T', messages: [{ role: 'user', content: 'Q' }] });

    component.newConversation();

    expect(component.activeId()).toBeNull();
    expect(component.messages()).toEqual([]);
    expect(component.conversations().length).toBe(1);
  });

  it('renames a conversation', () => {
    init([{ id: 1, title: 'Old', updatedAt: '2026-09-24T10:00:00' }]);
    httpMock.expectOne(`${BASE}/1`).flush({ id: 1, title: 'Old', messages: [] });
    const conversation = component.conversations()[0];

    component.startRename(conversation, new Event('click'));
    component.editTitle = 'New name';
    component.commitRename(conversation);

    const req = httpMock.expectOne(`${BASE}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ title: 'New name' });
    req.flush({ id: 1, title: 'New name', updatedAt: '2026-09-24T10:00:00' });

    expect(component.conversations()[0].title).toBe('New name');
    expect(notificationSuccess).toHaveBeenCalledWith('aiAssistant.renameSuccess');
  });

  it('deletes the active conversation after confirmation and clears the chat', () => {
    init([{ id: 1, title: 'T', updatedAt: '2026-09-24T10:00:00' }]);
    httpMock.expectOne(`${BASE}/1`).flush({ id: 1, title: 'T', messages: [{ role: 'user', content: 'Q' }] });

    component.remove(component.conversations()[0], new Event('click'));

    const req = httpMock.expectOne(`${BASE}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(component.conversations()).toEqual([]);
    expect(component.activeId()).toBeNull();
    expect(component.messages()).toEqual([]);
  });

  it('does not delete when the confirmation is declined', () => {
    confirmOpen.mockReturnValue(of(false));
    init([{ id: 1, title: 'T', updatedAt: '2026-09-24T10:00:00' }]);
    httpMock.expectOne(`${BASE}/1`).flush({ id: 1, title: 'T', messages: [] });

    component.remove(component.conversations()[0], new Event('click'));

    httpMock.expectNone(`${BASE}/1`);
    expect(component.conversations().length).toBe(1);
  });
});
