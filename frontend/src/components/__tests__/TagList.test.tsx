import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import TagList from '../TagList';

/** 글 상세 태그 링크 (005 T039 → 008 T038: 주소는 tagPath 하나로). */
describe('TagList', () => {
  it('링크가 tagPath 모양이다 (c#은 %23, c++는 + 그대로)', () => {
    render(
      <MemoryRouter>
        <TagList tags={['c#', 'c++', '스프링-부트', 'node.js']} />
      </MemoryRouter>,
    );
    const hrefs = screen.getAllByTestId('tag').map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual([
      '/tags/c%23',
      '/tags/c++',
      '/tags/%EC%8A%A4%ED%94%84%EB%A7%81-%EB%B6%80%ED%8A%B8',
      '/tags/node.js',
    ]);
    expect(screen.getByText('#c#')).toBeInTheDocument();
  });

  it('태그가 없으면 그리지 않는다', () => {
    const { container } = render(
      <MemoryRouter>
        <TagList tags={[]} />
      </MemoryRouter>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
