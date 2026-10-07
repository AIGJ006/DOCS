import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import SaveStatus from '../SaveStatus';

describe('SaveStatus', () => {
  it('저장됨은 서울 시각 HH:MM으로', () => {
    render(<SaveStatus status={{ kind: 'saved', savedAt: '2026-10-07T05:03:12Z' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('✓ 저장됨 14:03');
  });

  it('이 기기에만 저장됨', () => {
    render(<SaveStatus status={{ kind: 'local' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('● 이 기기에 저장됨 (동기화 대기)');
  });

  it('오프라인', () => {
    render(<SaveStatus status={{ kind: 'offline' }} />);
    expect(screen.getByRole('status')).toHaveTextContent(
      '⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화',
    );
  });

  it('충돌 — [비교하기]를 누를 수 있다', async () => {
    const onCompare = vi.fn();
    render(<SaveStatus status={{ kind: 'conflict' }} onCompare={onCompare} />);
    expect(screen.getByRole('status')).toHaveTextContent(
      '⚠ 다른 곳에서 수정됨 — 이 기기에만 저장 중',
    );
    await userEvent.setup().click(screen.getByRole('button', { name: '비교하기' }));
    expect(onCompare).toHaveBeenCalled();
  });

  it('글자로 알리고 화면 읽기 도구에 조용히 전한다', () => {
    render(<SaveStatus status={{ kind: 'local' }} />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
  });
});
