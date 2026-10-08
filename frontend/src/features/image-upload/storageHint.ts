/**
 * 사진 저장 공간 표시 도우미 (003 US4, FR-014). 단위는 1024 기준 KB·MB·GB, 소수 한 자리(끝의 .0은 뺀다).
 */
import type { StorageUsage } from '../../api/types/images';

const KB = 1024;
const MB = KB * 1024;
const GB = MB * 1024;

/** 이 비율을 넘으면 에디터에 남은 공간을 알린다. */
export const HINT_RATIO = 0.9;

function oneDecimal(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

export function formatBytes(bytes: number): string {
  if (bytes >= GB) return `${oneDecimal(bytes / GB)}GB`;
  if (bytes >= MB) return `${oneDecimal(bytes / MB)}MB`;
  return `${oneDecimal(bytes / KB)}KB`;
}

/** 사용률 (0~100 정수). */
export function usagePercent(usage: StorageUsage): number {
  if (usage.quotaBytes <= 0) return 100;
  return Math.min(100, Math.max(0, Math.round((usage.usedBytes / usage.quotaBytes) * 100)));
}

/** 90%를 넘으면 "남은 공간 약 100MB", 아니면 null. */
export function storageHint(usage: StorageUsage | null): string | null {
  if (!usage || usage.usedBytes <= usage.quotaBytes * HINT_RATIO) {
    return null;
  }
  return `남은 공간 약 ${formatBytes(Math.max(0, usage.quotaBytes - usage.usedBytes))}`;
}
