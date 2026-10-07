import { useEffect, useState } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { onNotFound } from './api/client';
import { SessionProvider } from './features/auth/SessionProvider';
import SessionBar from './features/auth/SessionBar';
import EditorPage, { NewPostPage } from './pages/EditorPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import PrivacyPage from './pages/PrivacyPage';
import SignupPage from './pages/SignupPage';
import TermsPage from './pages/TermsPage';
import VerifyEmailPage from './pages/VerifyEmailPage';

/** 화면 자리. 각 기능이 자기 화면 컴포넌트로 바꾼다 (001: 가입·로그인·설정 등). */
function Placeholder({ name }: { name: string }) {
  return <main data-route={name}>{name}</main>;
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
          <Route path="/write/new" element={<NewPostPage />} />
          <Route path="/write/:postId" element={<EditorPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      )}
    </SessionProvider>
  );
}
