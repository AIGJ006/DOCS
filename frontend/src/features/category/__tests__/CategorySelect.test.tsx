import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import CategorySelect, { CATEGORY_SAVE_FAILED_TEXT } from '../CategorySelect';

const TREE = {
  maxCount: 100,
  items: [
    {
      id: 1,
      name: '개발',
      postCount: 0,
      children: [{ id: 3, name: 'Spring', postCount: 0, children: [] }],
    },
  ],
};

function renderSelect() {
  return render(
    <MemoryRouter>
      <CategorySelect postId={42} />
    </MemoryRouter>,
  );
}

/** 글쓰기 카테고리 선택 (017 T025, US2 #1·#3·#6·#7). */
describe('CategorySelect', () => {
  beforeEach(() => {
    resetClientForTests();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('지금 값을 보이고 고르면 바로 저장한다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'GET /api/posts/42/category': () => json(200, { categoryId: 1 }),
      'PUT /api/posts/42/category': () => json(200, { categoryId: 3 }),
    });
    renderSelect();

    const select = await screen.findByRole('combobox', { name: '카테고리' });
    expect(select).toHaveValue('1');
    expect(screen.getByRole('option', { name: '분류 없음' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '카테고리 관리' })).toHaveAttribute(
      'href',
      '/manage/categories',
    );

    await userEvent.selectOptions(select, '3');
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'PUT', '/api/posts/42/category')).toHaveLength(1),
    );
    expect(
      JSON.parse(String(requestsTo(fetchMock, 'PUT', '/api/posts/42/category')[0][1]?.body)),
    ).toEqual({
      categoryId: 3,
    });
    await waitFor(() => expect(select).toHaveValue('3'));
  });

  it('저장이 실패하면 이전 값으로 돌리고 알린다', async () => {
    stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'GET /api/posts/42/category': () => json(200, { categoryId: null }),
      'PUT /api/posts/42/category': () =>
        json(400, errorBody('INVALID_CATEGORY', '카테고리를 다시 선택해 주세요')),
    });
    renderSelect();

    const select = await screen.findByRole('combobox', { name: '카테고리' });
    expect(select).toHaveValue('');
    await userEvent.selectOptions(select, '1');

    expect(await screen.findByRole('alert')).toHaveTextContent(CATEGORY_SAVE_FAILED_TEXT);
    expect(select).toHaveValue('');
  });

  it('불러오지 못하면 선택을 숨긴다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me/categories': () => json(500, errorBody('INTERNAL', '오류')),
      'GET /api/posts/42/category': () => json(200, { categoryId: null }),
    });
    renderSelect();
    await waitFor(() => expect(requestsTo(fetchMock, 'GET', '/api/me/categories')).toHaveLength(1));
    expect(screen.queryByRole('combobox', { name: '카테고리' })).toBeNull();
  });
});
