import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import type { CommentView } from '../../../api/types/comments';
import type { ViewerFlags } from '../../../api/types/viewerFlags';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import CommentForm from '../CommentForm';
import { GUEST, MEMBER, comment } from './commentFixtures';

function renderForm(viewer: ViewerFlags, onCreated: (view: CommentView) => void = () => {}) {
  return render(
    <MemoryRouter initialEntries={['/@kim/posts/7']}>
      <CommentForm postId={7} viewer={viewer} onCreated={onCreated} />
    </MemoryRouter>,
  );
}

/** 댓글 입력칸 (007 T030, US2 #4~#7·#10, FR-023·024). */
describe('CommentForm', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('비회원 → "로그인하고 댓글을 남겨 보세요 [로그인]" (지금 글로 돌아오는 링크)', () => {
    renderForm(GUEST);

    expect(screen.getByText('로그인하고 댓글을 남겨 보세요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F%40kim%2Fposts%2F7',
    );
    expect(screen.queryByLabelText('댓글 입력')).toBeNull();
  });

  it('인증 전 → "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"(001 재발송 API)', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'POST /api/auth/email-verification': () => new Response(null, { status: 204 }),
    });
    renderForm({ ...MEMBER, emailVerified: false });

    expect(screen.getByText('이메일 인증 후 댓글을 쓸 수 있어요')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '인증 메일 다시 보내기' }));
    expect(await screen.findByText('인증 메일을 보냈어요')).toBeInTheDocument();
    expect(requestsTo(mock, 'POST', '/api/auth/email-verification')).toHaveLength(1);
  });

  it('글자 수는 코드 포인트로 센다 (이모지 1자)', async () => {
    const user = userEvent.setup();
    renderForm(MEMBER);

    await user.type(screen.getByLabelText('댓글 입력'), '😀가');

    expect(screen.getByTestId('comment-counter')).toHaveTextContent('2/1000');
  });

  it('등록 중에는 "등록 중…" 비활성, 성공하면 입력을 비우고 알린다', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const created = comment({ id: 5, content: '안녕', mine: true });
    const mock = stubFetch({
      'POST /api/posts/7/comments': () =>
        new Promise<Response>((resolve) => {
          release = () => resolve(json(201, created));
        }),
    });
    const onCreated = vi.fn();
    renderForm(MEMBER, onCreated);

    await user.type(screen.getByLabelText('댓글 입력'), '안녕');
    await user.click(screen.getByRole('button', { name: '등록' }));

    const busy = await screen.findByRole('button', { name: '등록 중…' });
    expect(busy).toBeDisabled();
    await waitFor(() => expect(requestsTo(mock, 'POST', '/api/posts/7/comments')).toHaveLength(1));
    release();
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
    expect(screen.getByLabelText('댓글 입력')).toHaveValue('');
    expect(screen.getByRole('button', { name: '등록' })).toBeEnabled();
  });

  it('칸 오류면 문구를 보이고 입력을 지우지 않는다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'POST /api/posts/7/comments': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력값을 확인해 주세요', [
            { field: 'content', code: 'COMMENT_REQUIRED', message: '댓글 내용을 입력해 주세요' },
          ]),
        ),
    });
    renderForm(MEMBER);

    await user.type(screen.getByLabelText('댓글 입력'), '   ');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('댓글 내용을 입력해 주세요');
    expect(screen.getByLabelText('댓글 입력')).toHaveValue('   ');
  });

  it.each([
    [429, 'TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'],
    [503, 'AUTOSAVE_UNAVAILABLE', '잠시 후 다시 저장할게요'],
  ])('%s %s → "잠시 후 다시 시도해 주세요", 입력 유지', async (status, code, message) => {
    const user = userEvent.setup();
    stubFetch({
      'POST /api/posts/7/comments': () =>
        json(status, errorBody(code, message), { 'Retry-After': '30' }),
    });
    renderForm(MEMBER);

    await user.type(screen.getByLabelText('댓글 입력'), '열한 번째');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(screen.getByLabelText('댓글 입력')).toHaveValue('열한 번째');
  });

  it('1000자를 넘으면 글자 수를 강조하지만 판정은 서버에 맡긴다', async () => {
    const user = userEvent.setup();
    renderForm(MEMBER);

    const box = screen.getByLabelText('댓글 입력');
    await user.click(box);
    await user.paste('가'.repeat(1001));

    const counter = screen.getByTestId('comment-counter');
    expect(counter).toHaveTextContent('1001/1000');
    expect(counter).toHaveAttribute('data-over', 'true');
  });
});
