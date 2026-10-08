import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { searchPeople } from '../../api/discovery';
import type { PersonItem } from '../../api/types/discovery';
import PeopleResultItem from './PeopleResultItem';
import { parsePeopleQuery } from './parseQuery';
import {
  SEARCH_FAILED_TEXT,
  SEARCH_RATE_LIMITED_TEXT,
  SEARCH_TOO_SHORT_TEXT,
  noPeopleText,
} from './searchMessages';
import './search.css';

/**
 * 사람 검색 결과 (012 T037, research R10, US3 #1·#2). 최대 20명, [더 보기] 없음.
 * 남는 글자가 2개 미만이면(공백·맨 앞 `@` 제외) 요청하지 않고 "두 글자 이상 입력해 주세요".
 */
export default function PeopleResults({ q }: { q: string }) {
  const token = parsePeopleQuery(q);
  if (token === null) {
    return (
      <p role="status" className="search-empty">
        {SEARCH_TOO_SHORT_TEXT}
      </p>
    );
  }
  return <PeopleList key={q} q={q} />;
}

type State =
  | { status: 'loading' }
  | { status: 'ready'; items: PersonItem[] }
  | { status: 'error'; rateLimited: boolean };

function PeopleList({ q }: { q: string }) {
  const [state, setState] = useState<State>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    searchPeople(q).then(
      (result) => {
        if (!cancelled) {
          setState({ status: 'ready', items: result.items });
        }
      },
      (error: unknown) => {
        if (!cancelled) {
          setState({
            status: 'error',
            rateLimited: error instanceof ApiError && error.status === 429,
          });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [q, attempt]);

  if (state.status === 'loading') {
    return <p aria-busy="true" className="search-empty" />;
  }
  if (state.status === 'error') {
    return (
      <p role="status" className="search-empty">
        {state.rateLimited ? SEARCH_RATE_LIMITED_TEXT : SEARCH_FAILED_TEXT}{' '}
        <button
          type="button"
          onClick={() => {
            setState({ status: 'loading' });
            setAttempt((n) => n + 1);
          }}
        >
          다시 시도
        </button>
      </p>
    );
  }
  if (state.items.length === 0) {
    return (
      <p role="status" className="search-empty" data-testid="people-empty">
        {noPeopleText(q)}
      </p>
    );
  }
  return (
    <ul className="people-list" aria-label="사람 검색 결과">
      {state.items.map((person) => (
        <PeopleResultItem key={person.handle} person={person} />
      ))}
    </ul>
  );
}
