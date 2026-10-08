import { useCallback, useState, type ReactNode } from 'react';
import Toast, { type ToastMessage } from './Toast';

/** `const { show, toast } = useToast()` — 새 알림이 오면 앞의 알림을 바꾼다. */
export function useToast(): { show: (message: ToastMessage) => void; toast: ReactNode } {
  const [message, setMessage] = useState<ToastMessage | null>(null);
  const close = useCallback(() => setMessage(null), []);
  const show = useCallback((next: ToastMessage) => setMessage(next), []);
  return { show, toast: message ? <Toast message={message} onClose={close} /> : null };
}
