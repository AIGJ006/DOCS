import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostCard, PostCardPage } from '../../api/types/reading';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import TagPage from '../TagPage';

function card(id: number): PostCard {
  return {
    id,
    url: `/@kim755030/posts/${id}`,
    title: `글 ${id}`,
    excerpt: '요약',
    thumbnailUrl: null,
    firstPublicAt: '2026-10-02T14:03:12.123456Z',
    commentCount: 0,
    likeCount: 0,
    author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
  };
}

function page(ids: number[], nextCursor: string | null): PostCardPage {
  return { items: ids.map(card), nextCursor };
}

function range(from: number, count: number) {
  return Array.from({ length: count }, (_, i) => from - i);
}

function renderTag(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/tags/:name" element={<TagPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 태그별 글 목록 화면 (008 T030, US2 #1·#3, FR-027). */
describe('TagPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('머리말 "#spring-boot · 공개 글 12"와 카드 9개, [더 보기]로 이어진다', async () => {
    const pages = [page(range(12, 9), 'c1'), page(range(3, 3), null)];
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/tags/spring-boot/summary': () => json(200, { name: 'spring-boot', postCount: 12 }),
      'GET /api/tags/spring-boot/posts': () => json(200, pages[call++]),
    });
    const user = userEvent.setup();
    renderTag('/tags/spring-boot');

    expect(await screen.findByRole('heading', { name: '#spring-boot' })).toBeInTheDocument();
    expect(await screen.findByText('공개 글 12')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(9));
    expect(requestsTo(fetchMock, 'GET', '/api/tags/spring-boot/summary')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(12));
    const [, second] = requestsTo(fetchMock, 'GET', '/api/tags/spring-boot/posts');
    expect(String(second[0])).toContain('cursor=c1');
    expect(screen.getByText('모든 글을 다 봤어요')).toBeInTheDocument();
  });

  it('불러오는 중에는 [더 보기]가 비활성이고 실패하면 "불러오지 못했어요 [다시 시도]"', async () => {
    let finish: (r: Response) => void = () => undefined;
    const replies = [
      () => json(200, page(range(12, 9), 'c1')),
      () => new Promise<Response>((resolve) => (finish = resolve)),
      () => json(200, page(range(3, 3), null)),
    ];
    stubFetch({
      'GET /api/tags/jpa/summary': () => json(200, { name: 'jpa', postCount: 12 }),
      'GET /api/tags/jpa/posts': () => replies.shift()!(),
    });
    const user = userEvent.setup();
    renderTag('/tags/jpa');
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(9));

    await user.click(screen.getByRole('button', { name: '더 보기' }));
    expect(await screen.findByRole('button', { name: '불러오는 중…' })).toBeDisabled();
    finish(json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')));
    expect(await screen.findByText(/불러오지 못했어요/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(12));
  });

  it('첫 목록 실패도 [다시 시도]', async () => {
    const replies = [
      () => json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')),
      () => json(200, page([5], null)),
    ];
    stubFetch({
      'GET /api/tags/jpa/summary': () => json(200, { name: 'jpa', postCount: 1 }),
      'GET /api/tags/jpa/posts': () => replies.shift()!(),
    });
    const user = userEvent.setup();
    renderTag('/tags/jpa');
    expect(await screen.findByText(/불러오지 못했어요/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(1));
  });

  it('글이 없으면 "아직 이 태그로 공개된 글이 없어요"', async () => {
    stubFetch({
      'GET /api/tags/%EC%9D%B4%EC%A7%81%EC%A4%80%EB%B9%84/summary': () =>
        json(200, { name: '이직준비', postCount: 0 }),
      'GET /api/tags/%EC%9D%B4%EC%A7%81%EC%A4%80%EB%B9%84/posts': () =>
        json(200, page([], null)),
    });
    renderTag('/tags/%EC%9D%B4%EC%A7%81%EC%A4%80%EB%B9%84');
    expect(await screen.findByText('아직 이 태그로 공개된 글이 없어요')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '#이직준비' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
  });

  it('c#처럼 인코딩된 주소는 디코드한 이름으로 같은 API를 부른다', async () => {
    const fetchMock = stubFetch({
      'GET /api/tags/c%23/summary': () => json(200, { name: 'c#', postCount: 1 }),
      'GET /api/tags/c%23/posts': () => json(200, page([1], null)),
    });
    renderTag('/tags/c%23');
    expect(await screen.findByRole('heading', { name: '#c#' })).toBeInTheDocument();
    expect(requestsTo(fetchMock, 'GET', '/api/tags/c%23/posts')).toHaveLength(1);
  });

  it('API 404면 공통 404 화면', async () => {
    stubFetch({});
    renderTag('/tags/%F0%9F%94%A5');
    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
  });
});
