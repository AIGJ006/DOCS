import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { useConfirm } from '../useConfirm';

function Harness({
  message = '휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요',
}: {
  message?: string;
}) {
  const { confirm, dialog } = useConfirm();
  const [result, setResult] = useState('none');
  return (
    <>
      <button
        type="button"
        onClick={async () => {
          setResult(String(await confirm({ message, confirmLabel: '휴지통으로' })));
        }}
      >
        열기
      </button>
      <output data-testid="result">{result}</output>
      {dialog}
    </>
  );
}

/** 공통 확인창 (006 T012). */
describe('ConfirmDialog', () => {
  it('확인 버튼에 처음 포커스가 가고 [확인]이면 true', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: '열기' }));

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription('휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요');
    expect(screen.getByRole('button', { name: '휴지통으로' })).toHaveFocus();
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));

    expect(screen.getByTestId('result')).toHaveTextContent('true');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '열기' })).toHaveFocus();
  });

  it('[취소]와 Esc는 false', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: '열기' }));
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(screen.getByTestId('result')).toHaveTextContent('false');

    await user.click(screen.getByRole('button', { name: '열기' }));
    await act(async () => {
      await user.keyboard('{Escape}');
    });
    expect(screen.getByTestId('result')).toHaveTextContent('false');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('Tab은 확인창 안에서만 돈다', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: '열기' }));

    await user.tab();
    expect(screen.getByRole('button', { name: '취소' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: '휴지통으로' })).toHaveFocus();
  });

  it('문구는 글자 그대로 보인다', async () => {
    const user = userEvent.setup();
    render(<Harness message="<img src=x onerror=alert(1)>" />);
    await user.click(screen.getByRole('button', { name: '열기' }));

    expect(screen.getByRole('dialog')).toHaveTextContent('<img src=x onerror=alert(1)>');
    expect(document.querySelector('img')).toBeNull();
  });
});
