import { useState } from 'react';
import { Link } from 'react-router-dom';
import { logout } from './logout';
import { useSession } from './useSession';

/** 임시 머리말: 로그인 상태와 로그아웃 버튼. 공통 머리말이 생기면 그쪽으로 옮긴다. */
export default function SessionBar() {
  const { loading, me } = useSession();
  const [error, setError] = useState<string | null>(null);
  if (loading) {
    return null;
  }
  if (!me) {
    return (
      <nav aria-label="계정">
        <Link to="/login">로그인</Link> · <Link to="/signup">회원 가입</Link>
      </nav>
    );
  }
  return (
    <nav aria-label="계정">
      {me.nickname}{' '}
      <button
        type="button"
        onClick={() => {
          setError(null);
          logout(me.memberId).catch(() => setError('로그아웃하지 못했어요. 다시 시도해 주세요'));
        }}
      >
        로그아웃
      </button>
      {error && <span role="alert"> {error}</span>}
    </nav>
  );
}
