import { act, fireEvent, render, screen } from '@testing-library/react';
import { useRef, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { UploadResult } from './uploadImage';
import { useImageInsert } from './useImageInsert';

interface Deferred {
  resolve: (result: UploadResult) => void;
}

function uploaded(url: string): UploadResult {
  return {
    kind: 'uploaded',
    image: {
      imageId: 1,
      url,
      thumbUrl: null,
      contentType: 'image/webp',
      width: 10,
      height: 10,
      sizeBytes: 10,
    },
  };
}

function Harness({
  upload,
  onRejected,
  initial = '',
}: {
  upload: (file: Blob) => Promise<UploadResult>;
  onRejected?: (message: string) => void;
  initial?: string;
}) {
  const [content, setContent] = useState(initial);
  const ref = useRef<HTMLTextAreaElement>(null);
  const latest = useRef(initial);
  const insert = useImageInsert({
    textareaRef: ref,
    getContent: () => latest.current,
    setContent: (value) => {
      latest.current = value;
      setContent(value);
    },
    upload,
    onRejected,
  });
  return (
    <div>
      <textarea
        aria-label="본문"
        ref={ref}
        value={content}
        onChange={(e) => {
          latest.current = e.target.value;
          setContent(e.target.value);
        }}
        onPaste={insert.onPaste}
        onDrop={insert.onDrop}
        onDragOver={insert.onDragOver}
      />
      <button type="button" onClick={insert.openPicker}>
        사진
      </button>
      <input {...insert.fileInputProps} data-testid="picker" />
      {insert.uploading > 0 ? <span>사진 올리는 중…</span> : null}
    </div>
  );
}

function image(name = 'IMG_0001.jpg') {
  return new File([new Uint8Array(4)], name, { type: 'image/jpeg' });
}

function setup(initial = '', onRejected?: (message: string) => void) {
  const pending: Deferred[] = [];
  const upload = vi.fn(
    () =>
      new Promise<UploadResult>((resolve) => {
        pending.push({ resolve });
      }),
  );
  render(<Harness upload={upload} initial={initial} onRejected={onRejected} />);
  const textarea = screen.getByLabelText('본문') as HTMLTextAreaElement;
  return { upload, pending, textarea };
}

describe('useImageInsert', () => {
  it('붙여넣기: 커서 위치에 대기 표시 → 성공 시 ![](주소)로 한 번에 교체 (대체글 비움)', async () => {
    const { pending, textarea } = setup('앞뒤');
    textarea.setSelectionRange(1, 1);

    await act(async () => {
      fireEvent.paste(textarea, { clipboardData: { files: [image()], getData: () => '' } });
    });

    expect(textarea.value).toMatch(/^앞\n?!\[사진 올리는 중…\]\(uploading:[^)]+\)\n?뒤$/);
    expect(screen.getByText('사진 올리는 중…')).toBeInTheDocument();

    await act(async () => {
      pending[0].resolve(uploaded('http://localhost:9000/blog/images/2026/10/a.webp'));
    });

    expect(textarea.value).toBe('앞\n![](http://localhost:9000/blog/images/2026/10/a.webp)\n뒤');
    expect(textarea.value).not.toContain('IMG_0001');
    expect(screen.queryByText('사진 올리는 중…')).toBeNull();
  });

  it('끌어놓기·[사진] 버튼도 같은 흐름이고, 여러 장은 순서대로', async () => {
    const { upload, pending, textarea } = setup('');

    await act(async () => {
      fireEvent.drop(textarea, { dataTransfer: { files: [image('a.jpg'), image('b.jpg')] } });
    });
    expect(upload).toHaveBeenCalledTimes(1);
    await act(async () => {
      pending[0].resolve(uploaded('http://s/images/2026/10/1.webp'));
    });
    expect(upload).toHaveBeenCalledTimes(2);
    await act(async () => {
      pending[1].resolve(uploaded('http://s/images/2026/10/2.webp'));
    });
    expect(textarea.value).toBe(
      '![](http://s/images/2026/10/1.webp)\n![](http://s/images/2026/10/2.webp)\n',
    );

    const picker = screen.getByTestId('picker') as HTMLInputElement;
    expect(picker.type).toBe('file');
    expect(picker.accept).toBe('image/jpeg,image/png,image/gif,image/webp');
    expect(picker.multiple).toBe(true);
    await act(async () => {
      fireEvent.change(picker, { target: { files: [image('c.jpg')] } });
    });
    expect(upload).toHaveBeenCalledTimes(3);
  });

  it('업로드 중 다른 입력이 있어도 표시 위치를 잃지 않는다', async () => {
    const { pending, textarea } = setup('');
    await act(async () => {
      fireEvent.paste(textarea, { clipboardData: { files: [image()], getData: () => '' } });
    });
    fireEvent.change(textarea, { target: { value: `제목 줄\n${textarea.value}끝 줄` } });

    await act(async () => {
      pending[0].resolve(uploaded('http://s/images/2026/10/x.webp'));
    });

    expect(textarea.value).toBe('제목 줄\n![](http://s/images/2026/10/x.webp)\n끝 줄');
  });

  it('거부되면 표시를 지우고 안내한다', async () => {
    const onRejected = vi.fn();
    const { pending, textarea } = setup('글', onRejected);
    textarea.setSelectionRange(1, 1);
    await act(async () => {
      fireEvent.paste(textarea, { clipboardData: { files: [image()], getData: () => '' } });
    });
    await act(async () => {
      pending[0].resolve({
        kind: 'rejected',
        code: 'IMAGE_REJECTED',
        message: '올릴 수 없는 사진이에요',
      });
    });
    expect(textarea.value).toBe('글');
    expect(onRejected).toHaveBeenCalledWith('올릴 수 없는 사진이에요');
  });

  it('사진이 없는 붙여넣기(글자)는 그대로 둔다', async () => {
    const { upload, textarea } = setup('');
    fireEvent.paste(textarea, { clipboardData: { files: [], getData: () => '글자' } });
    expect(upload).not.toHaveBeenCalled();
  });
});
