/**
 * 내 글 관리 목록의 표시 계산 (006 T045, FR-007·008·011, research R23). 날짜는 한국 시간(Asia/Seoul) 기준이다.
 */
import type { PostStatus } from '../../api/posts';
import { formatDate, formatDateTime, formatMonthDay } from '../time/dateFormat';

const SEOUL_DAY = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** 한국 날짜의 일 번호 (UTC 자정 기준 일 수) */
function seoulDayNumber(value: string | Date): number {
  const [year, month, day] = SEOUL_DAY.format(new Date(value)).split('-').map(Number);
  return Math.round(Date.UTC(year, month - 1, day) / 86_400_000);
}

/** 완전 삭제까지 남은 날 = 한국 날짜 차이 (`purgeAt` 날짜 − 오늘 날짜). 0 이하면 오늘 밤 배치 대상이다. */
export function daysUntilPurge(purgeAt: string, now: Date = new Date()): number {
  return seoulDayNumber(purgeAt) - seoulDayNumber(now);
}

/** "27일 뒤 완전 삭제" / 남은 날이 0 이하면 "곧 완전 삭제" */
export function purgeText(purgeAt: string, now: Date = new Date()): string {
  const days = daysUntilPurge(purgeAt, now);
  return days > 0 ? `${days}일 뒤 완전 삭제` : '곧 완전 삭제';
}

/** 임시글 마지막 저장: 1분 미만 "방금 전", 24시간 이내 "N분 전"/"N시간 전", 그 밖 "10월 3일 14:03" (FR-007) */
export function formatSavedAt(value: string, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - new Date(value).getTime()) / 60_000);
  if (minutes < 1) {
    return '방금 전';
  }
  if (minutes < 60) {
    return `${minutes}분 전`;
  }
  if (minutes < 24 * 60) {
    return `${Math.floor(minutes / 60)}시간 전`;
  }
  return formatDateTime(value);
}

/** "발행 2026.10.01" + 다시 발행했으면 " · 수정됨 10월 3일" (FR-008) */
export function formatPublished(publishedAt: string | null, editedAt: string | null): string {
  if (!publishedAt) {
    return '';
  }
  const published = `발행 ${formatDate(publishedAt)}`;
  return editedAt ? `${published} · 수정됨 ${formatMonthDay(editedAt)}` : published;
}

/** 휴지통 줄의 원래 상태 (FR-011) */
export function originalStatusLabel(status: PostStatus): string {
  return status === 'DRAFT' ? '(임시글이었음)' : '(발행 글이었음)';
}

/** 휴지통 줄 "삭제 10월 2일" */
export function formatDeletedOn(deletedAt: string): string {
  return `삭제 ${formatMonthDay(deletedAt)}`;
}
