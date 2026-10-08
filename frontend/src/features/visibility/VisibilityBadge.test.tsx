import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import VisibilityBadge from './VisibilityBadge';

/** 공개 범위 배지 (004 T037, FR-046): 🌐 전체 공개 / 👥 친구 공개 / 🔒 나만 보기. */
describe('VisibilityBadge', () => {
  it.each([
    ['PUBLIC', '🌐', '전체 공개'],
    ['FRIENDS', '👥', '친구 공개'],
    ['PRIVATE', '🔒', '나만 보기'],
  ] as const)('%s → %s %s', (value, icon, label) => {
    render(<VisibilityBadge visibility={value} />);

    const badge = screen.getByText(label).closest('[data-visibility]');
    expect(badge).toHaveAttribute('data-visibility', value);
    expect(badge).toHaveTextContent(`${icon} ${label}`);
    // 그림 글자는 화면 읽기 프로그램이 읽지 않는다
    expect(screen.getByText(icon)).toHaveAttribute('aria-hidden', 'true');
  });

  it('작게 보이기(compact)는 그림 글자만 보이고 스크린 리더용 글자 "공개"·"비공개"를 둔다', () => {
    const { rerender } = render(<VisibilityBadge visibility="PRIVATE" compact />);
    expect(screen.getByText('🔒')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('비공개')).toHaveClass('sr-only');
    expect(screen.queryByText('나만 보기')).toBeNull();

    rerender(<VisibilityBadge visibility="PUBLIC" compact />);
    expect(screen.getByText('🌐')).toBeInTheDocument();
    expect(screen.getByText('공개')).toHaveClass('sr-only');
  });
});
