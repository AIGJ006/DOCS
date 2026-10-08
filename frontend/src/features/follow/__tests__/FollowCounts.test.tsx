import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import FollowCounts from '../FollowCounts';

/** 블로그 머리말 수 한 줄 (010 T033, FR-013). */
describe('FollowCounts', () => {
  it('"공개 글 N · 팔로워 N · 팔로잉 N", 팔로워·팔로잉은 목록 링크, 천 단위 쉼표', () => {
    render(
      <MemoryRouter>
        <FollowCounts handle="na_ms" publicPostCount={24} followerCount={1234} followingCount={0} />
      </MemoryRouter>,
    );
    const counts = screen.getByTestId('follow-counts');
    expect(counts).toHaveTextContent('공개 글 24·팔로워 1,234·팔로잉 0');
    expect(screen.getByRole('link', { name: '팔로워 1,234' })).toHaveAttribute(
      'href',
      '/@na_ms/followers',
    );
    expect(screen.getByRole('link', { name: '팔로잉 0' })).toHaveAttribute(
      'href',
      '/@na_ms/following',
    );
  });
});
