/** 검색 화면 문구 (012 spec FR-024·FR-035·FR-037, research R15). 끝에 마침표 없음. */
export const SEARCH_TOO_SHORT_TEXT = '두 글자 이상 입력해 주세요';
export const SEARCH_TWO_CHAR_NOTICE_TEXT = '두 글자 단어는 제목·태그에서만 찾았어요';
export const SEARCH_RATE_LIMITED_TEXT = '잠시 후 다시 시도해 주세요';
export const SEARCH_FAILED_TEXT = '검색하지 못했어요';

export function noPostsText(q: string): string {
  return `'${q}'에 대한 글이 없어요`;
}

export function noPeopleText(q: string): string {
  return `'${q}'에 대한 사람이 없어요`;
}

/** 머리말 검색창 이름 */
export const SEARCH_BOX_LABEL = '검색';
/** 블로그 머리말 검색창 이름 */
export const BLOG_SEARCH_BOX_LABEL = '이 블로그에서 검색';
