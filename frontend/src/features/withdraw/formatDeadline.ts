/**
 * 복구 기한 문구 (015 R12, 44 §2·§3). 표시는 한국 시간(Asia/Seoul) "2026년 11월 7일 오후 3:20".
 */
const DEADLINE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

const DAY_MS = 24 * 60 * 60 * 1000;

/** "2026년 11월 7일 오후 3:20" */
export function formatDeadline(iso: string): string {
  const parts = DEADLINE.formatToParts(new Date(iso));
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  return `${get('year')}년 ${get('month')} ${get('day')}일 ${get('dayPeriod')} ${get('hour')}:${get('minute')}`;
}

/** 기한까지 남은 날(올림). 기한이 지났으면 0. */
export function remainingDays(iso: string, now: Date = new Date()): number {
  const diff = new Date(iso).getTime() - now.getTime();
  return diff <= 0 ? 0 : Math.ceil(diff / DAY_MS);
}
