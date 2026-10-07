import { useEffect, useState } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { onNotFound } from './api/client';
import NotFoundPage from './pages/NotFoundPage';

/** 화면 자리. 각 기능이 자기 화면 컴포넌트로 바꾼다 (001: 가입·로그인·설정 등). */
function Placeholder({ name }: { name: string }) {
  return <main data-route={name}>{name}</main>;
}

export default function App() {
  const location = useLocation();
  // API가 404 NOT_FOUND를 주면 지금 화면 대신 공통 404 화면을 보인다(004 T024·T025). 다른 주소로 옮기면 풀린다.
  const [notFoundAt, setNotFoundAt] = useState<string | null>(null);

  useEffect(() => onNotFound(() => setNotFoundAt(location.key)), [location.key]);

  if (notFoundAt !== null && notFoundAt === location.key) {
    return <NotFoundPage />;
  }

  return (
    <Routes>
      <Route path="/" element={<Placeholder name="home" />} />
      <Route path="/signup" element={<Placeholder name="signup" />} />
      <Route path="/signup/social" element={<Placeholder name="signup-social" />} />
      <Route path="/login" element={<Placeholder name="login" />} />
      <Route path="/verify-email" element={<Placeholder name="verify-email" />} />
      <Route path="/forgot-password" element={<Placeholder name="forgot-password" />} />
      <Route path="/reset-password" element={<Placeholder name="reset-password" />} />
      <Route path="/reagree" element={<Placeholder name="reagree" />} />
      <Route path="/settings" element={<Placeholder name="settings" />} />
      <Route path="/terms" element={<Placeholder name="terms" />} />
      <Route path="/privacy" element={<Placeholder name="privacy" />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
