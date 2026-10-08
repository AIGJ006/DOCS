import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Toast from '../Toast';

/** 알림 메시지 (006 T013). */
describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('글자와 링크 버튼 하나를 role=status로 보이고 5초 뒤 닫힌다', () => {
    const onClose = vi.fn();
    render(
      <MemoryRouter>
        <Toast
          message={{
            text: '복구했어요',
            action: { label: '발행 글 탭에서 보기', to: '/manage/posts?tab=published' },
          }}
          onClose={onClose}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('status')).toHaveTextContent('복구했어요');
    expect(screen.getByRole('link', { name: '발행 글 탭에서 보기' })).toHaveAttribute(
      'href',
      '/manage/posts?tab=published',
    );
    act(() => vi.advanceTimersByTime(4_999));
    expect(onClose).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
