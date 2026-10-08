/** AI 태그 추천 화면 문구 (013 research R13). 끝에 마침표를 붙이지 않는다. */
export const AI_MESSAGES = {
  tooShort: '글을 조금 더 쓴 뒤 추천받아 보세요',
  empty: '추천할 태그를 찾지 못했어요',
  unavailable: '지금은 추천할 수 없어요',
  busy: '잠시 후 다시 시도해 주세요',
  dailyLimit: '오늘 추천을 모두 썼어요. 내일 다시 써 보세요',
  full: '태그를 더 붙일 수 없어요',
  loading: '추천 중…',
  loadingOllama: '자체 AI로 추천 중이라 조금 걸려요',
  aiNote: 'AI 제안이에요',
  truncatedNote: '본문 앞부분을 보고 추천했어요 · AI 제안이에요',
  consentChanged: '동의 문구가 바뀌었어요. 새로 고친 뒤 다시 시도해 주세요',
  remaining: (n: number) => `오늘 남은 추천 ${n}회`,
} as const;
