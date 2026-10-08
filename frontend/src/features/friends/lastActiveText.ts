import type { LastActive } from '../../api/friends';

/** 최근 활동 구간 문구 (R-26). 서버가 구간만 보내므로 정확한 시각은 화면에도 없다. 모르는 구간이면 null. */
export function lastActiveText(value: LastActive | undefined | null): string | null {
  switch (value?.bucket) {
    case 'TODAY':
      return '오늘';
    case 'YESTERDAY':
      return '어제';
    case 'DAYS_AGO':
      return typeof value.days === 'number' ? `${value.days}일 전` : null;
    case 'OVER_A_WEEK':
      return '1주 이상';
    default:
      return null;
  }
}
