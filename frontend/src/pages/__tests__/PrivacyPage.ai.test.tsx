import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { CURRENT_AGREEMENTS, json, stubFetch } from '../../test/fetchRoutes';
import PrivacyPage from '../PrivacyPage';

beforeEach(() => {
  resetClientForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('PrivacyPage 외부 AI 전송 문단 (013 T038)', () => {
  it('무료 등급 데이터 사용·사람 검토·비공개 글 제외·철회 방법을 알린다', () => {
    stubFetch({ 'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS) });
    render(
      <MemoryRouter>
        <PrivacyPage />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole('heading', { name: '6. 외부 AI 서비스(Google Gemini)로의 전송' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/무료 등급이라 Google이 받은 내용을 서비스 개선에/),
    ).toBeInTheDocument();
    expect(screen.getByText(/사람이 검토할 수 있어요/)).toBeInTheDocument();
    expect(screen.getByText(/비공개·친구 공개 글은 외부로 보내지 않고/)).toBeInTheDocument();
    expect(screen.getByText(/"AI 동의" 칸에서 언제든 취소/)).toBeInTheDocument();
  });
});
