import { Route, Routes } from 'react-router-dom';

/** 화면 자리. 각 기능이 자기 화면 컴포넌트로 바꾼다 (001: 가입·로그인·설정 등). */
function Placeholder({ name }: { name: string }) {
  return <main data-route={name}>{name}</main>;
}

export default function App() {
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
    </Routes>
  );
}
