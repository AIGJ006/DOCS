import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import './dialogs.css';

/** 알림 한 건: 글자 + 선택적 링크 버튼 1개 (예: "복구했어요 [발행 글 탭에서 보기]"). */
export interface ToastMessage {
  text: string;
  action?: { label: string; to: string };
}

export const TOAST_DURATION_MS = 5_000;

interface ToastProps {
  message: ToastMessage;
  onClose: () => void;
  durationMs?: number;
}

/** 알림 메시지 (006 T013). `role="status"`로 읽어 주고 5초 뒤 스스로 닫힌다. 글자는 텍스트로만 렌더링한다. */
export default function Toast({ message, onClose, durationMs = TOAST_DURATION_MS }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onClose, durationMs);
    return () => clearTimeout(timer);
  }, [message, onClose, durationMs]);

  return (
    <div className="app-toast" role="status">
      <span>{message.text}</span>
      {message.action ? (
        <Link to={message.action.to} onClick={onClose}>
          {message.action.label}
        </Link>
      ) : null}
    </div>
  );
}
