import type { PostCard, PostCardPage } from './reading';

/**
 * 트렌딩·검색 응답 타입 (012 contracts/openapi.yaml 그대로).
 */

/** 트렌딩 한 페이지 — 005 `PostCardPage`와 같은 모양. */
export type TrendingPage = PostCardPage;

/**
 * 검색어 주변 문장. 원문 그대로(서식 기호 포함), 앞뒤가 잘렸으면 `…`.
 * `marks`는 강조 범위 `[시작, 끝)` — UTF-16 단위(JS 문자열 색인), 겹치지 않고 오름차순.
 */
export interface Snippet {
  text: string;
  marks: [number, number][];
}

/** 글 검색 결과 카드 = 005 카드 + 주변 문장. */
export interface PostSearchItem extends PostCard {
  snippet: Snippet;
}

/** 2글자 단어가 있으면 "두 글자 단어는 제목·태그에서만 찾았어요". */
export type SearchNotice = 'TWO_CHAR_TITLE_TAG_ONLY';

export interface PostSearchPage {
  items: PostSearchItem[];
  nextCursor: string | null;
  notice: SearchNotice | null;
}

export type SearchSort = 'relevance' | 'latest';

/** 사람 검색 결과 한 명. */
export interface PersonItem {
  handle: string;
  nickname: string;
  profileImageUrl: string | null;
  bioFirstLine: string | null;
}

export interface PeopleSearchResult {
  items: PersonItem[];
}
