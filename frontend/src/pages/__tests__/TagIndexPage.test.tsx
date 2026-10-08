import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { errorBody, json, stubFetch } from '../../test/fetchRoutes';
import TagIndexPage from '../TagIndexPage';

function renderIndex() {
  return render(
    <MemoryRouter initialEntries={['/tags']}>
      <TagIndexPage />
    </MemoryRouter>,
  );
}

/** 전체 태그 목록 화면 (008 T049, US4). */
describe('TagIndexPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('"#이름 글 수" 목록과 tagPath 링크', async () => {
    stubFetch({
      'GET /api/tags': () =>
        json(200, {
          items: [
            { name: 'spring-boot', postCount: 1234 },
            { name: 'c#', postCount: 3 },
            { name: '자바', postCount: 1 },
          ],
        }),
    });
    renderIndex();

    expect(await screen.findByRole('heading', { name: '태그' })).toBeInTheDocument();
    const list = await screen.findByRole('list', { name: '태그 목록' });
    const links = within(list).getAllByRole('link');
    expect(links.map((a) => a.textContent)).toEqual(['#spring-boot 1,234', '#c# 3', '#자바 1']);
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/tags/spring-boot',
      '/tags/c%23',
      '/tags/%EC%9E%90%EB%B0%94',
    ]);
  });

  it('빈 목록이면 "아직 태그가 없어요"', async () => {
    stubFetch({ 'GET /api/tags': () => json(200, { items: [] }) });
    renderIndex();
    expect(await screen.findByText('아직 태그가 없어요')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: '태그 목록' })).not.toBeInTheDocument();
  });

  it('실패하면 "불러오지 못했어요 [다시 시도]"', async () => {
    const replies = [
      () => json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')),
      () => json(200, { items: [{ name: 'java', postCount: 2 }] }),
    ];
    stubFetch({ 'GET /api/tags': () => replies.shift()!() });
    const user = userEvent.setup();
    renderIndex();

    expect(await screen.findByText(/불러오지 못했어요/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('link', { name: '#java 2' })).toBeInTheDocument();
  });
});
