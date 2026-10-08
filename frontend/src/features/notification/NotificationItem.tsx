import { useNavigate } from 'react-router-dom';
import { markRead } from '../../api/notifications';
import type { NotificationItem as Item } from '../../api/types/notification';
import RelativeTime from '../../components/RelativeTime';
import { notificationSegments } from './notificationText';

export interface NotificationItemProps {
  item: Item;
  /** 읽음으로 바꿨을 때 (목록·안 읽은 수를 바로 맞춘다) */
  onRead: (id: number) => void;
  /** 주면 오른쪽에 [×](`aria-label="알림 삭제"`)를 둔다 — 알림 화면 */
  onDelete?: (id: number) => void;
  deleting?: boolean;
}

/**
 * 알림 한 줄 (011 T032, FR-029·FR-031, Clarifications Q3).
 *
 * - 안 읽음은 ● + 굵은 문장(`<strong>`). 닉네임은 늘 `<strong>`(탈퇴한 사용자는 굵게 하지 않음).
 * - 누르면 안 읽은 알림은 `PUT …/read`를 보낸 뒤(실패해도) `url`이 있으면 그리로 이동하고, 없으면 그 자리에서 읽음 표시만 바꾼다.
 * - 댓글 알림 주소는 `?comment={id}#comment-{id}` — 007 댓글 영역이 그 댓글로 내려가 2초 동안 테두리로 강조한다.
 */
export default function NotificationItem({
  item,
  onRead,
  onDelete,
  deleting,
}: NotificationItemProps) {
  const navigate = useNavigate();

  const onClick = async () => {
    if (!item.read) {
      try {
        await markRead(item.id);
      } catch {
        // 실패해도 이동은 한다. 안 읽은 수는 다음 확인에서 맞춘다
      }
      onRead(item.id);
    }
    if (item.url) {
      navigate(item.url);
    }
  };

  const segments = notificationSegments(item).map((segment, index) =>
    segment.strong ? (
      <strong key={index}>{segment.text}</strong>
    ) : (
      <span key={index}>{segment.text}</span>
    ),
  );
  const Text = item.read ? 'span' : 'strong';

  return (
    <li
      data-testid="notification-item"
      className={item.read ? 'notification-item' : 'notification-item notification-item-unread'}
    >
      <button type="button" className="notification-item-main" onClick={() => void onClick()}>
        {!item.read && (
          <span className="notification-dot" aria-hidden="true">
            ●
          </span>
        )}
        <span className="notification-item-body">
          {!item.read && <span className="notification-sr-only">안 읽음 </span>}
          <Text data-testid="notification-text" className="notification-text">
            {segments}
          </Text>
          <RelativeTime value={item.updatedAt} className="notification-time" />
        </span>
      </button>
      {onDelete && (
        <button
          type="button"
          className="notification-delete"
          aria-label="알림 삭제"
          disabled={deleting}
          onClick={() => onDelete(item.id)}
        >
          ×
        </button>
      )}
    </li>
  );
}
