import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { stubFetch } from '../../../test/fetchRoutes';
import SearchBox from '../SearchBox';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderBox() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <SearchBox />
      <Routes>
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function submit(text: string) {
  const box = screen.getByRole('searchbox', { name: '검색' });
  await userEvent.clear(box);
  await userEvent.type(box, `${text}{Enter}`);
}

/** 머리말 검색창 (012 T012, R6, FR-025). */
describe('SearchBox', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('#spring이면 태그 페이지로 간다', async () => {
    renderBox();
    await submit('#spring');
    expect(screen.getByTestId('where').textContent).toBe('/tags/spring');
  });

  it('#C++처럼 정규화가 바뀌어도 태그 주소 규칙대로 간다', async () => {
    renderBox();
    await submit('#C++');
    expect(screen.getByTestId('where').textContent).toBe('/tags/c++');
  });

  it('# spring은 공백이 있어 보통 검색', async () => {
    renderBox();
    await submit('# spring');
    expect(screen.getByTestId('where').textContent).toBe(
      `/search?q=${encodeURIComponent('# spring')}`,
    );
  });

  it('보통 검색어는 /search?q=로 간다', async () => {
    renderBox();
    await submit('  트랜잭션 격리 ');
    expect(screen.getByTestId('where').textContent).toBe(
      `/search?q=${encodeURIComponent('트랜잭션 격리')}`,
    );
  });

  it('#만 있거나 남는 단어가 없으면 요청·이동 없이 "두 글자 이상 입력해 주세요"', async () => {
    const fetchMock = stubFetch({});
    renderBox();

    await submit('#');
    expect(screen.getByRole('alert')).toHaveTextContent('두 글자 이상 입력해 주세요');
    expect(screen.getByTestId('where').textContent).toBe('/');

    await submit('a b c');
    expect(screen.getByRole('alert')).toHaveTextContent('두 글자 이상 입력해 주세요');
    expect(screen.getByTestId('where').textContent).toBe('/');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
