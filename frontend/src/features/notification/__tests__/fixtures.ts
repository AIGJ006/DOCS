import type { NotificationItem } from '../../../api/types/notification';

/** 시험용 알림 한 개. 기본은 안 읽은 댓글 알림. */
export function notification(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 1,
    type: 'COMMENT',
    read: false,
    updatedAt: new Date(Date.now() - 5 * 60_000).toISOString(),
    actor: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
    othersCount: 0,
    post: { title: '첫 글', url: '/@na_ms/posts/7' },
    comment: { id: 3, preview: '좋은 글이에요' },
    report: null,
    hidden: null,
    url: '/@na_ms/posts/7?comment=3#comment-3',
    ...overrides,
  };
}
