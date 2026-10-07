import type { FieldError } from '../../api/client';

/** 서버 `errors[]`를 칸 이름별로 묶는다. 한 칸에 여러 오류가 올 수 있다. */
export function groupFieldErrors(errors: FieldError[]): Record<string, FieldError[]> {
  const grouped: Record<string, FieldError[]> = {};
  for (const error of errors) {
    (grouped[error.field] ??= []).push(error);
  }
  return grouped;
}

/**
 * 서버 문구 끝의 `[로그인] [비밀번호 찾기]` 같은 버튼 표기를 떼어 낸다. 화면은 그 자리에 실제 링크를 그린다.
 */
export function stripActionLabels(message: string): string {
  return message.replace(/(\.?\s*\[[^\]]+\])+\s*$/, '').trim();
}

/** `Retry-After` 초를 "42초"·"약 3분"으로. */
export function formatWait(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}초`;
  }
  return `약 ${Math.ceil(seconds / 60)}분`;
}
