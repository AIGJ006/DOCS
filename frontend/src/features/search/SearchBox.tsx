import { useId, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { tagPath } from '../tag/tagPath';
import { parseQuery, searchHref, tagTarget } from './parseQuery';
import { SEARCH_BOX_LABEL, SEARCH_TOO_SHORT_TEXT } from './searchMessages';
import './search.css';

/**
 * 검색창 (012 T019, research R6·R15, FR-001·FR-025·FR-032). 머리말(모든 화면)과 검색 화면·블로그 머리말이 함께 쓴다.
 *
 * 제출하면:
 * 1. 입력 전체가 `#태그`(공백 없음, 008 정규화 통과)면 태그 페이지로 이동한다 (`#spring` → `/tags/spring`).
 * 2. 남는 단어가 없으면(서버와 같은 규칙) 요청·이동 없이 "두 글자 이상 입력해 주세요".
 * 3. 아니면 `onSearch(q)` — 기본은 `/search?q=…`(글 탭).
 */
export interface SearchBoxProps {
  /** 입력의 접근 이름 (기본 "검색", 블로그 머리말은 "이 블로그에서 검색") */
  label?: string;
  defaultValue?: string;
  /** 보통 검색어 처리. 없으면 `/search?q=…`로 이동 */
  onSearch?: (q: string) => void;
  className?: string;
  /** 입력 placeholder */
  placeholder?: string;
}

export default function SearchBox({
  label = SEARCH_BOX_LABEL,
  defaultValue = '',
  onSearch,
  className = 'search-box',
  placeholder = '검색',
}: SearchBoxProps) {
  const navigate = useNavigate();
  const messageId = useId();
  const [value, setValue] = useState(defaultValue);
  const [tooShort, setTooShort] = useState(false);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const tag = tagTarget(value);
    if (tag !== null) {
      setTooShort(false);
      navigate(tagPath(tag));
      return;
    }
    if (parseQuery(value).words.length === 0) {
      setTooShort(true);
      return;
    }
    setTooShort(false);
    const q = value.trim();
    if (onSearch) {
      onSearch(q);
    } else {
      navigate(searchHref(q));
    }
  };

  return (
    <form role="search" className={className} onSubmit={onSubmit} aria-label={label}>
      <input
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        maxLength={200}
        aria-describedby={tooShort ? messageId : undefined}
        onChange={(event) => {
          setValue(event.target.value);
          setTooShort(false);
        }}
      />
      {tooShort && (
        <p id={messageId} role="alert" className="search-box-message">
          {SEARCH_TOO_SHORT_TEXT}
        </p>
      )}
    </form>
  );
}
