/**
 * 날짜·시각 문구 (005 FR-012·030·039, research R-29). 표시는 한국 시간(Asia/Seoul), `<time datetime>`은 UTC ISO-8601.
 */
const SEOUL = 'Asia/Seoul';

const DATE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const MONTH_DAY = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  month: 'long',
  day: 'numeric',
});

const HOUR_MINUTE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
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

function part(
  formatter: Intl.DateTimeFormat,
  value: Date,
  type: Intl.DateTimeFormatPartTypes,
): string {
  return formatter.formatToParts(value).find((p) => p.type === type)?.value ?? '';
}

/** `2026.10.02` */
export function formatDate(value: string | Date): string {
  const date = new Date(value);
  return `${part(DATE, date, 'year')}.${part(DATE, date, 'month')}.${part(DATE, date, 'day')}`;
}

/** `10월 3일` — "수정됨 · 10월 3일" */
export function formatMonthDay(value: string | Date): string {
  const date = new Date(value);
  return `${part(MONTH_DAY, date, 'month')} ${part(MONTH_DAY, date, 'day')}일`;
}

/** `10월 3일 14:03` — 작성자 안내의 저장 시각 */
export function formatDateTime(value: string | Date): string {
  const date = new Date(value);
  return `${formatMonthDay(date)} ${HOUR_MINUTE.format(date)}`;
}

/** 마우스를 올리면 보이는 전체 시각 `2026년 10월 2일 23:03` */
export function formatFull(value: string | Date): string {
  return FULL.format(new Date(value)).replace(/\.\s/g, '. ');
}

/** 1분 미만 "방금 전", 1시간 미만 "N분 전", 24시간 미만 "N시간 전", 그 이후 `YYYY.MM.DD` */
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
