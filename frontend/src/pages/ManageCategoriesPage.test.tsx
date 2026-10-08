import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MyCategories } from '../api/categories';
import { resetClientForTests } from '../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import ManageCategoriesPage, { HAS_CHILDREN_TEXT } from './ManageCategoriesPage';

const TREE: MyCategories = {
  maxCount: 100,
  items: [
    {
      id: 1,
      name: '개발',
      postCount: 3,
      children: [
        { id: 3, name: 'Spring', postCount: 2, children: [] },
        { id: 4, name: 'React', postCount: 1, children: [] },
      ],
    },
    { id: 2, name: '일상', postCount: 0, children: [] },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/manage/categories']}>
      <ManageCategoriesPage />
    </MemoryRouter>,
  );
}

function bodyOf(init: RequestInit | undefined) {
  return JSON.parse(String(init?.body));
}

/** 카테고리 관리 화면 (017 T018, US1, FR-013·FR-014). */
describe('ManageCategoriesPage', () => {
  beforeEach(() => {
    resetClientForTests();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('나무 모양으로 이름과 글 수를 보인다', async () => {
    stubFetch({ 'GET /api/me/categories': () => json(200, TREE) });
    renderPage();

    const list = await screen.findByRole('list', { name: '카테고리 목록' });
    const names = within(list)
      .getAllByText(/^(개발|Spring|React|일상)$/)
      .map((el) => el.textContent);
    expect(names).toEqual(['개발', 'Spring', 'React', '일상']);
    expect(within(list).getByText('글 3')).toBeInTheDocument();
  });

  it('상위를 골라 추가한다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'POST /api/me/categories': () => json(201, { id: 9, name: 'JPA', parentId: 1, position: 2 }),
    });
    renderPage();
    await screen.findByRole('list', { name: '카테고리 목록' });

    await userEvent.type(screen.getByRole('textbox', { name: '새 카테고리 이름' }), 'JPA');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '상위 카테고리' }), '1');
    await userEvent.click(screen.getByRole('button', { name: '카테고리 추가' }));

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/me/categories')).toHaveLength(1),
    );
    expect(bodyOf(requestsTo(fetchMock, 'POST', '/api/me/categories')[0][1])).toEqual({
      name: 'JPA',
      parentId: 1,
    });
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: '새 카테고리 이름' })).toHaveValue(''),
    );
  });

  it('서버 오류 문구를 보인다', async () => {
    stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'POST /api/me/categories': () =>
        json(409, errorBody('CATEGORY_NAME_DUPLICATED', '같은 이름의 카테고리가 이미 있어요')),
    });
    renderPage();
    await screen.findByRole('list', { name: '카테고리 목록' });
    await userEvent.type(screen.getByRole('textbox', { name: '새 카테고리 이름' }), '개발');
    await userEvent.click(screen.getByRole('button', { name: '카테고리 추가' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '같은 이름의 카테고리가 이미 있어요',
    );
  });

  it('[위로]는 이웃과 자리를 바꾼 순서를 보낸다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'PUT /api/me/categories/order': () => json(200, TREE),
    });
    renderPage();
    await screen.findByRole('list', { name: '카테고리 목록' });

    expect(screen.getByRole('button', { name: '개발 위로' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'React 위로' }));

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'PUT', '/api/me/categories/order')).toHaveLength(1),
    );
    expect(bodyOf(requestsTo(fetchMock, 'PUT', '/api/me/categories/order')[0][1])).toEqual({
      parentId: 1,
      ids: [4, 3],
    });
  });

  it('하위가 있으면 지우지 않고 이유를 알린다, 글이 있으면 분류 없음 확인', async () => {
    const fetchMock = stubFetch({
      'GET /api/me/categories': () => json(200, TREE),
      'DELETE /api/me/categories/3': () => new Response(null, { status: 204 }),
    });
    renderPage();
    const list = await screen.findByRole('list', { name: '카테고리 목록' });

    const devRow = within(list).getByText('개발').closest('.category-row') as HTMLElement;
    await userEvent.click(within(devRow).getByRole('button', { name: '삭제' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(HAS_CHILDREN_TEXT);

    const springRow = within(list).getByText('Spring').closest('.category-row') as HTMLElement;
    await userEvent.click(within(springRow).getByRole('button', { name: '삭제' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('글 2개는 분류 없음이 돼요');
    await userEvent.click(within(dialog).getByRole('button', { name: '삭제' }));

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'DELETE', '/api/me/categories/3')).toHaveLength(1),
    );
  });
});
