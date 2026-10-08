import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import SnippetText from '../SnippetText';

/** 주변 문장 강조 (012 T012, FR-033·FR-034, SC-005). */
describe('SnippetText', () => {
  it('marks 범위만 <mark>로 감싸고 나머지는 글자 그대로', () => {
    render(
      <SnippetText
        snippet={{
          text: '…스프링 트랜잭션 전파와 트랜잭션 격리…',
          marks: [
            [5, 9],
            [14, 18],
          ],
        }}
      />,
    );

    const marks = screen.getByTestId('snippet-text').querySelectorAll('mark');
    expect(Array.from(marks, (mark) => mark.textContent)).toEqual(['트랜잭션', '트랜잭션']);
    expect(screen.getByTestId('snippet-text').textContent).toBe(
      '…스프링 트랜잭션 전파와 트랜잭션 격리…',
    );
  });

  it('<script> 글자는 텍스트 노드다 — 요소가 생기지 않는다', () => {
    const text = '<script>alert(1)</script> 트랜잭션';
    render(<SnippetText snippet={{ text, marks: [[26, 30]] }} />);

    const root = screen.getByTestId('snippet-text');
    expect(root.querySelector('script')).toBeNull();
    expect(root.textContent).toBe(text);
    expect(root.querySelector('mark')?.textContent).toBe('트랜잭션');
  });

  it('marks가 비면 강조 없이 글자만', () => {
    render(<SnippetText snippet={{ text: '요약 그대로', marks: [] }} />);

    const root = screen.getByTestId('snippet-text');
    expect(root.querySelector('mark')).toBeNull();
    expect(root.textContent).toBe('요약 그대로');
  });

  it('겹치거나 글자 밖인 범위는 건너뛴다', () => {
    render(
      <SnippetText
        snippet={{
          text: 'abcdef',
          marks: [
            [0, 2],
            [1, 3],
            [4, 99],
          ],
        }}
      />,
    );

    const root = screen.getByTestId('snippet-text');
    expect(Array.from(root.querySelectorAll('mark'), (m) => m.textContent)).toEqual(['ab']);
    expect(root.textContent).toBe('abcdef');
  });
});
