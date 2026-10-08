import { useCallback, useState } from 'react';
import { ApiError } from '../../api/client';
import { createReport } from '../../api/reports';
import type { ReportReason, TargetType } from '../../api/types/moderation';

export const REPORT_TEXT = {
  button: '신고',
  submit: '신고하기',
  submitting: '보내는 중…',
  cancel: '취소',
  title: (type: TargetType) => (type === 'POST' ? '글 신고하기' : '댓글 신고하기'),
  detailLabel: '설명',
  detailPlaceholder: '어떤 문제인지 적어 주세요',
  accepted: '신고가 접수됐어요. 검토 후 처리할게요',
  notFound: (type: TargetType) => (type === 'POST' ? '볼 수 없는 글이에요' : '볼 수 없는 댓글이에요'),
  retryLater: '잠시 후 다시 시도해 주세요',
} as const;

export const DETAIL_MAX = 200;

/** 코드 포인트 수 (서버와 같은 기준 — 이모지 1자). */
export function countChars(text: string): number {
  return Array.from(text).length;
}

export type ReportOutcome = 'accepted' | 'failed' | 'gated';

/**
 * 신고 보내기 (014 T024). 결과: 접수 → `'accepted'`, 401·403(계정 상태) → `onGate`가 처리하면 `'gated'`, 그 밖 → 창 안 문구.
 */
export function useReport(
  targetType: TargetType,
  targetId: number,
  onGate: (error: unknown) => boolean,
) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = useCallback(
    async (reason: ReportReason, detail: string): Promise<ReportOutcome> => {
      setSubmitting(true);
      setError(null);
      try {
        await createReport({ targetType, targetId, reason, detail });
        return 'accepted';
      } catch (caught) {
        if (onGate(caught)) {
          return 'gated';
        }
        setError(messageFor(caught, targetType));
        return 'failed';
      } finally {
        setSubmitting(false);
      }
    },
    [onGate, targetId, targetType],
  );

  return { submit, submitting, error };
}

function messageFor(error: unknown, targetType: TargetType): string {
  if (!(error instanceof ApiError)) {
    return REPORT_TEXT.retryLater;
  }
  if (error.status === 404) {
    return REPORT_TEXT.notFound(targetType);
  }
  if (error.status === 429 || error.status >= 500) {
    return REPORT_TEXT.retryLater;
  }
  if (error.status === 400) {
    return error.errors[0]?.message ?? error.message;
  }
  return error.message || REPORT_TEXT.retryLater;
}
