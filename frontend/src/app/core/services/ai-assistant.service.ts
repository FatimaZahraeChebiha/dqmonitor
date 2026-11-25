import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiConversationDetail, AiConversationReply, AiConversationSummary } from '../models/ai-chat.model';

@Injectable({ providedIn: 'root' })
export class AiAssistantService {
  private readonly baseUrl = `${environment.apiUrl}/api/ai/conversations`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<AiConversationSummary[]> {
    return this.http.get<AiConversationSummary[]>(this.baseUrl);
  }

  create(): Observable<AiConversationSummary> {
    return this.http.post<AiConversationSummary>(this.baseUrl, {});
  }

  get(id: number): Observable<AiConversationDetail> {
    return this.http.get<AiConversationDetail>(`${this.baseUrl}/${id}`);
  }

  rename(id: number, title: string): Observable<AiConversationSummary> {
    return this.http.patch<AiConversationSummary>(`${this.baseUrl}/${id}`, { title });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  sendMessage(id: number, message: string): Observable<AiConversationReply> {
    return this.http.post<AiConversationReply>(`${this.baseUrl}/${id}/messages`, { message });
  }
}
