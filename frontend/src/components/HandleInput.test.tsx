import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { toHandleChars } from '../features/handle/handleChars';
import HandleInput from './HandleInput';

function Harness({ prefix, initialEmail = '' }: { prefix?: 'go-' | 'gi-'; initialEmail?: string }) {
  const [email, setEmail] = useState(initialEmail);
  const [handle, setHandle] = useState('');
  return (
    <>
      <label htmlFor="email">이메일</label>
      <input id="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <HandleInput
        id="handle"
        label="블로그 주소"
        value={handle}
        onChange={setHandle}
        prefix={prefix}
        sourceEmail={prefix ? undefined : email}
      />
      <output data-testid="value">{handle}</output>
    </>
  );
}

describe('HandleInput', () => {
  it('이메일로 미리 채우고, 직접 고친 뒤에는 이메일을 바꿔도 다시 채우지 않는다 (US3 #3)', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText('이메일'), 'kim.min@naver.com');
    expect(screen.getByLabelText('블로그 주소')).toHaveValue('kim_min');
    expect(screen.getByText(/이메일 앞부분으로 미리 채웠어요/)).toBeInTheDocument();

    await user.clear(screen.getByLabelText('블로그 주소'));
    await user.type(screen.getByLabelText('블로그 주소'), 'mine');
    await user.type(screen.getByLabelText('이메일'), 'x');
    expect(screen.getByLabelText('블로그 주소')).toHaveValue('mine');
  });

  it('대문자는 소문자로, -는 입력되지 않는다', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText('블로그 주소'), 'Kim-Min');
    expect(screen.getByLabelText('블로그 주소')).toHaveValue('kimmin');
  });

  it('두벌식 자모는 같은 자리 영문으로 바꾼다 (ㅏ → k)', () => {
    expect(toHandleChars('ㅏ')).toBe('k');
    expect(toHandleChars('ㅏㅑㅓ')).toBe('kij');
    expect(toHandleChars('김')).toBe('rla');
    expect(toHandleChars('뷁')).toBe('qnpfr');
    expect(toHandleChars('Kim_7-')).toBe('kim_7');
  });

  it('소셜 모드에서는 접두어를 고정 글자로 보이고 고칠 수 없다', async () => {
    const user = userEvent.setup();
    render(<Harness prefix="go-" />);
    expect(screen.getAllByText('go-').length).toBeGreaterThan(0);
    const input = screen.getByLabelText('블로그 주소');
    await user.type(input, 'abc');
    await user.type(input, '{Home}{Backspace}{Backspace}');
    expect(input).toHaveValue('abc');
    expect(screen.getByTestId('value')).toHaveTextContent(/^abc$/);
  });

  it('주소 입력 속성(inputmode=url, autocapitalize=off)과 "가입 후 바꿀 수 없어요" 안내', () => {
    render(<Harness />);
    const input = screen.getByLabelText('블로그 주소');
    expect(input).toHaveAttribute('inputmode', 'url');
    expect(input).toHaveAttribute('autocapitalize', 'off');
    expect(screen.getByText('블로그 주소는 가입 후 바꿀 수 없어요')).toBeInTheDocument();
  });
});
