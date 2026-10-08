import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { TagCount } from '../../api/types/tags';
import BlogTagStrip from '../BlogTagStrip';

function tags(n: number): TagCount[] {
  return Array.from({ length: n }, (_, i) => ({ name: `t${i}`, postCount: n - i }));
}

function renderStrip(items: TagCount[], active?: string) {
  return render(
    <MemoryRouter>
      <BlogTagStrip handle="kim755030" items={items} initialVisible={10} active={active} />
    </MemoryRouter>,
  );
}

function links() {
  return within(screen.getByRole('list', { name: '블로그 태그' })).getAllByRole('link');
}

/** 블로그 태그 줄 (008 T057, US5 #1). */
describe('BlogTagStrip', () => {
  it('처음 10개와 [태그 더 보기 +남은 수], 누르면 나머지를 펼친다', async () => {
    const user = userEvent.setup();
    renderStrip(tags(13));

    expect(links()).toHaveLength(10);
    expect(links()[0]).toHaveTextContent('#t0 13');
    expect(links()[0]).toHaveAttribute('href', '/@kim755030?tag=t0');

    await user.click(screen.getByRole('button', { name: '태그 더 보기 +3' }));
    expect(links()).toHaveLength(13);
    expect(screen.queryByRole('button', { name: /태그 더 보기/ })).not.toBeInTheDocument();
  });

  it('10개 이하면 버튼이 없다', () => {
    renderStrip(tags(10));
    expect(links()).toHaveLength(10);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('태그가 없으면 줄을 숨긴다', () => {
    const { container } = renderStrip([]);
    expect(container).toBeEmptyDOMElement();
  });

  it('링크는 쿼리 값으로 인코딩하고 지금 필터는 aria-current', () => {
    renderStrip(
      [
        { name: 'c++', postCount: 2 },
        { name: 'c#', postCount: 1 },
        { name: '자바', postCount: 1 },
      ],
      'c#',
    );
    expect(links().map((a) => a.getAttribute('href'))).toEqual([
      '/@kim755030?tag=c%2B%2B',
      '/@kim755030?tag=c%23',
      '/@kim755030?tag=%EC%9E%90%EB%B0%94',
    ]);
    expect(links()[1]).toHaveAttribute('aria-current', 'true');
    expect(links()[0]).not.toHaveAttribute('aria-current');
  });
});
