import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ensureCsrf } from './api/client';

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

// 상태를 바꾸는 첫 요청 전에 XSRF-TOKEN 쿠키를 받아 둔다(R-05). 실패해도 화면은 띄우고,
// 다음 ensureCsrf() 호출이 다시 시도한다.
ensureCsrf()
  .catch(() => undefined)
  .finally(() => render(container));
