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

describe('PrivacyPage 조회수 집계 문단 (009 T046, research R15)', () => {
  it('vid 쿠키 1년과 하루만 쓰는 되돌릴 수 없는 값을 알린다', () => {
    stubFetch({ 'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS) });
    render(
      <MemoryRouter>
        <PrivacyPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: '5. 조회수 집계' })).toBeInTheDocument();
    expect(screen.getByText(/무작위\s+식별자 쿠키\(vid\)를 1년 동안/)).toBeInTheDocument();
    expect(screen.getByText(/되돌릴 수 없는 값을 하루 동안만/)).toBeInTheDocument();
  });
});
