export type AiChatRole = 'user' | 'assistant';

export interface AiChatMessage {
  role: AiChatRole;
  content: string;
}

export interface AiConversationSummary {
  id: number;
  /** Null until the first message names the conversation. */
  title: string | null;
  updatedAt: string;
}

export interface AiConversationDetail {
  id: number;
  title: string | null;
  messages: AiChatMessage[];
}

export interface AiConversationReply {
  reply: string;
  title: string | null;
}
