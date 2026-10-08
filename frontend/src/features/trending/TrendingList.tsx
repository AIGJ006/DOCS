import { useCallback, useState } from 'react';
import { Link, useNavigationType } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { getTrending } from '../../api/discovery';
import LoadMoreButton, { INITIAL_LOAD_FAILED_TEXT } from '../../components/LoadMoreButton';
import PostCard from '../../components/PostCard';
import PostCardGrid from '../../components/PostCardGrid';
import * as listRestore from '../post-list/listRestore';
import { useCursorList } from '../post-list/useCursorList';
import {
  SNAPSHOT_EXPIRED_CODE,
  TRENDING_DESCRIPTION_TEXT,
  TRENDING_EMPTY_TEXT,
  TRENDING_EXPIRED_TEXT,
  TRENDING_LIST_KEY,
  TRENDING_SHOW_LATEST_TEXT,
} from './trendingMessages';

/**
 * 홈 트렌딩 탭 목록 (012 T029, US2, FR-009~FR-015, research R5).
 *
 * - 탭 아래 작은 글씨 "최근 7일 동안 반응이 많은 글 · 10분마다 갱신". 카드는 005 `PostCard` 그대로 — 순위 숫자는 없다.
 * - 빈 상태 "아직 트렌딩 글이 없어요" + [최신 글 보기]. 최신 글로 채우지 않는다.
 * - [더 보기]가 410 `SNAPSHOT_EXPIRED`(보던 순위가 30분이 지나 사라짐)면 "순위가 새로 바뀌었어요"를 보이고 최신 순위를
 *   처음부터 다시 부른다(보관한 복원값도 버린다).
 * - 뒤로 가기 복원 키 `trending` (홈 최신 탭 `home`과 따로).
 */
export default function TrendingList() {
  const navigationType = useNavigationType();
  // 순위가 바뀌어 처음부터 다시 부를 때마다 1씩 늘린다 — `load`가 바뀌면 목록이 처음 상태로 돌아간다
  const [generation, setGeneration] = useState(0);
  const [expired, setExpired] = useState(false);
  const load = useCallback(
    async (cursor?: string | null) => {
      void generation;
      try {
        return await getTrending(cursor);
      } catch (error) {
        if (error instanceof ApiError && error.code === SNAPSHOT_EXPIRED_CODE) {
          listRestore.clear(TRENDING_LIST_KEY);
          setExpired(true);
          setGeneration((n) => n + 1);
        }
        throw error;
      }
    },
    [generation],
  );
  const list = useCursorList(load, {
    listKey: TRENDING_LIST_KEY,
    restore: navigationType === 'POP',
  });
  const snapshotExpired =
    list.status === 'error' &&
    list.error instanceof ApiError &&
    list.error.code === SNAPSHOT_EXPIRED_CODE;

  const empty = list.loadedOnce && list.items.length === 0;

  return (
    <section aria-label="트렌딩 글">
      <p style={{ margin: '0 0 1rem', color: 'var(--color-text-muted)', fontSize: '0.8125rem' }}>
        {TRENDING_DESCRIPTION_TEXT}
      </p>
      {expired ? (
        <p role="status" data-testid="trending-expired" style={{ margin: '0 0 1rem' }}>
          {TRENDING_EXPIRED_TEXT}
        </p>
      ) : null}
      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} showAuthor />
        ))}
      </PostCardGrid>
      {list.initialError ? (
        <p role="status" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {INITIAL_LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : empty ? (
        <p data-testid="empty-trending" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {TRENDING_EMPTY_TEXT} <Link to="/">{TRENDING_SHOW_LATEST_TEXT}</Link>
        </p>
      ) : snapshotExpired ? null : (
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
