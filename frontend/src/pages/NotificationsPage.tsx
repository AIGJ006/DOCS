import { useCallback, useEffect, useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { deleteNotification, listNotifications, markAllRead } from '../api/notifications';
import type { NotificationItem as Item, NotificationPage } from '../api/types/notification';
import LoadMoreButton from '../components/LoadMoreButton';
import { loginPathFor } from '../features/auth-gate/authGate';
import { useSession } from '../features/auth/useSession';
import NotificationItem from '../features/notification/NotificationItem';
import { EMPTY_TEXT, LOAD_FAILED_TEXT } from '../features/notification/NotificationDropdown';
import { announceNotificationsChanged } from '../features/notification/useUnreadCount';
import '../features/notification/notification.css';

export const NOTIFICATIONS_PATH = '/notifications';
export const PAGE_SIZE = 20;

/**
 * 알림 화면 `/notifications` (011 T032, FR-029·FR-030·FR-031, research R16). 로그인 전용.
 *
 * - 20개씩, [더 보기]는 마지막 응답의 `nextCursor`를 그대로 보낸다. 이어 붙일 때 이미 있는 번호는 건너뛴다(FR-029).
 *   005 `useCursorList`는 글 카드 전용 타입이라 같은 규칙을 이 화면 안에서 작게 다시 쓴다.
 * - 항목마다 [×](`aria-label="알림 삭제"`) — 지우면 목록에서 바로 뺀다. 위에 [모두 읽음].
 * - 읽음·삭제 뒤에는 종에게 다시 확인하라고 알린다.
 */
export default function NotificationsPage() {
  const { loading, me } = useSession();
  if (loading) {
    return <main data-route="notifications" aria-busy="true" className="notification-page" />;
  }
  if (!me) {
    return <Navigate to={loginPathFor(NOTIFICATIONS_PATH)} replace />;
  }
  return <Notifications />;
}

type Status = 'idle' | 'loading' | 'error';

function Notifications() {
  const [items, setItems] = useState<Item[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadedOnce, setLoadedOnce] = useState(false);
  const [status, setStatus] = useState<Status>('loading');
  const [deleting, setDeleting] = useState<number | null>(null);
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const inFlight = useRef(false);

  const receive = useCallback((page: NotificationPage) => {
    setItems((previous) => {
      const seen = new Set(previous.map((item) => item.id));
      return [...previous, ...page.items.filter((item) => !seen.has(item.id))];
    });
    setNextCursor(page.nextCursor);
    setLoadedOnce(true);
    setStatus('idle');
  }, []);

  const fetchPage = useCallback(
    async (cursor: string | null) => {
      if (inFlight.current) {
        return;
      }
      inFlight.current = true;
      setStatus('loading');
      try {
        receive(await listNotifications({ size: PAGE_SIZE, cursor }));
      } catch {
        setStatus('error');
      } finally {
        inFlight.current = false;
      }
    },
    [receive],
  );

  // 첫 페이지 (상태는 응답 콜백에서만 바꾼다)
  useEffect(() => {
    let cancelled = false;
    inFlight.current = true;
    listNotifications({ size: PAGE_SIZE })
      .then((page) => !cancelled && receive(page))
      .catch(() => !cancelled && setStatus('error'))
      .finally(() => {
        inFlight.current = false;
      });
    return () => {
      cancelled = true;
    };
  }, [receive]);

  const onRead = (id: number) => {
    setItems((previous) =>
      previous.map((item) => (item.id === id ? { ...item, read: true } : item)),
    );
    announceNotificationsChanged();
  };

  const onDelete = async (id: number) => {
    setDeleting(id);
    setMessage(null);
    try {
      await deleteNotification(id);
      setItems((previous) => previous.filter((item) => item.id !== id));
      announceNotificationsChanged();
    } catch {
      setMessage({ kind: 'error', text: '잠시 후 다시 시도해 주세요' });
    } finally {
      setDeleting(null);
    }
  };

  const onReadAll = async () => {
    setMessage(null);
    try {
      const result = await markAllRead();
      setItems((previous) => previous.map((item) => ({ ...item, read: true })));
      setMessage({ kind: 'ok', text: `알림 ${result.updated}개를 읽음으로 바꿨어요` });
      announceNotificationsChanged();
    } catch {
      setMessage({ kind: 'error', text: '잠시 후 다시 시도해 주세요' });
    }
  };

  const done = loadedOnce && nextCursor === null;
  const initialError = status === 'error' && !loadedOnce;

  return (
    <main data-route="notifications" className="notification-page">
      <div className="notification-page-toolbar">
        <h1>알림</h1>
        <button type="button" onClick={() => void onReadAll()}>
          모두 읽음
        </button>
      </div>
      {message && <p role={message.kind === 'error' ? 'alert' : 'status'}>{message.text}</p>}
      {items.length > 0 && (
        <ul className="notification-list">
          {items.map((item) => (
            <NotificationItem
              key={item.id}
              item={item}
              onRead={onRead}
              onDelete={(id) => void onDelete(id)}
              deleting={deleting === item.id}
            />
          ))}
        </ul>
      )}
      {!loadedOnce && status === 'loading' && (
        <p className="notification-panel-status">불러오는 중…</p>
      )}
      {initialError ? (
        <p role="status" className="notification-panel-status">
          {LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void fetchPage(null)}>
            다시 시도
          </button>
        </p>
      ) : loadedOnce && items.length === 0 && done ? (
        <p className="notification-panel-status">{EMPTY_TEXT}</p>
      ) : done ? null : (
        loadedOnce && (
          <LoadMoreButton
            status={status}
            done={false}
            onLoadMore={() => void fetchPage(nextCursor)}
            onRetry={() => void fetchPage(nextCursor)}
          />
        )
      )}
    </main>
  );
}
