import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import ReportDialog from '../ReportDialog';

function renderDialog(overrides: Partial<Parameters<typeof ReportDialog>[0]> = {}) {
  const props = {
    targetType: 'POST' as const,
    targetId: 42,
    onClose: vi.fn(),
    onDone: vi.fn(),
    onGate: vi.fn(() => false),
    ...overrides,
  };
  render(<ReportDialog {...props} />);
  return props;
}

describe('ReportDialog', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=t';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('사유 6개 라디오가 있고 고르기 전에는 [신고하기]가 비활성', () => {
    stubFetch({});
    renderDialog();
    expect(screen.getAllByRole('radio')).toHaveLength(6);
    expect(screen.getByRole('radio', { name: '스팸·광고' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '신고하기' })).toBeDisabled();
  });

  it('기타일 때만 설명 칸·200자 카운터가 보이고 빈 설명이면 비활성', async () => {
    stubFetch({});
    renderDialog();
    expect(screen.queryByRole('textbox')).toBeNull();
    await userEvent.click(screen.getByRole('radio', { name: '기타' }));
    const box = screen.getByRole('textbox', { name: '설명' });
    expect(screen.getByText('0/200')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '신고하기' })).toBeDisabled();
    await userEvent.type(box, '   ');
    expect(screen.getByRole('button', { name: '신고하기' })).toBeDisabled();
    await userEvent.type(box, '광고 링크');
    expect(screen.getByRole('button', { name: '신고하기' })).toBeEnabled();
    expect(box).toHaveAttribute('maxLength', '200');
    await userEvent.click(screen.getByRole('radio', { name: '욕설·혐오' }));
    expect(screen.queryByRole('textbox')).toBeNull();
  });

  it('성공하면 요청 본문을 보내고 onDone', async () => {
    const fetchMock = stubFetch({ 'POST /api/reports': () => json(200, { accepted: true }) });
    const props = renderDialog();
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    await waitFor(() => expect(props.onDone).toHaveBeenCalled());
    const [, init] = requestsTo(fetchMock, 'POST', '/api/reports')[0];
    expect(JSON.parse(String(init?.body))).toEqual({
      targetType: 'POST',
      targetId: 42,
      reason: 'SPAM',
      detail: null,
    });
  });

  it('404면 "볼 수 없는 글이에요", 429면 "잠시 후 다시 시도해 주세요"', async () => {
    let status = 404;
    stubFetch({
      'POST /api/reports': () =>
        status === 404
          ? json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요'))
          : json(429, errorBody('TOO_MANY_REQUESTS', '요청이 너무 많아요'), { 'Retry-After': '30' }),
    });
    const props = renderDialog();
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    expect(await screen.findByText('볼 수 없는 글이에요')).toBeInTheDocument();
    status = 429;
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    expect(await screen.findByText('잠시 후 다시 시도해 주세요')).toBeInTheDocument();
    expect(props.onDone).not.toHaveBeenCalled();
  });

  it('401·403은 onGate에 넘긴다', async () => {
    stubFetch({
      'POST /api/reports': () => json(403, errorBody('EMAIL_NOT_VERIFIED', '이메일 인증 후 이용할 수 있어요')),
    });
    const onGate = vi.fn(() => true);
    renderDialog({ onGate });
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    await waitFor(() => expect(onGate).toHaveBeenCalled());
  });

  it('Esc·[취소]는 요청 없이 닫는다', async () => {
    const fetchMock = stubFetch({});
    const props = renderDialog();
    await userEvent.click(screen.getByRole('button', { name: '취소' }));
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(props.onClose).toHaveBeenCalledTimes(2);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('Tab은 창 안에서만 돈다', async () => {
    stubFetch({});
    renderDialog();
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    const cancel = screen.getByRole('button', { name: '취소' });
    const submit = screen.getByRole('button', { name: '신고하기' });
    submit.focus();
    await userEvent.tab();
    expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);
    cancel.focus();
    expect(document.activeElement).toBe(cancel);
  });
});
