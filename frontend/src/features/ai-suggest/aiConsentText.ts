/**
 * AI 외부 전송 동의 문구와 버전 (013 research R10, 34 §7-2, Clarifications Q1).
 *
 * 버전은 서버 `blog.agreement.ai.version` 기본값과 같아야 한다 — 다르면 배포 오류다(동의하면 400). 화면 시험
 * (`AiConsentDialog.test.tsx`)이 이 상수를, 서버 시험(`TagSuggestPropertiesBindingTest`)이 기본값을 같은 값으로 검사한다.
 * 문구를 바꾸면 두 값을 함께 올린다(다음 추천 때 모든 회원이 다시 동의).
 */
export const AI_CONSENT_VERSION = '2026-10-08';

export const AI_CONSENT_TITLE = 'AI 태그 추천을 쓰기 전에 확인해 주세요';

export const AI_CONSENT_LINES: readonly string[] = [
  '글의 제목과 본문 앞부분이 외부 AI 서비스(Google Gemini, 무료 등급)로 전송돼요.',
  'Google이 받은 내용을 서비스 개선에 쓰고, 사람이 검토할 수 있어요.',
  '외부 AI를 쓸 수 없을 때는 우리 서버의 자체 AI로 처리하고, 그때는 외부로 보내지 않아요.',
  '비공개·친구 공개 글은 외부로 보내지 않아요.',
  '개인정보·비밀번호·회사 기밀이 든 글에는 쓰지 말아 주세요.',
];

export const AI_CONSENT_AGREE = '동의하고 추천받기';
export const AI_CONSENT_CANCEL = '취소';
