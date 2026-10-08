/**
 * 알림 API (011 contracts/openapi.yaml). 요청은 001 `client.ts`를 쓴다(CSRF 헤더·오류 본문).
 *
 * 알림 번호가 없거나 남의 것이면 404지만 공통 404 화면으로 바꾸지 않는다 — 목록에서 빼기만 한다(`notFoundScreen: false`).
 */
import { apiDelete, apiGet, apiPost, apiPut } from './client';
import type {
  NotificationPage,
  NotificationSettings,
  ReadAllResult,
  UnreadCount,
} from './types/notification';

/** 펼침 목록 10개, 알림 화면 20개 (서버는 이 두 값만 받는다). */
export type NotificationPageSize = 10 | 20;

export function getUnreadCount(): Promise<UnreadCount> {
  return apiGet<UnreadCount>('/api/notifications/unread-count', { notFoundScreen: false });
}

export function listNotifications({
  size,
  cursor,
}: {
  size: NotificationPageSize;
  cursor?: string | null;
}): Promise<NotificationPage> {
  const query = cursor ? `size=${size}&cursor=${encodeURIComponent(cursor)}` : `size=${size}`;
  return apiGet<NotificationPage>(`/api/notifications?${query}`, { notFoundScreen: false });
}

export function markRead(id: number): Promise<void> {
  return apiPut<void>(`/api/notifications/${id}/read`, undefined, { notFoundScreen: false });
}

export function markAllRead(): Promise<ReadAllResult> {
  return apiPost<ReadAllResult>('/api/notifications/read-all');
}

export function deleteNotification(id: number): Promise<void> {
  return apiDelete<void>(`/api/notifications/${id}`, undefined, { notFoundScreen: false });
}

export function getNotificationSettings(): Promise<NotificationSettings> {
  return apiGet<NotificationSettings>('/api/me/notification-settings');
}

export function putNotificationSettings(
  settings: NotificationSettings,
): Promise<NotificationSettings> {
  return apiPut<NotificationSettings>('/api/me/notification-settings', settings);
}
