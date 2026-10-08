import { useEffect, useId, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import NotificationDropdown from './NotificationDropdown';
import { useUnreadCount } from './useUnreadCount';
import './notification.css';

/** 배지 숫자: 100 이상은 "99+" */
function badgeText(count: number): string {
  return count >= 100 ? '99+' : String(count);
}

/**
 * 머리말 알림 종 (011 T032, FR-026, research R16). 로그인했을 때만 `SiteHeader`가 그린다.
 *
 * - 이름: 안 읽은 게 있으면 "안 읽은 알림 N개", 없으면 "알림". 배지는 0이면 숨기고 100 이상은 "99+".
 * - 누르면 펼침 목록(`NotificationDropdown`)을 연다. Esc·바깥 누름·화면 이동이면 닫히고, Esc면 초점이 종으로 돌아온다.
 */
export default function NotificationBell() {
  const unread = useUnreadCount(true);
  const location = useLocation();
  const panelId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  // 연 화면의 location.key — 다른 화면으로 옮기면 저절로 닫힌다(계정 메뉴와 같은 방식).
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt !== null && openAt === location.key;

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpenAt(null);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenAt(null);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const label = unread.count > 0 ? `안 읽은 알림 ${unread.count}개` : '알림';

  return (
    <div className="notification-bell" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="notification-bell-button"
        aria-label={label}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => setOpenAt(open ? null : location.key)}
      >
        <svg
          aria-hidden="true"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unread.count > 0 && (
          <span className="notification-badge" data-testid="notification-badge" aria-hidden="true">
            {badgeText(unread.count)}
          </span>
        )}
      </button>
      {open && (
        <NotificationDropdown
          id={panelId}
          onRead={() => unread.decrease(1)}
          onReadAll={() => unread.clear()}
        />
      )}
    </div>
  );
}
