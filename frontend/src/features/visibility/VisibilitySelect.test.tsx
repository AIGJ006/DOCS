import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { onNotFound, resetClientForTests } from '../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import VisibilitySelect from './VisibilitySelect';

function setCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=token-1; path=/';
}

/** 공개 범위 선택 (004 T037, FR-016·FR-020, US1). */
describe('VisibilitySelect', () => {
  beforeEach(() => {
    resetClientForTests();
    setCsrfCookie();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('선택지는 "🌐 전체 공개"·"🔒 나만 보기" 두 개뿐이다 (친구 공개는 기본 비활성)', () => {
    render(<VisibilitySelect value="PUBLIC" onChange={() => {}} />);

    const select = screen.getByRole('combobox', { name: '공개 범위' });
    const options = Array.from((select as HTMLSelectElement).options).map((o) => [
      o.value,
      o.textContent,
    ]);
    expect(options).toEqual([
      ['PUBLIC', '🌐 전체 공개'],
      ['PRIVATE', '🔒 나만 보기'],
    ]);
    expect(select).toHaveValue('PUBLIC');
  });

  it('값만 고르는 모드는 서버를 부르지 않고 onChange만 부른다 (발행 설정)', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({});
    const onChange = vi.fn();
    render(<VisibilitySelect value="PUBLIC" onChange={onChange} />);

    await user.selectOptions(screen.getByRole('combobox', { name: '공개 범위' }), 'PRIVATE');

    expect(onChange).toHaveBeenCalledWith('PRIVATE');
    expect(mock).not.toHaveBeenCalled();
  });

  it('즉시 저장 모드는 PUT /api/posts/{id}/visibility 를 부르고 응답 값으로 표시를 바꾼다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'PUT /api/posts/7/visibility': () =>
        json(200, { visibility: 'PRIVATE', firstPublicAt: '2026-10-01T02:00:00.123456Z' }),
    });
    const onSaved = vi.fn();
    render(<VisibilitySelect postId={7} value="PUBLIC" onSaved={onSaved} />);

    await user.selectOptions(screen.getByRole('combobox', { name: '공개 범위' }), 'PRIVATE');

    await waitFor(() =>
      expect(onSaved).toHaveBeenCalledWith({
        visibility: 'PRIVATE',
        firstPublicAt: '2026-10-01T02:00:00.123456Z',
      }),
    );
    expect(screen.getByRole('combobox', { name: '공개 범위' })).toHaveValue('PRIVATE');
    const calls = requestsTo(mock, 'PUT', '/api/posts/7/visibility');
    expect(calls).toHaveLength(1);
    const init = calls[0]?.[1];
    expect(JSON.parse(String(init?.body))).toEqual({ visibility: 'PRIVATE' });
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('token-1');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('400이면 "공개 범위를 다시 선택해 주세요"를 보이고 이전 값으로 되돌린다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'PUT /api/posts/7/visibility': () =>
        json(
          400,
          errorBody('INVALID_VISIBILITY', '공개 범위를 다시 선택해 주세요', [
            {
              field: 'visibility',
              code: 'INVALID_VISIBILITY',
              message: '허용되지 않은 공개 범위예요',
            },
          ]),
        ),
    });
    render(<VisibilitySelect postId={7} value="PUBLIC" />);

    await user.selectOptions(screen.getByRole('combobox', { name: '공개 범위' }), 'PRIVATE');

    expect(await screen.findByRole('alert')).toHaveTextContent('공개 범위를 다시 선택해 주세요');
    expect(screen.getByRole('combobox', { name: '공개 범위' })).toHaveValue('PUBLIC');
  });

  it('404면 공통 404 화면으로 넘어간다 (onNotFound)', async () => {
    const user = userEvent.setup();
    stubFetch({
      'PUT /api/posts/7/visibility': () =>
        json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });
    const notFound = vi.fn();
    const off = onNotFound(notFound);
    render(<VisibilitySelect postId={7} value="PUBLIC" />);

    await act(async () => {
      await user.selectOptions(screen.getByRole('combobox', { name: '공개 범위' }), 'PRIVATE');
    });

    await waitFor(() => expect(notFound).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('combobox', { name: '공개 범위' })).toHaveValue('PUBLIC');
    off();
  });

  it('확인 함수가 false를 돌려주면 부르지 않고 그대로 둔다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({});
    const confirm = vi.fn(async () => false);
    render(<VisibilitySelect postId={7} value="PRIVATE" confirm={confirm} />);

    await user.selectOptions(screen.getByRole('combobox', { name: '공개 범위' }), 'PUBLIC');

    await waitFor(() => expect(confirm).toHaveBeenCalledWith('PRIVATE', 'PUBLIC'));
    expect(mock).not.toHaveBeenCalled();
    expect(screen.getByRole('combobox', { name: '공개 범위' })).toHaveValue('PRIVATE');
  });

  it('설정 화면의 기본 공개 범위도 같은 선택지·라벨 목록을 쓴다 (004 T057, 서버 값 검사와 같은 허용값)', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const mock = stubFetch({});
    vi.stubGlobal('fetch', mock);
    render(<VisibilitySelect label="새 글 기본 공개 범위" value="PUBLIC" onChange={onChange} />);

    const select = screen.getByRole('combobox', { name: '새 글 기본 공개 범위' });
    expect(Array.from((select as HTMLSelectElement).options).map((o) => o.textContent)).toEqual([
      '🌐 전체 공개',
      '🔒 나만 보기',
    ]);
    await user.selectOptions(select, 'PRIVATE');
    expect(onChange).toHaveBeenCalledWith('PRIVATE');
    expect(mock).not.toHaveBeenCalled();
  });
});
