import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { SITE_NAME } from '../config';
import { logout } from '../features/auth/logout';
import { useSession } from '../features/auth/useSession';
import type { MeSummary } from '../api/me';
import DefaultAvatar from './DefaultAvatar';
import SearchBox from '../features/search/SearchBox';
import './siteHeader.css';

/** 로그인 화면으로 가는 주소. 지금 보던 화면으로 돌아오게 `returnTo`를 붙인다(로그인·가입 화면 자신은 빼고). */
function loginHref(pathname: string, search: string): string {
  if (pathname === '/' || pathname === '/login' || pathname.startsWith('/signup')) {
    return '/login';
  }
  return `/login?returnTo=${encodeURIComponent(pathname + search)}`;
}

/**
 * 모든 화면 위에 있는 공통 머리말 (001 임시 `SessionBar`를 대신한다).
 *
 * - 왼쪽: 서비스 이름 → 홈
 * - 비로그인: [로그인] [회원 가입]
 * - 로그인: [글쓰기] · 피드(010) · 내 블로그 · 계정 메뉴(내 글 관리 · 설정 · 로그아웃)
 * - 글쓰기 화면(`/write/*`)에서는 [글쓰기]만 숨긴다. 머리말 자체는 남긴다 — 016 테마 버튼이 "어느 페이지에서나 같은 자리"(45 T-4).
 * - 맨 오른쪽은 016 테마 전환 버튼 자리다. 010 [피드]·011 알림 🔔도 이 줄의 `site-header-actions`에 더한다.
 * - 012 검색창(이름 "검색")은 이 줄 맨 앞. 좁은 화면에서는 입력 대신 [검색] 링크(`/search`, 검색 화면에 입력이 있다).
 */
export default function SiteHeader() {
  const { loading, me } = useSession();
  const location = useLocation();
  const onEditor = location.pathname.startsWith('/write/');

  return (
    <header className="site-header">
      <div className="site-header-inner">
        <Link to="/" className="site-header-logo" aria-label={SITE_NAME}>
          {logoParts(SITE_NAME)}
        </Link>
        <nav aria-label="사이트 메뉴" className="site-header-actions">
          <span className="site-header-wide-only">
            <SearchBox />
          </span>
          <Link to="/search" className="site-header-link site-header-narrow-only">
            검색
          </Link>
          {loading ? null : me ? (
            <>
              {!onEditor && (
                <Link to="/write/new" className="site-header-write">
                  글쓰기
                </Link>
              )}
              <Link to="/feed" className="site-header-link">
                피드
              </Link>
              <Link to={`/@${me.handle}`} className="site-header-link site-header-wide-only">
                내 블로그
              </Link>
              <AccountMenu me={me} />
            </>
          ) : (
            <>
              <Link to={loginHref(location.pathname, location.search)} className="site-header-link">
                로그인
              </Link>
              <Link to="/signup" className="site-header-write">
                회원 가입
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}

/** 계정 메뉴. 열고 닫는 단추 + 링크 목록(펼침 방식). Esc·바깥 누름·화면 이동이면 닫힌다. */
function AccountMenu({ me }: { me: MeSummary }) {
  const location = useLocation();
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  // 연 화면의 location.key. 다른 화면으로 옮기면 저절로 닫힌다.
  const [openAt, setOpenAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const open = openAt !== null && openAt === location.key;

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpenAt(null);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenAt(null);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const close = () => setOpenAt(null);

  const onLogout = () => {
    setError(null);
    setBusy(true);
    logout(me.memberId)
      .catch(() => setError('로그아웃하지 못했어요. 다시 시도해 주세요'))
      .finally(() => setBusy(false));
  };

  return (
    <div className="site-account" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="site-account-button"
        aria-label={`계정 메뉴 (${me.nickname})`}
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpenAt(open ? null : location.key)}
      >
        {me.profileImageUrl ? (
          <img src={me.profileImageUrl} alt="" width={28} height={28} className="site-avatar" />
        ) : (
          <DefaultAvatar nickname={me.nickname} handle={me.handle} size={28} />
        )}
        <span className="site-account-name site-header-wide-only">{me.nickname}</span>
        <span aria-hidden="true" className="site-account-caret">
          ▾
        </span>
      </button>
      {open && (
        <ul id={menuId} className="site-account-menu">
          <li className="site-header-narrow-only">
            <Link to={`/@${me.handle}`} onClick={close}>
              내 블로그
            </Link>
          </li>
          <li>
            <Link to="/manage/posts" onClick={close}>
              내 글 관리
            </Link>
          </li>
          <li>
            <Link to="/settings" onClick={close}>
              설정
            </Link>
          </li>
          <li>
            <button type="button" onClick={onLogout} disabled={busy}>
              로그아웃
            </button>
          </li>
        </ul>
      )}
      {error && (
        <p role="alert" className="site-account-error">
          {error}
        </p>
      )}
    </div>
  );
}

/** 대문자만 브랜드 색으로 칠한다: BuildLOG → B·LOG가 이어져 BLOG로 읽힌다. */
function logoParts(name: string) {
  return Array.from(name.matchAll(/[A-Z]+|[^A-Z]+/g), ([part], i) => (
    <span
      key={i}
      className={/[A-Z]/.test(part) ? 'site-header-logo-mark' : undefined}
      aria-hidden="true"
    >
      {part}
    </span>
  ));
}
