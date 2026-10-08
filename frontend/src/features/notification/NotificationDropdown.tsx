import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listNotifications, markAllRead } from '../../api/notifications';
import type { NotificationItem as Item } from '../../api/types/notification';
import NotificationItem from './NotificationItem';

export const DROPDOWN_SIZE = 10;
export const EMPTY_TEXT = '새 알림이 없어요';
export const LOAD_FAILED_TEXT = '알림을 불러오지 못했어요';

type State = { kind: 'loading' } | { kind: 'error' } | { kind: 'ready'; items: Item[] };

export interface NotificationDropdownProps {
  id: string;
  /** 알림 하나를 읽음으로 바꿨을 때 */
  onRead: () => void;
  /** [모두 읽음]이 성공했을 때 */
  onReadAll: () => void;
}

/**
 * 종 아래 펼침 목록 (011 T032, FR-027·FR-028, research R16). 열 때마다(마운트마다) 최근 10개를 새로 받는다.
 * 실패해도 배지는 건드리지 않는다. 아래에 [모두 읽음]·[모든 알림 보기].
 */
export default function NotificationDropdown({ id, onRead, onReadAll }: NotificationDropdownProps) {
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(() => {
    setState({ kind: 'loading' });
    return listNotifications({ size: DROPDOWN_SIZE })
      .then((page) => setState({ kind: 'ready', items: page.items }))
      .catch(() => setState({ kind: 'error' }));
  }, []);

  useEffect(() => {
    let cancelled = false;
    listNotifications({ size: DROPDOWN_SIZE })
      .then((page) => !cancelled && setState({ kind: 'ready', items: page.items }))
      .catch(() => !cancelled && setState({ kind: 'error' }));
    return () => {
      cancelled = true;
    };
  }, []);

  const markOne = (itemId: number) => {
    setState((previous) =>
      previous.kind === 'ready'
        ? {
            kind: 'ready',
            items: previous.items.map((item) =>
              item.id === itemId ? { ...item, read: true } : item,
            ),
          }
        : previous,
    );
    onRead();
  };

  const readAll = async () => {
    setBusy(true);
    setMessage(null);
    try {
      await markAllRead();
      setState((previous) =>
        previous.kind === 'ready'
          ? { kind: 'ready', items: previous.items.map((item) => ({ ...item, read: true })) }
          : previous,
      );
      onReadAll();
    } catch {
      setMessage('잠시 후 다시 시도해 주세요');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div id={id} className="notification-panel" role="region" aria-label="알림 목록">
      {state.kind === 'loading' && <p className="notification-panel-status">불러오는 중…</p>}
      {state.kind === 'error' && (
        <p className="notification-panel-status" role="status">
          {LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void load()}>
            다시 시도
          </button>
        </p>
      )}
      {state.kind === 'ready' &&
        (state.items.length === 0 ? (
          <p className="notification-panel-status">{EMPTY_TEXT}</p>
        ) : (
          <ul className="notification-list">
            {state.items.map((item) => (
              <NotificationItem key={item.id} item={item} onRead={markOne} />
            ))}
          </ul>
        ))}
      {message && (
        <p role="alert" className="notification-panel-error">
          {message}
        </p>
      )}
      <div className="notification-panel-footer">
        <button type="button" onClick={() => void readAll()} disabled={busy}>
          모두 읽음
        </button>
        <Link to="/notifications">모든 알림 보기</Link>
      </div>
    </div>
  );
}
