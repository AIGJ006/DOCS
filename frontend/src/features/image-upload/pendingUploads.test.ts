import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadDraft, resetLocalDraftsForTests, saveDraft } from '../editor/localDraftStore';
import { createPendingRetrier, holdPending, localImageUrls, retryPending } from './pendingUploads';
import type { UploadResult } from './uploadImage';

const MEMBER = 7;
const POST = 42;

function photo(bytes = [1, 2, 3]) {
  return new File([new Uint8Array(bytes)], 'IMG_0001.jpg', { type: 'image/jpeg' });
}

function uploaded(url: string): UploadResult {
  return {
    kind: 'uploaded',
    image: {
      imageId: 1,
      url,
      thumbUrl: null,
      contentType: 'image/webp',
      width: 1,
      height: 1,
      sizeBytes: 1,
    },
  };
}

/** 본문 상태 (에디터 대신). */
function editor(initial: string) {
  let content = initial;
  const setContent = vi.fn((value: string) => {
    content = value;
  });
  return { getContent: () => content, setContent, current: () => content };
}

beforeEach(async () => {
  await resetLocalDraftsForTests();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('holdPending', () => {
  it('원래 파일을 임시 글의 pendingImages에 넣고 본문에 넣을 local: 표시를 돌려준다', async () => {
    const held = await holdPending(MEMBER, POST, photo([9, 8, 7]));

    expect(held.markdown).toBe(`![](local:${held.localId})`);
    const draft = await loadDraft(MEMBER, POST);
    expect(draft?.pendingImages).toHaveLength(1);
    expect(draft?.pendingImages[0].localId).toBe(held.localId);
    expect(draft?.pendingImages[0].type).toBe('image/jpeg');
    expect(Array.from(new Uint8Array(draft!.pendingImages[0].data))).toEqual([9, 8, 7]);
    expect(JSON.stringify(Object.keys(draft!.pendingImages[0]))).not.toContain('name');
  });

  it('자동 저장(pendingImages 없이 저장)이 대기 사진을 지우지 않는다', async () => {
    const held = await holdPending(MEMBER, POST, photo());
    await saveDraft(MEMBER, POST, {
      title: '제목',
      contentMd: held.markdown,
      baseVersion: 1,
      dirty: true,
      updatedAt: 1,
    });
    expect((await loadDraft(MEMBER, POST))?.pendingImages).toHaveLength(1);
  });

  it('미리보기용 blob: 주소를 만든다', async () => {
    const created = vi.fn(() => 'blob:http://localhost/abc');
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: created, revokeObjectURL: vi.fn() }),
    );
    const held = await holdPending(MEMBER, POST, photo());
    const urls = localImageUrls((await loadDraft(MEMBER, POST))!.pendingImages);
    expect(urls.get(held.localId)).toBe('blob:http://localhost/abc');
    vi.unstubAllGlobals();
  });
});

describe('retryPending', () => {
  it('차례로 다시 올리고 성공하면 본문을 교체하고 대기열에서 뺀다', async () => {
    const a = await holdPending(MEMBER, POST, photo([1]));
    const b = await holdPending(MEMBER, POST, photo([2]));
    const doc = editor(`첫\n${a.markdown}\n둘\n${b.markdown}\n`);
    const order: number[] = [];
    const upload = vi.fn(async (file: Blob) => {
      const first = new Uint8Array(await file.arrayBuffer())[0];
      order.push(first);
      return uploaded(`http://s/images/2026/10/${first}.webp`);
    });

    const result = await retryPending(MEMBER, POST, { ...doc, upload });

    expect(result).toEqual({ remaining: 0, failed: 0 });
    expect(order).toEqual([1, 2]);
    expect(doc.current()).toBe(
      '첫\n![](http://s/images/2026/10/1.webp)\n둘\n![](http://s/images/2026/10/2.webp)\n',
    );
    expect(doc.setContent).toHaveBeenCalled();
    expect((await loadDraft(MEMBER, POST))?.pendingImages).toEqual([]);
  });

  it('또 보관 대상이면 멈추고 남은 사진을 그대로 둔다', async () => {
    const a = await holdPending(MEMBER, POST, photo());
    await holdPending(MEMBER, POST, photo());
    const doc = editor(a.markdown);
    const upload = vi.fn(async (): Promise<UploadResult> => ({ kind: 'pending' }));

    const result = await retryPending(MEMBER, POST, { ...doc, upload });

    expect(result).toEqual({ remaining: 2, failed: 0 });
    expect(upload).toHaveBeenCalledTimes(1);
    expect(doc.current()).toBe(a.markdown);
  });

  it('다시 시도 중 409·429·400이면 대기열에서 빼고 local: 표시는 둔 채 안내한다', async () => {
    const a = await holdPending(MEMBER, POST, photo());
    const doc = editor(a.markdown);
    const onFailed = vi.fn();
    const upload = vi.fn(async (): Promise<UploadResult> => ({
      kind: 'rejected',
      code: 'STORAGE_QUOTA_EXCEEDED',
      message:
        '사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요',
    }));

    const result = await retryPending(MEMBER, POST, { ...doc, upload, onFailed });

    expect(result).toEqual({ remaining: 0, failed: 1 });
    expect(doc.current()).toBe(a.markdown);
    expect(onFailed).toHaveBeenCalledWith('업로드하지 못한 사진이 있어요');
    expect((await loadDraft(MEMBER, POST))?.pendingImages).toEqual([]);
  });
});

describe('createPendingRetrier', () => {
  it('online 이벤트 때 다시 시도하고, stop 뒤에는 듣지 않는다', async () => {
    const a = await holdPending(MEMBER, POST, photo());
    const doc = editor(a.markdown);
    const upload = vi.fn(async () => uploaded('http://s/images/2026/10/x.webp'));
    const retrier = createPendingRetrier(MEMBER, POST, { ...doc, upload, isOnline: () => false });
    retrier.start();
    expect(upload).not.toHaveBeenCalled();

    window.dispatchEvent(new Event('online'));
    await vi.waitFor(() => expect(doc.current()).toBe('![](http://s/images/2026/10/x.webp)'));
    retrier.stop();

    await holdPending(MEMBER, POST, photo());
    window.dispatchEvent(new Event('online'));
    await new Promise((r) => setTimeout(r, 20));
    expect(upload).toHaveBeenCalledTimes(1);
  });

  it('에디터를 열 때(start) 온라인이면 바로 다시 시도하고, 실패하면 점점 길게 기다린다(최대 5분)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const a = await holdPending(MEMBER, POST, photo());
    const doc = editor(a.markdown);
    let calls = 0;
    const upload = vi.fn(async (): Promise<UploadResult> => {
      calls += 1;
      return calls < 3 ? { kind: 'pending' } : uploaded('http://s/images/2026/10/y.webp');
    });
    const onCount = vi.fn();
    const retrier = createPendingRetrier(MEMBER, POST, {
      ...doc,
      upload,
      isOnline: () => true,
      onCount,
    });

    retrier.start();
    await vi.waitFor(() => expect(upload).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(2_000);
    await vi.waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
    await vi.advanceTimersByTimeAsync(4_000);
    await vi.waitFor(() => expect(doc.current()).toBe('![](http://s/images/2026/10/y.webp)'));
    expect(onCount).toHaveBeenLastCalledWith(0);
    expect(retrier.delayFor(20)).toBe(300_000);
    retrier.stop();
  });
});
