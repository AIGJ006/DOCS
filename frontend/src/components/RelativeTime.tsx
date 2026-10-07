/**
 * 상대 시간·날짜 (005 FR-012, research R-29). 1분 미만 "방금 전", 1시간 미만 "N분 전", 24시간 미만 "N시간 전",
 * 그 이후 `YYYY.MM.DD`(한국 시간). `<time datetime>`에는 UTC ISO-8601을 담는다.
 */

const SEOUL = 'Asia/Seoul';

const DATE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const FULL = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** `2026.10.02` (한국 시간). */
export function formatDate(value: string | Date): string {
  const parts = DATE.formatToParts(new Date(value));
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  return `${get('year')}.${get('month')}.${get('day')}`;
}

/** `10월 3일` (한국 시간) — "수정됨 · 10월 3일". */
export function formatMonthDay(value: string | Date): string {
  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL,
    month: 'long',
    day: 'numeric',
  }).formatToParts(new Date(value));
  const month = parts.find((part) => part.type === 'month')?.value ?? '';
  const day = parts.find((part) => part.type === 'day')?.value ?? '';
  return `${month} ${day}일`;
}

/** `10월 3일 14:03` (한국 시간) — 작성자 안내의 저장 시각. */
export function formatDateTime(value: string | Date): string {
  const date = new Date(value);
  const time = new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
  return `${formatMonthDay(date)} ${time}`;
}

/** 사람이 읽는 문구. `now`는 테스트·고정 시각용. */
export function relativeText(value: string | Date, now: Date = new Date()): string {
  const date = new Date(value);
  const diffMinutes = Math.floor((now.getTime() - date.getTime()) / 60_000);
  if (diffMinutes < 1) {
    return '방금 전';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}분 전`;
  }
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}시간 전`;
  }
  return formatDate(date);
}

export interface RelativeTimeProps {
  /** UTC ISO-8601 시각 */
  value: string;
  className?: string;
}

export default function RelativeTime({ value, className }: RelativeTimeProps) {
  const date = new Date(value);
  return (
    <time
      dateTime={date.toISOString()}
      title={FULL.format(date).replace(/\.\s/g, '. ')}
      className={className}
    >
      {relativeText(date)}
    </time>
  );
}
