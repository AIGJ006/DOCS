import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostCard as PostCardData } from '../../api/types/reading';
import PostCard from '../PostCard';

const card: PostCardData = {
  id: 42,
  url: '/@kim755030/posts/42',
  title: 'JPA N+1 정리 — 아주 긴 제목이라 한 줄에서 잘린다',
  excerpt: '지연 로딩으로 연관 엔티티를 조회할 때 생기는 문제',
  thumbnailUrl: 'http://localhost:9000/blog/images/3f2a_thumb.webp',
  firstPublicAt: '2026-10-02T14:03:12.123456Z',
  commentCount: 3,
  likeCount: 12,
  author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
};

function renderCard(data: Partial<PostCardData> = {}, showAuthor = true) {
  return render(
    <MemoryRouter>
      <PostCard card={{ ...card, ...data }} showAuthor={showAuthor} />
    </MemoryRouter>,
  );
}

/** 카드 부품 (005 FR-007~014). */
describe('PostCard', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('썸네일은 화면에 가까워질 때 불러오고 대체글은 제목이다', () => {
    renderCard();
    const image = screen.getByRole('img', { name: card.title });
    expect(image).toHaveAttribute('loading', 'lazy');
    expect(image).toHaveAttribute('src', card.thumbnailUrl!);
  });

  it('썸네일이 없으면 사진 대신 같은 크기 빈 영역을 둔다', () => {
    renderCard({ thumbnailUrl: null });
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    const placeholder = screen.getByTestId('card-thumb');
    expect(placeholder).toHaveAttribute('data-empty', 'true');
  });

  it('제목은 1줄로 자르고 전체 제목을 title 속성에 담는다', () => {
    renderCard();
    const title = screen.getByTestId('card-title');
    expect(title).toHaveAttribute('title', card.title);
    expect(title).toHaveTextContent(card.title);
  });

  it('요약은 3줄이고 비어도 3줄 높이를 지킨다', () => {
    renderCard({ excerpt: null });
    const excerpt = screen.getByTestId('card-excerpt');
    expect(excerpt).toHaveTextContent('');
    expect(excerpt.style.minHeight).not.toBe('');
    expect(
      excerpt.style.webkitLineClamp === '3' ||
        excerpt.style.getPropertyValue('-webkit-line-clamp') === '3',
    ).toBe(true);
  });

  it('댓글 수·좋아요 수는 0도 보여준다', () => {
    renderCard({ commentCount: 0, likeCount: 0 });
    expect(screen.getByTestId('card-comments')).toHaveTextContent('0');
    expect(screen.getByTestId('card-likes')).toHaveTextContent('0');
  });

  it('카드 전체 링크와 작성자 링크 모두 키보드로 이동할 수 있다', () => {
    renderCard();
    const links = screen.getAllByRole('link');
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/@kim755030/posts/42',
      '/@kim755030',
    ]);
    for (const link of links) {
      expect(link.tabIndex).toBeGreaterThanOrEqual(0);
    }
    // 카드 링크 안에 제목·요약·썸네일이 있다
    const post = links[0];
    expect(post).toHaveTextContent(card.title);
  });

  it('showAuthor가 false면 작성자 영역이 없다', () => {
    renderCard({}, false);
    expect(screen.queryByText('김민서')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /김민서/ })).not.toBeInTheDocument();
  });

  it('프로필 사진이 없으면 기본 아이콘을 보여준다', () => {
    renderCard();
    const chip = screen.getByTestId('author-chip');
    expect(within(chip).getByTestId('default-avatar')).toBeInTheDocument();
    expect(within(chip).queryByTestId('author-avatar')).not.toBeInTheDocument();
  });

  it('프로필 사진이 있으면 작은 사진을 보여준다', () => {
    renderCard({
      author: { ...card.author, profileImageUrl: 'http://localhost:9000/blog/p_thumb.webp' },
    });
    const chip = screen.getByTestId('author-chip');
    expect(within(chip).getByTestId('author-avatar')).toHaveAttribute(
      'src',
      'http://localhost:9000/blog/p_thumb.webp',
    );
    expect(within(chip).queryByTestId('default-avatar')).not.toBeInTheDocument();
  });

  it('날짜는 최초 공개 일자다', () => {
    renderCard();
    expect(screen.getByText('2026.10.02')).toHaveAttribute('datetime', '2026-10-02T14:03:12.123Z');
  });
});
