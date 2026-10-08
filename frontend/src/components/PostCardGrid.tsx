import type { ReactNode } from 'react';

/**
 * 카드 그리드 (005 FR-015). 375px~데스크톱 가로 스크롤 없음, 같은 줄 카드 높이 같음(`align-items: stretch`).
 * 한 줄 개수는 각자 정할 수 있게 CSS 변수 `--card-min-width`로 둔다(예시: ≥1024px 3개, 640~1023px 2개, <640px 1개).
 */
export interface PostCardGridProps {
  children: ReactNode;
}

export default function PostCardGrid({ children }: PostCardGridProps) {
  return (
    <div
      data-testid="post-card-grid"
      style={{
        display: 'grid',
        gridTemplateColumns:
          'repeat(auto-fill, minmax(min(100%, var(--card-min-width, 18rem)), 1fr))',
        gap: 'var(--card-gap, 1rem)',
        alignItems: 'stretch',
        width: '100%',
        boxSizing: 'border-box',
        maxWidth: '100%',
      }}
    >
      {children}
    </div>
  );
}
