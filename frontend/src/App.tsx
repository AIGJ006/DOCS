import { Route, Routes } from 'react-router-dom';
import { SessionProvider } from './features/auth/SessionProvider';
import SessionBar from './features/auth/SessionBar';
import LoginPage from './pages/LoginPage';
import PrivacyPage from './pages/PrivacyPage';
import SignupPage from './pages/SignupPage';
import TermsPage from './pages/TermsPage';
import VerifyEmailPage from './pages/VerifyEmailPage';

/** 화면 자리. 각 기능이 자기 화면 컴포넌트로 바꾼다 (001: 가입·로그인·설정 등). */
function Placeholder({ name }: { name: string }) {
  return <main data-route={name}>{name}</main>;
}

export default function App() {
  return (
    <SessionProvider>
      <SessionBar />
      <Routes>
        <Route path="/" element={<Placeholder name="home" />} />
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
      </Routes>
    </SessionProvider>
  );
}
