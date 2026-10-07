import type { AutosaveStatus } from './autosaveQueue';

/**
 * 저장 상태 문구 (002 T086, FR-013). 시각은 서버 `savedAt`을 서울 시각 `HH:MM`으로 보인다.
 */
const TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function formatSavedAt(savedAt: string): string {
  const date = new Date(savedAt);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const parts = TIME.formatToParts(date);
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '';
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '';
  return `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
}

export function statusText(status: AutosaveStatus): string {
  switch (status.kind) {
    case 'saved':
      return `✓ 저장됨 ${formatSavedAt(status.savedAt)}`.trim();
    case 'local':
      return '● 이 기기에 저장됨 (동기화 대기)';
    case 'offline':
      return '⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화';
    case 'conflict':
      return '⚠ 다른 곳에서 수정됨 — 이 기기에만 저장 중';
    case 'error':
      return `⚠ ${status.message}`;
  }
}
