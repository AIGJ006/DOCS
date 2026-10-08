import { useSearchParams } from 'react-router-dom';
import type { SearchSort } from '../api/types/discovery';
import PeopleResults from '../features/search/PeopleResults';
import PostSearchResults from '../features/search/PostSearchResults';
import SearchBox from '../features/search/SearchBox';
import '../features/search/search.css';

export type SearchTab = 'posts' | 'people';

const TABS: { value: SearchTab; label: string }[] = [
  { value: 'posts', label: '글' },
  { value: 'people', label: '사람' },
];

/**
 * 검색 화면 `/search?q&tab&sort` (012 T020·T037, research R15, US1·US3). `[글] [사람]` 탭(기본 글), 글 탭은 정렬
 * `[관련도순] [최신순]`(기본 관련도순). 탭·정렬·검색어는 주소에 둔다 — 뒤로 가기로 같은 결과로 돌아온다.
 * 첫 응답은 서버 셸이 `noindex`로 준다(FR-038).
 */
export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const tab: SearchTab = params.get('tab') === 'people' ? 'people' : 'posts';
  const sort: SearchSort = params.get('sort') === 'latest' ? 'latest' : 'relevance';

  const go = (next: { q?: string; tab?: SearchTab; sort?: SearchSort }) => {
    const search = new URLSearchParams();
    search.set('q', next.q ?? q);
    const nextTab = next.tab ?? tab;
    if (nextTab !== 'posts') {
      search.set('tab', nextTab);
    }
    const nextSort = next.sort ?? sort;
    if (nextTab === 'posts' && nextSort !== 'relevance') {
      search.set('sort', nextSort);
    }
    setParams(search);
  };

  return (
    <main
      data-route="search"
      style={{
        boxSizing: 'border-box',
        width: '100%',
        maxWidth: 'var(--page-max-width, 72rem)',
        margin: '0 auto',
        padding: '1.5rem 1rem',
      }}
    >
      <h1 style={{ fontSize: '1.25rem', margin: '0 0 1rem' }}>검색</h1>
      <SearchBox
        key={q}
        label="검색어"
        placeholder="검색어"
        defaultValue={q}
        className="search-page-form"
        onSearch={(next) => go({ q: next })}
      />
      <div role="tablist" aria-label="검색 대상" className="tab-row">
        {TABS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={tab === option.value}
            onClick={() => {
              if (tab !== option.value) {
                go({ tab: option.value });
              }
            }}
          >
            {option.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" aria-label={tab === 'posts' ? '글' : '사람'}>
        {q.trim() === '' ? null : tab === 'posts' ? (
          <PostSearchResults q={q} sort={sort} onSortChange={(s) => go({ sort: s })} />
        ) : (
          <PeopleResults q={q} />
        )}
      </div>
    </main>
  );
}
