/** 013 AI 태그 추천 API 타입 (contracts/openapi.yaml). */

/** GEMINI = 외부 AI(무료 등급), OLLAMA = 자체 서버 AI */
export type Provider = 'GEMINI' | 'OLLAMA';

export interface TagSuggestRequest {
  title: string;
  contentMd: string;
  /** 지금 붙인 태그 (저장 안 된 것 포함) */
  currentTags: string[];
  /** [다시 추천] — 같은 글 비슷한 내용 재사용을 건너뛴다 */
  refresh: boolean;
}

export interface TagSuggestResponse {
  /** 정규화·검사 뒤 0~5개 */
  tags: string[];
  provider: Provider;
  /** 재사용 저장소로 답함 (오늘 횟수가 줄지 않음) */
  cached: boolean;
  /** true면 "본문 앞부분을 보고 추천했어요" */
  truncated: boolean;
  remainingToday: number;
}

export interface TagSuggestStatus {
  /** false면 추천 영역을 그리지 않는다 */
  available: boolean;
  /** 누르면 동의 창을 먼저 띄운다 */
  consentRequired: boolean;
  consentVersion: string;
  /** 지금 고를 공급자 예측 (실제는 추천 응답의 provider) */
  provider: Provider | null;
  remainingToday: number;
}

/** `GET·PUT·DELETE /api/me/agreements/ai` */
export interface AiConsent {
  agreed: boolean;
  version: string | null;
  currentVersion: string;
  agreedAt: string | null;
}
