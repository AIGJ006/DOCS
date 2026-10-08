import { useEffect, useRef, useState } from 'react';
import { suggestTags } from '../../api/tags';
import type { TagSuggestion } from '../../api/types/tags';
import { normalizeTagQuery } from './normalizeTag';

/** 입력이 이만큼 멈추면 부른다 (spec FR-033, research R12). 서버 설정값이 아니라 화면 상수다. */
export const TAG_SUGGEST_DEBOUNCE_MS = 300;

export type SuggestLoader = (q: string, signal?: AbortSignal) => Promise<TagSuggestion[]>;

interface Loaded {
  query: string;
  items: TagSuggestion[];
}

const NONE: TagSuggestion[] = [];

/**
 * 태그 자동완성 후보 (008 T045, US3 #4·#5, research R12).
 *
 * - 입력이 0.3초 멈추고 한글 조합 중(`compositionstart`~`compositionend`)이 아닐 때만 부른다.
 * - 검색어는 `normalizeTagQuery`로 정리한다 — 비면(`#`·공백·형식 오류) 부르지 않는다.
 * - 요청마다 번호를 올리고 이전 요청은 `AbortController`로 끊는다. 늦게 온 응답은 번호가 달라 버린다.
 * - 실패·429·빈 결과·불러오는 중에는 빈 목록이다(결과는 그 검색어에 묶어 두고, 지금 검색어와 같을 때만 보인다).
 */
export function useTagSuggest(
  text: string,
  composing: boolean,
  load: SuggestLoader = suggestTags,
): TagSuggestion[] {
  const query = composing ? null : normalizeTagQuery(text);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const requestNo = useRef(0);

  useEffect(() => {
    if (query === null) {
      requestNo.current += 1;
      return undefined;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      const no = ++requestNo.current;
      load(query, controller.signal).then(
        (items) => {
          if (no === requestNo.current) {
            setLoaded({ query, items });
          }
        },
        () => {
          if (no === requestNo.current) {
            setLoaded({ query, items: NONE });
          }
        },
      );
    }, TAG_SUGGEST_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, load]);

  return query !== null && loaded !== null && loaded.query === query ? loaded.items : NONE;
}
