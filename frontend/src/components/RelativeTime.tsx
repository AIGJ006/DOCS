import { formatFull, relativeText } from '../features/time/dateFormat';

/**
 * 상대 시간·날짜 (005 FR-012, research R-29). 문구 규칙은 `features/time/dateFormat.ts`에 있다.
 */
export interface RelativeTimeProps {
  /** UTC ISO-8601 시각 */
  value: string;
  className?: string;
}

export default function RelativeTime({ value, className }: RelativeTimeProps) {
  const date = new Date(value);
  return (
    <time dateTime={date.toISOString()} title={formatFull(date)} className={className}>
      {relativeText(date)}
    </time>
  );
}
