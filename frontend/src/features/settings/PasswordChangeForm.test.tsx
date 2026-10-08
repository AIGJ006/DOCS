import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { errorBody, json, stubFetch } from '../../test/fetchRoutes';
import PasswordChangeForm from './PasswordChangeForm';

beforeEach(() => {
  resetClientForTests();
});

async function fill(current: string, next: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('현재 비밀번호'), current);
  await user.type(screen.getByLabelText('새 비밀번호'), next);
  await user.type(screen.getByLabelText('새 비밀번호 확인'), next);
  await user.click(screen.getByRole('button', { name: '비밀번호 변경' }));
}

describe('PasswordChangeForm', () => {
  it('소셜 계정(passwordChangeAvailable=false)이면 그리지 않는다', () => {
    const { container } = render(<PasswordChangeForm available={false} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('성공하면 다른 기기 로그아웃 안내', async () => {
    stubFetch({ 'POST /api/me/password': () => new Response(null, { status: 204 }) });
    render(<PasswordChangeForm available />);
    await fill('Blog#2026a', 'Fresh#2026b');
    expect(await screen.findByRole('status')).toHaveTextContent('다른 기기에서는 로그아웃됐어요');
  });

  it('현재 비밀번호가 틀리면 문구', async () => {
    stubFetch({
      'POST /api/me/password': () =>
        json(400, errorBody('CURRENT_PASSWORD_MISMATCH', '현재 비밀번호가 올바르지 않아요')),
    });
    render(<PasswordChangeForm available />);
    await fill('Wrong#2026x', 'Fresh#2026b');
    expect(await screen.findByRole('alert')).toHaveTextContent('현재 비밀번호가 올바르지 않아요');
  });

  it('잠금(429)이면 남은 시간', async () => {
    stubFetch({
      'POST /api/me/password': () =>
        json(
          429,
          errorBody('PASSWORD_CHANGE_TEMPORARILY_LOCKED', '잠시 후 다시 시도해 주세요(약 15분)'),
          { 'Retry-After': '840' },
        ),
    });
    render(<PasswordChangeForm available />);
    await fill('Blog#2026a', 'Fresh#2026b');
    expect(await screen.findByRole('alert')).toHaveTextContent('약 14분 남았어요');
  });
});
