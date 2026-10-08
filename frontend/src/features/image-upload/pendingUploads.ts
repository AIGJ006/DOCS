/**
 * 업로드 대기 사진 (003 US3, research R12, FR-018·019). 오프라인·네트워크 오류·시간 초과·5xx로 올리지 못한 사진의 원래 파일을
 * 이 기기 임시 글(`draft:{memberId}:{postId}.pendingImages`)에 보관하고, 본문에는 `![](local:{localId})`를 넣는다. 화면은
 * `blob:` 주소로 보인다(PreviewPane). 발행은 002 `PENDING_IMAGES`가 막는다.
 *
 * 다시 올리기는 한 번에 하나씩. 에디터를 열 때와 `online` 이벤트 때 시작하고, 또 보관 대상이면 2초부터 두 배씩 최대 5분 기다린다.
 * 다시 시도 중 즉시 안내 대상 오류(400·409·429 등)가 나면 그 사진을 대기열에서 빼고 본문의 `local:` 표시는 그대로 둔 채
 * "업로드하지 못한 사진이 있어요"를 보인다 — 사용자가 지우거나 다시 넣는다.
 */
import {
  loadPendingImages,
  updatePendingImages,
  type PendingImage,
} from '../editor/localDraftStore';
import { uploadImage, type UploadResult } from './uploadImage';
import { PENDING_FAILED } from './uploadMessages';

export const LOCAL_SCHEME = 'local:';

const BASE_DELAY_MS = 2_000;
const MAX_DELAY_MS = 5 * 60_000;

function newLocalId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

async function toArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(blob);
  });
}

/** 보관한 사진의 원래 파일 (이름 없음). */
export function pendingBlob(image: PendingImage): Blob {
  return new Blob([image.data], { type: image.type });
}

/** 원래 파일을 보관하고 본문에 넣을 표시를 돌려준다. */
export async function holdPending(
  memberId: number,
  postId: number,
  file: Blob,
): Promise<{ localId: string; markdown: string }> {
  const localId = newLocalId();
  const image: PendingImage = {
    localId,
    type: file.type || 'application/octet-stream',
    data: await toArrayBuffer(file),
    heldAt: Date.now(),
  };
  await updatePendingImages(memberId, postId, (images) => [...images, image]);
  return { localId, markdown: `![](${LOCAL_SCHEME}${localId})` };
}

/** 대기 사진 → `blob:` 미리보기 주소. 쓰고 나면 `revokeObjectURL`로 놓는다. */
export function localImageUrls(images: PendingImage[]): Map<string, string> {
  const urls = new Map<string, string>();
  for (const image of images) {
    urls.set(image.localId, URL.createObjectURL(pendingBlob(image)));
  }
  return urls;
}

export interface RetryDeps {
  getContent: () => string;
  /** 본문을 바꾼다 (자동 저장 대기열이 변경으로 본다) */
  setContent: (value: string) => void;
  upload?: (file: Blob) => Promise<UploadResult>;
  onFailed?: (message: string) => void;
}

/** 본문의 `(local:{id})`를 바꾼다. */
function replaceLocal(content: string, localId: string, url: string): string {
  return content.split(`(${LOCAL_SCHEME}${localId})`).join(`(${url})`);
}

/**
 * 대기 사진을 차례로 다시 올린다. 또 보관 대상이면 멈춘다.
 *
 * @returns 남은 대기 사진 수와 이번에 포기한 수
 */
export async function retryPending(
  memberId: number,
  postId: number,
  deps: RetryDeps,
): Promise<{ remaining: number; failed: number }> {
  const upload = deps.upload ?? uploadImage;
  let failed = 0;
  for (const image of await loadPendingImages(memberId, postId)) {
    const result = await upload(pendingBlob(image));
    if (result.kind === 'pending') {
      const remaining = (await loadPendingImages(memberId, postId)).length;
      return { remaining, failed };
    }
    if (result.kind === 'uploaded') {
      const content = deps.getContent();
      const next = replaceLocal(content, image.localId, result.image.url);
      if (next !== content) {
        deps.setContent(next);
      }
    } else {
      failed += 1;
    }
    await updatePendingImages(memberId, postId, (images) =>
      images.filter((i) => i.localId !== image.localId),
    );
  }
  if (failed > 0) {
    deps.onFailed?.(PENDING_FAILED);
  }
  return { remaining: (await loadPendingImages(memberId, postId)).length, failed };
}

export interface RetrierDeps extends RetryDeps {
  isOnline?: () => boolean;
  /** 남은 대기 사진 수가 바뀔 때 */
  onCount?: (count: number) => void;
}

/** 에디터 하나의 다시 올리기 담당: `start`(에디터 열기)·`online` 이벤트·지수 대기. */
export function createPendingRetrier(memberId: number, postId: number, deps: RetrierDeps) {
  const isOnline = deps.isOnline ?? (() => typeof navigator === 'undefined' || navigator.onLine);
  let timer: ReturnType<typeof setTimeout> | null = null;
  let running = false;
  let stopped = true;
  let attempt = 0;

  const delayFor = (n: number) => Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** Math.max(0, n - 1));

  const clear = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const run = async () => {
    if (running || stopped) return;
    running = true;
    clear();
    try {
      const { remaining } = await retryPending(memberId, postId, deps);
      if (stopped) return;
      deps.onCount?.(remaining);
      if (remaining > 0) {
        attempt += 1;
        timer = setTimeout(() => void run(), delayFor(attempt));
      } else {
        attempt = 0;
      }
    } catch {
      // 저장소(IndexedDB)를 읽지 못하면 다음 기회(online·다음 열기)에 다시 한다
    } finally {
      running = false;
    }
  };

  const onOnline = () => {
    attempt = 0;
    void run();
  };

  return {
    start() {
      if (!stopped) return;
      stopped = false;
      window.addEventListener('online', onOnline);
      void loadPendingImages(memberId, postId)
        .then((images) => {
          deps.onCount?.(images.length);
          if (images.length > 0 && isOnline()) {
            void run();
          }
        })
        .catch(() => undefined);
    },
    stop() {
      stopped = true;
      window.removeEventListener('online', onOnline);
      clear();
    },
    /** 사진을 새로 보관했을 때 — 수를 알리고, 온라인이면 대기 시간 뒤 다시 시도한다. */
    held(count: number) {
      deps.onCount?.(count);
      if (!timer && !running && isOnline() && !stopped) {
        attempt += 1;
        timer = setTimeout(() => void run(), delayFor(attempt));
      }
    },
    retryNow: () => run(),
    delayFor,
  };
}
