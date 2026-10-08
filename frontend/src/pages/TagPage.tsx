import { useCallback, useEffect, useState } from 'react';
import { useNavigationType, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getTagSummary, listTagPosts } from '../api/tags';
import type { TagSummary } from '../api/types/tags';
import LoadMoreButton, { INITIAL_LOAD_FAILED_TEXT } from '../components/LoadMoreButton';
import PostCard from '../components/PostCard';
import PostCardGrid from '../components/PostCardGrid';
import { useCursorList } from '../features/post-list/useCursorList';
import NotFoundPage from './NotFoundPage';
import '../features/tag/tag.css';

/** 공개 글이 없을 때 (US2 #3). 비공개 글만 있는 태그와 같은 화면이다(SC-005). */
export const EMPTY_TAG_TEXT = '아직 이 태그로 공개된 글이 없어요';

/** 어느 이름의 결과인지 함께 둔다 — 이름이 바뀌면 새 결과가 올 때까지 머리말 숫자를 숨긴다. */
type SummaryState =
  | { name: string; status: 'ready'; summary: TagSummary }
  | { name: string; status: 'not-found' }
  | { name: string; status: 'error' };

/**
 * 태그별 글 목록 화면 (008 T037, US2, FR-008·FR-027).
 *
 * 머리말("#name · 공개 글 N")과 첫 목록을 동시에 부른다. 카드·[더 보기]·첫 목록 실패 문구는 홈·블로그(005)와 같은 부품이고,
 * 뒤로 가기 복원 키는 `tag:{name}`이다. react-router는 `:name`을 디코드해서 준다(`/tags/c%23` → `c#`). 서버가 404를 주면
 * (형식이 틀린 이름) 공통 404 화면이다 — 정규화되지 않은 주소의 301은 서버 첫 응답이 이미 처리했다.
 */
export default function TagPage() {
  const params = useParams();
  const name = params.name ?? '';
  const [state, setState] = useState<SummaryState | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const summary = await getTagSummary(name);
        if (!cancelled) {
          setState({ name, status: 'ready', summary });
        }
      } catch (error) {
        if (!cancelled) {
          const notFound = error instanceof ApiError && error.status === 404;
          setState({ name, status: notFound ? 'not-found' : 'error' });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [name]);

  const navigationType = useNavigationType();
  const load = useCallback((cursor?: string | null) => listTagPosts(name, cursor), [name]);
  const list = useCursorList(load, {
    listKey: `tag:${name}`,
    restore: navigationType === 'POP',
  });

  const current = state !== null && state.name === name ? state : null;
  if (current?.status === 'not-found') {
    return <NotFoundPage />;
  }

  return (
    <main data-route="tag" className="tag-page">
      <header className="tag-page__header">
        <h1 className="tag-page__title">#{name}</h1>
        {current?.status === 'ready' ? (
          <p className="tag-page__count">
            공개 글 {current.summary.postCount.toLocaleString('ko-KR')}
          </p>
        ) : null}
      </header>

      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} />
        ))}
      </PostCardGrid>

      {list.initialError ? (
        <p role="status" className="tag-page__empty">
          {INITIAL_LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : list.loadedOnce && list.items.length === 0 ? (
        <p className="tag-page__empty">{EMPTY_TAG_TEXT}</p>
      ) : (
        <LoadMoreButton
          status={list.status}
          done={list.done}
          onLoadMore={() => void list.loadMore()}
          onRetry={() => void list.retry()}
        />
      )}
    </main>
  );
}
