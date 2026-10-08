/**
 * 태그 오류 코드 → 문구 (008 data-model §2-2, 002 `TOO_MANY_TAGS`). 서버 문구와 같다(끝 마침표 없음).
 * 서버가 보낸 `message`가 있으면 그것을 우선한다.
 */
export const TAG_MESSAGES: Record<string, string> = {
  INVALID_TAG: '쓸 수 없는 글자가 있어요',
  TAG_TOO_LONG: '태그는 30자까지 쓸 수 있어요',
  TAG_BANNED_WORD: '쓸 수 없는 단어가 들어 있어요',
  TOO_MANY_TAGS: '태그가 너무 많아요',
};

export function tagMessage(code: string, serverMessage?: string): string {
  return serverMessage || TAG_MESSAGES[code] || '태그를 확인해 주세요';
}
