/**
 * 발행 요청 규칙 (002 T112, FR-037, US6 #1, research A-8).
 *
 * - [발행]을 한 번 누르면 `publishOnce` 한 번 = 새 `Idempotency-Key`(UUID) 하나.
 * - 409 `IN_PROGRESS`(같은 요청을 서버가 처리 중)만 `inProgressRetryMs`(1초) 뒤 **같은 키**로 다시 보낸다.
 * - 409 `VERSION_CONFLICT` → 비교 창(US5 `conflict.ts`)으로 넘길 서버 내용, 400 → 칸 오류, 그 밖 → 문구.
 * - 성공하면 `finishPublish`가 이 기기 초안을 지운 뒤 글 주소로 옮긴다.
 */
import { ApiError, type FieldError } from '../../api/client';
import {
  publishPost,
  type PublishRequest,
  type PublishResponse,
  type ServerCopy,
} from '../../api/posts';
import { serverCopyOf } from './conflict';
import { EDITOR_CONFIG } from './editorConfig';

export const PUBLISH_FAILED = '발행하지 못했어요. 연결을 확인하고 다시 시도해 주세요';

/** `IN_PROGRESS`가 이어질 때 다시 보내는 최대 횟수. */
export const MAX_IN_PROGRESS_RETRIES = 10;

export type PublishSend = (
  postId: number,
  body: PublishRequest,
  idempotencyKey: string,
) => Promise<PublishResponse>;

export type PublishOutcome =
  | { kind: 'published'; response: PublishResponse }
  | { kind: 'invalid'; errors: FieldError[]; message: string }
  | { kind: 'conflict'; server: ServerCopy; message: string }
  | { kind: 'failed'; message: string };

export interface PublishOptions {
  send?: PublishSend;
  newKey?: () => string;
  retryMs?: number;
  maxInProgressRetries?: number;
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** [발행] 한 번. 예외를 던지지 않고 결과 종류를 돌려준다. */
export async function publishOnce(
  postId: number,
  body: PublishRequest,
  options: PublishOptions = {},
): Promise<PublishOutcome> {
  const send = options.send ?? publishPost;
  const key = (options.newKey ?? (() => crypto.randomUUID()))();
  const retryMs = options.retryMs ?? EDITOR_CONFIG.inProgressRetryMs;
  const maxRetries = options.maxInProgressRetries ?? MAX_IN_PROGRESS_RETRIES;
  let attempt = 0;
  for (;;) {
    try {
      return { kind: 'published', response: await send(postId, body, key) };
    } catch (error) {
      if (error instanceof ApiError && error.code === 'IN_PROGRESS' && attempt < maxRetries) {
        attempt += 1;
        await sleep(retryMs);
        continue;
      }
      return outcomeOf(error);
    }
  }
}

function outcomeOf(error: unknown): PublishOutcome {
  if (!(error instanceof ApiError)) {
    return { kind: 'failed', message: PUBLISH_FAILED };
  }
  if (error.errors.length > 0) {
    return { kind: 'invalid', errors: error.errors, message: error.message };
  }
  if (error.code === 'VERSION_CONFLICT') {
    const server = serverCopyOf(error);
    if (server) {
      return { kind: 'conflict', server, message: error.message };
    }
  }
  return { kind: 'failed', message: error.message };
}

/** 발행 성공 뒤: 이 기기 초안을 지우고(실패해도 계속) 글 주소로 옮긴다. */
export async function finishPublish(
  response: PublishResponse,
  deps: { removeLocalDraft: () => Promise<void>; navigate: (url: string) => void },
): Promise<void> {
  await deps.removeLocalDraft().catch(() => undefined);
  deps.navigate(response.url);
}
