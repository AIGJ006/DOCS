import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import PeopleResultItem from '../PeopleResultItem';

/** 사람 검색 결과 한 줄 (012 T033, 33 §5). */
describe('PeopleResultItem', () => {
  it('닉네임·@주소·소개 첫 줄을 보이고 누르면 블로그로 간다', () => {
    render(
      <MemoryRouter>
        <ul>
          <PeopleResultItem
            person={{
              handle: 'kim755030',
              nickname: '김민서',
              profileImageUrl: '/images/p.webp',
              bioFirstLine: '백엔드 개발자',
            }}
          />
        </ul>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/@kim755030');
    expect(link).toHaveTextContent('김민서');
    expect(link).toHaveTextContent('@kim755030');
    expect(link).toHaveTextContent('백엔드 개발자');
    expect(link.querySelector('img')).toHaveAttribute('src', '/images/p.webp');
  });

  it('사진이 없으면 기본 아이콘, 소개가 없으면 줄 없음', () => {
    render(
      <MemoryRouter>
        <ul>
          <PeopleResultItem
            person={{
              handle: 'lee',
              nickname: '<b>이</b>',
              profileImageUrl: null,
              bioFirstLine: null,
            }}
          />
        </ul>
      </MemoryRouter>,
    );

    const link = screen.getByRole('link');
    expect(link.querySelector('b')).toBeNull();
    expect(link).toHaveTextContent('<b>이</b>');
    expect(link.querySelector('.people-item-bio')).toBeNull();
  });
});
