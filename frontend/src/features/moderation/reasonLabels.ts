/**
 * 신고·숨김 사유 코드 → 화면 이름 (014 Clarifications의 6개). 011 알림 문장과 014 신고·운영 화면이 같이 쓴다
 * (먼저 만든 쪽이 011 — 014는 이 파일을 그대로 가져다 쓴다).
 */
export type ReasonCode = 'SPAM' | 'ABUSE' | 'SEXUAL' | 'PRIVACY' | 'COPYRIGHT' | 'OTHER';

export const REASON_LABELS: Record<ReasonCode, string> = {
  SPAM: '스팸·광고',
  ABUSE: '욕설·혐오',
  SEXUAL: '음란·선정',
  PRIVACY: '개인정보 노출',
  COPYRIGHT: '저작권 침해',
  OTHER: '기타',
};

/** 모르는 코드나 빈 값은 "기타"로 보인다. */
export function reasonLabel(code: string | null | undefined): string {
  return code && code in REASON_LABELS ? REASON_LABELS[code as ReasonCode] : REASON_LABELS.OTHER;
}
