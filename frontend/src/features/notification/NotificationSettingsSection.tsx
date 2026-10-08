import { useEffect, useRef, useState } from 'react';
import { getNotificationSettings, putNotificationSettings } from '../../api/notifications';
import type { MutableNotificationType, NotificationSettings } from '../../api/types/notification';
import './notification.css';

const LABELS: [MutableNotificationType, string][] = [
  ['COMMENT', '내 글에 달린 댓글'],
  ['REPLY', '내 댓글에 달린 답글'],
  ['LIKE', '좋아요'],
  ['FOLLOW', '새 팔로워'],
  ['NEW_POST', '팔로우한 사람의 새 글'],
];

const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

/**
 * 설정 화면 "알림" 칸 (011 T051, US6, FR-033·FR-035, research R16).
 *
 * - 스위치 5개(`role="switch"`). 바꿀 때마다 다섯 값 전체를 `PUT`한다.
 * - 빠르게 여러 번 바꾸면 요청마다 번호를 매겨 마지막 요청의 결과만 반영한다 — 화면과 서버 모두 마지막 상태가 남는다.
 * - 실패하면 마지막으로 저장된 상태로 되돌리고 "잠시 후 다시 시도해 주세요".
 */
export default function NotificationSettingsSection() {
  const [settings, setSettings] = useState<NotificationSettings | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const saved = useRef<NotificationSettings | null>(null);
  const sequence = useRef(0);

  useEffect(() => {
    let active = true;
    getNotificationSettings()
      .then((value) => {
        if (active) {
          saved.current = value;
          setSettings(value);
        }
      })
      .catch(() => active && setLoadFailed(true));
    return () => {
      active = false;
    };
  }, []);

  const toggle = (type: MutableNotificationType) => {
    if (!settings) {
      return;
    }
    const next = { ...settings, [type]: !settings[type] };
    setSettings(next);
    setError(null);
    const mine = ++sequence.current;
    putNotificationSettings(next)
      .then((result) => {
        saved.current = result;
        if (mine === sequence.current) {
          setSettings(result);
        }
      })
      .catch(() => {
        if (mine === sequence.current) {
          setSettings(saved.current);
          setError(FAILED_MESSAGE);
        }
      });
  };

  return (
    <section aria-labelledby="notification-settings-title">
      <h2 id="notification-settings-title">알림</h2>
      {loadFailed ? (
        <p role="alert">알림 설정을 불러오지 못했어요. 새로 고쳐 주세요</p>
      ) : !settings ? (
        <p>불러오는 중이에요</p>
      ) : (
        <ul className="notification-settings-list">
          {LABELS.map(([type, label]) => (
            <li key={type}>
              <span id={`notification-setting-${type}`}>{label}</span>
              <button
                type="button"
                role="switch"
                className="notification-switch"
                aria-checked={settings[type]}
                aria-labelledby={`notification-setting-${type}`}
                onClick={() => toggle(type)}
              />
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      <p className="field-help">운영 알림(신고 결과·숨김)은 끌 수 없어요</p>
    </section>
  );
}
