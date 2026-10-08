import { normalizeTagQuery } from '../tag/normalizeTag';

/**
 * 검색어 정리 — 서버 `SearchQueryParser`와 같은 규칙 (012 research R6·R10). 화면은 요청 전에 같은 규칙으로
 * "두 글자 이상 입력해 주세요"를 보인다(남는 단어가 없으면 요청하지 않는다).
 *
 * 글: NFC → 앞뒤 공백 → 앞 50 코드 포인트 → 공백으로 나눔 → 1 코드 포인트 단어 버림 → 앞 5단어.
 * 사람: NFC → 앞뒤 공백 → 앞 50 코드 포인트 → 모든 공백 제거 → 맨 앞 `@` 하나 제거 → 2 코드 포인트 미만이면 없음.
 */
export const SEARCH_MAX_LENGTH = 50;
export const SEARCH_MAX_WORDS = 5;

export interface ParsedQuery {
  words: string[];
  /** 2글자 단어가 있다 — 제목·태그에서만 찾는다 */
  hasTwoCharWord: boolean;
}

function codePoints(value: string): number {
  return [...value].length;
}

function clip(raw: string): string {
  const trimmed = (raw ?? '').normalize('NFC').trim();
  return [...trimmed].slice(0, SEARCH_MAX_LENGTH).join('');
}

export function parseQuery(raw: string): ParsedQuery {
  const words = clip(raw)
    .split(/\s+/u)
    .filter((word) => codePoints(word) >= 2)
    .slice(0, SEARCH_MAX_WORDS);
  return { words, hasTwoCharWord: words.some((word) => codePoints(word) === 2) };
}

/** 사람 검색어. 남는 글자가 2 코드 포인트 미만이면 null. */
export function parsePeopleQuery(raw: string): string | null {
  const token = clip(raw).replace(/\s+/gu, '').replace(/^@/, '');
  return codePoints(token) >= 2 ? token : null;
}

/**
 * `#태그` 이동 판정 (FR-025): 입력 전체가 `#`으로 시작하고 공백이 없고 008 정규화가 통과하면 태그 이름, 아니면 null
 * (보통 검색 — 서버는 `#`을 글자로 찾는다).
 */
export function tagTarget(raw: string): string | null {
  const trimmed = (raw ?? '').trim();
  if (!trimmed.startsWith('#') || /\s/u.test(trimmed)) {
    return null;
  }
  return normalizeTagQuery(trimmed);
}

/** 검색 화면 주소 (글 탭). */
export function searchHref(q: string): string {
  return `/search?q=${encodeURIComponent(q)}`;
}
