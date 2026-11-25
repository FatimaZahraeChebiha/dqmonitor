export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'LOGIN_BLOCKED'
  | 'USER_REGISTERED'
  | 'USER_CREATED'
  | 'USER_DELETED'
  | 'PASSWORD_CHANGED'
  | 'PASSWORD_RESET_REQUESTED'
  | 'PASSWORD_RESET_COMPLETED'
  | 'RULE_CREATED'
  | 'RULE_UPDATED'
  | 'RULE_DELETED'
  | 'DATASET_UPLOADED'
  | 'DATASET_DELETED'
  | 'DATASET_ANALYZED'
  | 'DATABASE_RESET';

export interface AuditLog {
  id: number;
  actorEmail: string | null;
  action: AuditAction;
  targetType: string | null;
  targetId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}
