import { Link } from 'react-router-dom';

/** 모든 404에 같은 문구 (004 FR-014, 42 §4). 없는 글·볼 수 없는 글·남의 글을 구분하지 않는다. */
export const NOT_FOUND_MESSAGE = '볼 수 없는 페이지예요';

/** 공통 404 화면. 375px에서도 가로 스크롤이 생기지 않게 폭을 고정하지 않는다. */
export default function NotFoundPage() {
  return (
    <main
      data-route="not-found"
      style={{
        boxSizing: 'border-box',
        maxWidth: '40rem',
        width: '100%',
        margin: '0 auto',
        padding: '4rem 1rem',
        textAlign: 'center',
        overflowWrap: 'anywhere',
      }}
    >
      <h1 style={{ fontSize: '1.5rem', margin: '0 0 1.5rem' }}>{NOT_FOUND_MESSAGE}</h1>
      <p style={{ margin: 0 }}>
        <Link to="/">홈으로</Link>
      </p>
    </main>
  );
}
