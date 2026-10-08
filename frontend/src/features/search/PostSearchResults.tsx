import { useCallback } from 'react';
import { useNavigationType } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { searchPosts } from '../../api/discovery';
import type { PostSearchItem, SearchSort } from '../../api/types/discovery';
import LoadMoreButton from '../../components/LoadMoreButton';
import PostCard from '../../components/PostCard';
import PostCardGrid from '../../components/PostCardGrid';
import { useCursorList } from '../post-list/useCursorList';
import { parseQuery } from './parseQuery';
import {
  SEARCH_FAILED_TEXT,
  SEARCH_RATE_LIMITED_TEXT,
  SEARCH_TOO_SHORT_TEXT,
  SEARCH_TWO_CHAR_NOTICE_TEXT,
  noPostsText,
} from './searchMessages';
import SnippetText from './SnippetText';
import './search.css';

/**
 * 글 검색 결과 (012 T020·T037, research R15, FR-024·FR-028·FR-033~FR-037). 검색 화면 글 탭과 블로그 안 검색이 함께 쓴다.
 *
 * - 정렬 `[관련도순] [최신순]` (`aria-pressed`). 바꾸면 처음부터.
 * - 남는 단어가 없으면 요청하지 않고 "두 글자 이상 입력해 주세요".
 * - 2글자 단어가 있으면 "두 글자 단어는 제목·태그에서만 찾았어요" — 서버 `notice`와 같은 규칙(`parseQuery`)으로 판단해
 *   뒤로 가기 복원(요청 없음) 때도 같은 안내가 보인다.
 * - 결과 없음 "'{검색어}'에 대한 글이 없어요", 429 "잠시 후 다시 시도해 주세요" + [다시 시도].
 * - 카드는 005 `PostCard`, 요약 자리에 `SnippetText`. 복원 키 `search:posts:{sort}:{q}`(블로그 안이면
 *   `search:blog:{handle}:{sort}:{q}`).
 */
export interface PostSearchResultsProps {
  q: string;
  sort: SearchSort;
  onSortChange: (sort: SearchSort) => void;
  /** 블로그 안 검색이면 블로그 주소 */
  blog?: string | null;
  showAuthor?: boolean;
}

export default function PostSearchResults(props: PostSearchResultsProps) {
  const parsed = parseQuery(props.q);
  if (parsed.words.length === 0) {
    return (
      <p role="status" className="search-empty">
        {SEARCH_TOO_SHORT_TEXT}
      </p>
    );
  }
  return (
    <>
      <SortRow sort={props.sort} onSortChange={props.onSortChange} />
      <ResultList
        key={`${props.blog ?? ''}:${props.sort}:${props.q}`}
        {...props}
        twoChar={parsed.hasTwoCharWord}
      />
    </>
  );
}

const SORTS: { value: SearchSort; label: string }[] = [
  { value: 'relevance', label: '관련도순' },
  { value: 'latest', label: '최신순' },
];

function SortRow({
  sort,
  onSortChange,
}: {
  sort: SearchSort;
  onSortChange: (s: SearchSort) => void;
}) {
  return (
    <div className="sort-row" role="group" aria-label="정렬">
      {SORTS.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={sort === option.value}
          onClick={() => {
            if (sort !== option.value) {
              onSortChange(option.value);
            }
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function isRateLimited(error: unknown): boolean {
  return error instanceof ApiError && error.status === 429;
}

function ResultList({
  q,
  sort,
  blog = null,
  showAuthor = true,
  twoChar,
}: PostSearchResultsProps & { twoChar: boolean }) {
  const navigationType = useNavigationType();
  const load = useCallback(
    (cursor?: string | null) => searchPosts({ q, sort, cursor, blog }),
    [q, sort, blog],
  );
  const list = useCursorList<PostSearchItem>(load, {
    listKey: blog ? `search:blog:${blog}:${sort}:${q}` : `search:posts:${sort}:${q}`,
    restore: navigationType === 'POP',
  });
  const empty = list.loadedOnce && list.items.length === 0;

  return (
    <section aria-label="글 검색 결과">
      {twoChar && list.loadedOnce ? (
        <p className="search-notice" data-testid="search-notice">
          {SEARCH_TWO_CHAR_NOTICE_TEXT}
        </p>
      ) : null}
      <PostCardGrid>
        {list.items.map((item) => (
          <PostCard
            key={item.id}
            card={item}
            showAuthor={showAuthor}
            preview={<SnippetText snippet={item.snippet} />}
          />
        ))}
      </PostCardGrid>
      {list.initialError ? (
        <p role="status" className="search-empty">
          {isRateLimited(list.error) ? SEARCH_RATE_LIMITED_TEXT : SEARCH_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : empty ? (
        <p role="status" className="search-empty" data-testid="search-empty">
          {noPostsText(q)}
        </p>
      ) : list.status === 'error' && isRateLimited(list.error) ? (
        <p role="status" style={{ textAlign: 'center' }}>
          {SEARCH_RATE_LIMITED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : (
        <LoadMoreButton
          status={list.status}
          done={list.done}
          onLoadMore={() => void list.loadMore()}
          onRetry={() => void list.retry()}
        />
      )}
    </section>
  );
}
