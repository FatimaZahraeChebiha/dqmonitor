export type RuleType = 'NOT_NULL' | 'UNIQUE' | 'REGEX' | 'RANGE' | 'ENUM' | 'REQUIRED_COLUMN';

export interface ValidationRule {
  id: number;
  ruleName: string;
  columnName: string;
  ruleType: RuleType;
  ruleValue: string | null;
  isActive: boolean;
  description: string | null;
  createdAt: string;
}

export interface ValidationRuleRequest {
  ruleName: string;
  columnName: string;
  ruleType: RuleType;
  ruleValue: string | null;
  isActive: boolean;
  description: string | null;
}
