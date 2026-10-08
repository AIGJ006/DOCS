/**
 * 알림 응답 모양 (011 contracts/openapi.yaml `NotificationItem`, data-model §5).
 * 문장은 서버가 만들지 않는다 — `features/notification/notificationText.ts`가 조립한다.
 */
export type NotificationType =
  'COMMENT' | 'REPLY' | 'LIKE' | 'FOLLOW' | 'NEW_POST' | 'REPORT_RESOLVED' | 'CONTENT_HIDDEN';

/** 끌 수 있는 5종. 설정 API의 키 이름과 같다. */
export type MutableNotificationType = 'COMMENT' | 'REPLY' | 'LIKE' | 'FOLLOW' | 'NEW_POST';

export type NotificationActor =
  { handle: string; nickname: string; profileImageUrl: string | null } | { withdrawn: true };

export type NotificationPost = { title: string; url: string } | { unavailable: true };

export interface NotificationItem {
  id: number;
  type: NotificationType;
  read: boolean;
  /** UTC ISO-8601 */
  updatedAt: string;
  /** 운영 알림은 null */
  actor: NotificationActor | null;
  /** 묶음 인원 − 1, 그 밖 0 */
  othersCount: number;
  post: NotificationPost | null;
  comment: { id: number; preview: string } | null;
  report: { result: 'ACTION_TAKEN' | 'NO_VIOLATION' } | null;
  hidden: { targetType: 'POST' | 'COMMENT'; stillHidden: boolean; reason: string | null } | null;
  /** 이동할 곳. 없으면 null — 누르면 읽음만 바뀐다 */
  url: string | null;
}

export interface NotificationPage {
  items: NotificationItem[];
  nextCursor: string | null;
}

export interface UnreadCount {
  count: number;
}

export interface ReadAllResult {
  updated: number;
}

export type NotificationSettings = Record<MutableNotificationType, boolean>;
