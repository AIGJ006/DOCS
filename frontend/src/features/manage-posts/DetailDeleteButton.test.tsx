import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import DetailDeleteButton from './DetailDeleteButton';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname + location.search}</span>;
}

function renderButton(reload = vi.fn()) {
  render(
    <MemoryRouter initialEntries={['/@kim755030/posts/7']}>
      <Routes>
        <Route
          path="/:handle/posts/:postId"
          element={<DetailDeleteButton postId={7} reload={reload} />}
        />
        <Route path="/manage/posts" element={<main data-route="manage" />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
  return reload;
}

/** 글 상세의 작성자 [삭제] (006 US1, 005 deleteControl 자리). */
describe('DetailDeleteButton', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('확인 뒤 휴지통으로 옮기고 내 글 관리의 휴지통 탭으로 간다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'DELETE /api/posts/7': () => json(200, { trashed: true, purgeAt: '2026-11-07T00:00:00Z' }),
    });
    renderButton();

    await user.click(screen.getByRole('button', { name: '삭제' }));
    expect(screen.getByRole('dialog')).toHaveTextContent(
      '휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요',
    );
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/manage/posts?tab=trash'),
    );
    expect(requestsTo(mock, 'DELETE', '/api/posts/7')).toHaveLength(1);
  });

  it('[취소]면 요청하지 않고, 404면 상세를 다시 부른다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'DELETE /api/posts/7': () => json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });
    const reload = renderButton();

    await user.click(screen.getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(requestsTo(mock, 'DELETE', '/api/posts/7')).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));
    await waitFor(() => expect(reload).toHaveBeenCalledTimes(1));
  });
});
