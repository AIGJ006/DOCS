// 색 토큰 → 공통 기본 스타일 순서로, 다른 CSS보다 먼저 (016 T007·T009)
import './styles/tokens.css';
import './styles/base.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ensureCsrf } from './api/client';
import { registerLogoutCleanup } from './features/auth/logout';
import { clearMemberDrafts } from './features/editor/localDraftStore';
import { flushPendingWork } from './features/editor/pendingWork';

const container = document.getElementById('root');
if (!container) {
  throw new Error('root element not found');
}

function render(root: HTMLElement) {
  createRoot(root).render(
    <StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>,
  );
}

// 002: 로그아웃 때 미전송 자동 저장을 보내고 이 기기 임시 글을 지운다(001 logout.ts 등록 지점).
registerLogoutCleanup({ flushPendingWork, clearMemberDrafts });

// 상태를 바꾸는 첫 요청 전에 XSRF-TOKEN 쿠키를 받아 둔다(R-05). 실패해도 화면은 띄우고,
// 다음 ensureCsrf() 호출이 다시 시도한다.
ensureCsrf()
  .catch(() => undefined)
  .finally(() => render(container));
