export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  /** If set, the confirm button stays disabled until the user types this exact text. */
  requireTypedConfirmation?: string;
  typedConfirmationPlaceholder?: string;
  typedConfirmationMismatchLabel?: string;
}
