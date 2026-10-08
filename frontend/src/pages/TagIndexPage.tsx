import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listTopTags } from '../api/tags';
import type { TagCount } from '../api/types/tags';
import { tagPath } from '../features/tag/tagPath';
import '../features/tag/tag.css';

export const EMPTY_TAG_INDEX_TEXT = '아직 태그가 없어요';
export const TAG_INDEX_FAILED_TEXT = '불러오지 못했어요';

type State = { status: 'loading' } | { status: 'ready'; items: TagCount[] } | { status: 'error' };

/**
 * 전체 태그 목록 화면 (008 T052, US4). 공개 글 수 많은 순 상위 100개를 `#이름 글 수` 링크로 줄바꿈해 보인다(375px에서도 가로
 * 스크롤 없음 — 긴 이름은 `overflow-wrap: anywhere`).
 */
export default function TagIndexPage() {
  const [state, setState] = useState<State>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    listTopTags().then(
      (index) => {
        if (!cancelled) {
          setState({ status: 'ready', items: index.items });
        }
      },
      () => {
        if (!cancelled) {
          setState({ status: 'error' });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    setAttempt((n) => n + 1);
  }, []);

  return (
    <main data-route="tag-index" className="tag-page">
      <header className="tag-page__header">
        <h1 className="tag-page__title">태그</h1>
      </header>
      {state.status === 'loading' ? (
        <div aria-busy="true" style={{ minHeight: '4rem' }} />
      ) : state.status === 'error' ? (
        <p role="status" className="tag-page__empty">
          {TAG_INDEX_FAILED_TEXT}{' '}
          <button type="button" onClick={retry}>
            다시 시도
          </button>
        </p>
      ) : state.items.length === 0 ? (
        <p className="tag-page__empty">{EMPTY_TAG_INDEX_TEXT}</p>
      ) : (
        <ul className="tag-index__list" aria-label="태그 목록">
          {state.items.map((tag) => (
            <li key={tag.name} style={{ minWidth: 0, maxWidth: '100%' }}>
              <Link className="tag-link" to={tagPath(tag.name)}>
                #{tag.name}{' '}
                <span className="tag-index__count">{tag.postCount.toLocaleString('ko-KR')}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
