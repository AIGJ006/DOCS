/**
 * 에디터 사진 넣기 (003 T034, FR-020·FR-032, research R12). textarea 붙여넣기·끌어놓기·[사진] 버튼으로 고른 사진을
 * 커서 위치에 대기 표시로 넣고, 한 장씩 차례로 올린 뒤 표시를 `![](주소)`로 한 번에 바꾼다. 대체글은 비워 둔다(발행 때
 * 권유, US5). 표시는 고유한 글자라 업로드 중 다른 곳을 고쳐도 위치를 잃지 않는다. 자동 저장은 막지 않는다 — 대기 표시도
 * 본문의 일부로 저장되고, 교체되면 다시 저장된다.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChangeEvent, ClipboardEvent, DragEvent, InputHTMLAttributes, RefObject } from 'react';
import { uploadImage, type UploadResult } from './uploadImage';
import { RETRY_LATER } from './uploadMessages';

export const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';

export interface ImageInsertOptions {
  textareaRef: RefObject<HTMLTextAreaElement | null>;
  /** 지금 본문 (렌더 사이 최신값) */
  getContent: () => string;
  /** 본문을 바꾼다 (자동 저장 대기열에도 알린다) */
  setContent: (value: string) => void;
  upload?: (file: Blob) => Promise<UploadResult>;
  /** 보관 대상(`pending`)일 때 본문에 넣을 글자(`![](local:…)`)를 돌려준다. 없으면 표시를 지우고 안내만 한다(US3) */
  onPending?: (file: Blob) => Promise<string | null>;
  onRejected?: (message: string) => void;
}

interface Job {
  file: Blob;
  token: string;
  /** 넣은 글자 전체 (앞 줄바꿈 포함) */
  piece: string;
}

let sequence = 0;

function placeholder(): string {
  sequence += 1;
  return `![사진 올리는 중…](uploading:${Date.now().toString(36)}-${sequence})`;
}

export function useImageInsert(options: ImageInsertOptions) {
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });
  const [uploading, setUploading] = useState(0);
  const queue = useRef<Job[]>([]);
  const running = useRef(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const replace = useCallback((job: Job, text: string | null) => {
    const { getContent, setContent } = optionsRef.current;
    const content = getContent();
    let next: string;
    if (text === null) {
      next = content.includes(job.piece)
        ? content.replace(job.piece, '')
        : content.replace(job.token, '');
    } else {
      next = content.replace(job.token, text);
    }
    if (next !== content) {
      setContent(next);
    }
  }, []);

  const run = useCallback(async () => {
    if (running.current) return;
    running.current = true;
    try {
      while (queue.current.length > 0) {
        const job = queue.current[0];
        const { upload = uploadImage, onPending, onRejected } = optionsRef.current;
        let result: UploadResult;
        try {
          result = await upload(job.file);
        } catch {
          result = { kind: 'pending' };
        }
        if (result.kind === 'uploaded') {
          replace(job, `![](${result.image.url})`);
        } else if (result.kind === 'pending') {
          const local = onPending ? await onPending(job.file) : null;
          replace(job, local);
          if (local === null) onRejected?.(RETRY_LATER);
        } else {
          replace(job, null);
          onRejected?.(result.message);
        }
        queue.current.shift();
        setUploading(queue.current.length);
      }
    } finally {
      running.current = false;
    }
  }, [replace]);

  const insertFiles = useCallback(
    (files: ArrayLike<File> | null | undefined) => {
      const list = files ? Array.from(files) : [];
      if (list.length === 0) return;
      const { textareaRef, getContent, setContent } = optionsRef.current;
      const content = getContent();
      const textarea = textareaRef.current;
      const at = textarea
        ? Math.min(textarea.selectionStart ?? content.length, content.length)
        : content.length;
      const needsBreak = at > 0 && content[at - 1] !== '\n';
      const jobs: Job[] = list.map((file, i) => {
        const token = placeholder();
        return { file, token, piece: `${i === 0 && needsBreak ? '\n' : ''}${token}\n` };
      });
      const block = jobs.map((j) => j.piece).join('');
      setContent(content.slice(0, at) + block + content.slice(at));
      if (textarea) {
        const caret = at + block.length;
        requestAnimationFrame(() => textarea.setSelectionRange(caret, caret));
      }
      queue.current.push(...jobs);
      setUploading(queue.current.length);
      void run();
    },
    [run],
  );

  const onPaste = useCallback(
    (event: ClipboardEvent<HTMLTextAreaElement>) => {
      const files = event.clipboardData?.files;
      if (files && files.length > 0) {
        event.preventDefault();
        insertFiles(files);
      }
    },
    [insertFiles],
  );

  const onDrop = useCallback(
    (event: DragEvent<HTMLTextAreaElement>) => {
      const files = event.dataTransfer?.files;
      if (files && files.length > 0) {
        event.preventDefault();
        insertFiles(files);
      }
    },
    [insertFiles],
  );

  const onDragOver = useCallback((event: DragEvent<HTMLTextAreaElement>) => {
    if (event.dataTransfer?.types?.includes?.('Files')) {
      event.preventDefault();
    }
  }, []);

  const openPicker = useCallback(() => inputRef.current?.click(), []);

  const fileInputProps: InputHTMLAttributes<HTMLInputElement> & {
    ref: RefObject<HTMLInputElement>;
  } = {
    ref: inputRef,
    type: 'file',
    accept: ACCEPT,
    multiple: true,
    hidden: true,
    onChange: (event: ChangeEvent<HTMLInputElement>) => {
      insertFiles(event.target.files);
      event.target.value = '';
    },
  };

  return { onPaste, onDrop, onDragOver, openPicker, insertFiles, fileInputProps, uploading };
}
