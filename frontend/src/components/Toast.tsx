type ToastKind = 'info' | 'success' | 'error';

interface Props {
  kind?: ToastKind;
  message: string;
}

export function Toast({ kind = 'info', message }: Props) {
  return <div className={`toast toast-${kind}`}>{message}</div>;
}
