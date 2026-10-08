import { lazy, Suspense, useEffect, useState } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { onNotFound } from './api/client';
import { SessionProvider } from './features/auth/SessionProvider';
import DetailDeleteButton from './features/manage-posts/DetailDeleteButton';
import SessionBar from './features/auth/SessionBar';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import ManagePostsPage from './pages/ManagePostsPage';
import BlogPage from './pages/BlogPage';
import NotFoundPage from './pages/NotFoundPage';
import PostDetailPage from './pages/PostDetailPage';
import VisibilitySelect from './features/visibility/VisibilitySelect';
import type { AuthorActionContext } from './features/post-detail/AuthorActions';
import PrivacyPage from './pages/PrivacyPage';
import SignupPage from './pages/SignupPage';
import TermsPage from './pages/TermsPage';
import VerifyEmailPage from './pages/VerifyEmailPage';

/**
 * 에디터(002)는 따로 불러온다 (005 T075, FR-031). 에디터가 정적으로 묶이면 에디터 미리보기가 쓰는 코드 강조(highlight.js)도
 * 첫 번들에 함께 들어가 글을 읽기만 하는 사람도 받게 된다. 에디터를 나누면 코드 강조는 별도 청크가 되고, 글 상세는 코드 블록이
 * 있을 때만 그 청크를 부른다(`features/post-detail/loadHighlighter.ts`).
 */
const EditorPage = lazy(() => import('./pages/EditorPage'));
const NewPostPage = lazy(() =>
  import('./pages/EditorPage').then((module) => ({ default: module.NewPostPage })),
);

/** 에디터 청크를 받는 동안 */
function EditorLoading() {
  return (
    <main data-route="editor-loading" aria-busy="true">
      불러오는 중…
    </main>
  );
}

/** 화면 자리. 각 기능이 자기 화면 컴포넌트로 바꾼다 (001: 가입·로그인·설정 등). */
function Placeholder({ name }: { name: string }) {
  return <main data-route={name}>{name}</main>;
}

/**
 * 004: 글 상세 작성자 버튼 줄의 [공개 범위 ▾] 자리 (005 `visibilityControl`). 고르는 즉시 저장하고 상세를 다시 부른다 —
 * 다시 발행하지 않아 "수정됨"이 생기지 않는다(FR-016).
 */
function renderDetailVisibility({ postId, visibility, reload }: AuthorActionContext) {
  return <VisibilitySelect postId={postId} value={visibility} onSaved={reload} />;
}

/** 006: 글 상세 작성자 버튼 줄의 [삭제] 자리 (005 `deleteControl`) */
function renderDetailDelete({ postId, reload }: { postId: number; reload: () => void }) {
  return <DetailDeleteButton postId={postId} reload={reload} />;
}

export default function App() {
  const location = useLocation();
  // API가 404 NOT_FOUND를 주면 지금 화면 대신 공통 404 화면을 보인다(004 T024·T025). 다른 주소로 옮기면 풀린다.
  const [notFoundAt, setNotFoundAt] = useState<string | null>(null);

  useEffect(() => onNotFound(() => setNotFoundAt(location.key)), [location.key]);

  return (
    <SessionProvider>
      <SessionBar />
      {notFoundAt !== null && notFoundAt === location.key ? (
        <NotFoundPage />
      ) : (
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/signup/social" element={<Placeholder name="signup-social" />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<Placeholder name="forgot-password" />} />
          <Route path="/reset-password" element={<Placeholder name="reset-password" />} />
          <Route path="/reagree" element={<Placeholder name="reagree" />} />
          <Route path="/settings" element={<Placeholder name="settings" />} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route
            path="/write/new"
            element={
              <Suspense fallback={<EditorLoading />}>
                <NewPostPage />
              </Suspense>
            }
          />
          <Route
            path="/write/:postId"
            element={
              <Suspense fallback={<EditorLoading />}>
                <EditorPage />
              </Suspense>
            }
          />
          {/* 006 내 글 관리 — 사용자를 가리키는 값 없이 본인 글만 (FR-002) */}
          <Route path="/manage/posts" element={<ManagePostsPage />} />
          {/* 005 글 상세 — react-router는 `/@:handle`처럼 구간 일부만 파라미터로 받지 못해 `@`는 화면이 떼어 낸다 */}
          <Route
            path="/:handle/posts/:postId"
            element={
              <PostDetailPage
                visibilityControl={renderDetailVisibility}
                deleteControl={renderDetailDelete}
              />
            }
          />
          <Route path="/:handle" element={<BlogPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      )}
    </SessionProvider>
  );
}
