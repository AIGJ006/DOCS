import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { ApiError, type FieldError } from '../../api/client';
import {
  publishPost,
  type PublishResponse,
  type ServerCopy,
  type Visibility,
} from '../../api/posts';
import { EDITOR_CONFIG } from '../../features/editor/editorConfig';

/**
 * 발행 설정 창 (002 T055, FR-026·038, US1 #2·#3).
 *
 * - 태그: 임시 칩 입력(최대 `maxTags`, 008 화면이 교체). Enter·쉼표로 넣는다.
 * - 공개 범위: 004 `VisibilitySelect`가 아직 없어 단순 선택 상자를 쓴다(초기값 = working copy `visibility`).
 * - [발행]을 누를 때마다 `crypto.randomUUID()`로 새 `Idempotency-Key`를 만든다. 409 `IN_PROGRESS`만 같은 키로 1초 뒤 다시.
 * - 400 `errors[]`는 칸마다 보인다: 태그는 그 칩 옆, 공개 범위는 선택 상자 옆, 제목·본문은 `onFieldErrors`로 에디터에 넘긴다.
 */
export interface PublishContent {
  title: string;
  contentMd: string;
  baseVersion: number;
}

interface Props {
  postId: number;
  /** 발행 직전의 에디터 내용 (자동 저장을 멈추고 기준 버전을 확정한 뒤 돌려준다). */
  getContent: () => Promise<PublishContent>;
  initialTags: string[];
  initialVisibility: Visibility;
  onPublished: (response: PublishResponse) => void;
  onFieldErrors: (errors: FieldError[]) => void;
  onConflict?: (server: ServerCopy) => void;
  onClose: () => void;
  /** 발행 시도가 끝났다 (성공·실패 모두) — 에디터가 자동 저장을 다시 켠다. */
  onSettled?: () => void;
}

const VISIBILITY_LABELS: Record<Visibility, string> = {
  PUBLIC: '전체 공개',
  PRIVATE: '나만 보기',
};

const MAX_IN_PROGRESS_RETRIES = 10;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function newKey(): string {
  return crypto.randomUUID();
}

export default function PublishDialog({
  postId,
  getContent,
  initialTags,
  initialVisibility,
  onPublished,
  onFieldErrors,
  onConflict,
  onClose,
  onSettled,
}: Props) {
  const [tags, setTags] = useState<string[]>(initialTags);
  const [tagInput, setTagInput] = useState('');
  const [visibility, setVisibility] = useState<Visibility>(initialVisibility);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const full = tags.length >= EDITOR_CONFIG.maxTags;

  function addTag(raw: string) {
    const tag = raw.trim();
    if (tag === '' || full) {
      return;
    }
    if (!tags.some((t) => t.toLowerCase() === tag.toLowerCase())) {
      setTags([...tags, tag]);
    }
    setTagInput('');
  }

  function removeTag(index: number) {
    setTags(tags.filter((_, i) => i !== index));
    setErrors(errors.filter((e) => !e.field.startsWith('tags')));
  }

  function onTagKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      addTag(tagInput);
    }
  }

  function errorFor(field: string): FieldError | undefined {
    return errors.find((e) => e.field === field);
  }

  async function publish(event: FormEvent) {
    event.preventDefault();
    if (busy) {
      return;
    }
    setBusy(true);
    setMessage(null);
    setErrors([]);
    const key = newKey();
    try {
      const content = await getContent();
      const body = { ...content, tags, visibility };
      let attempt = 0;
      for (;;) {
        try {
          const response = await publishPost(postId, body, key);
          onFieldErrors([]);
          onPublished(response);
          return;
        } catch (error) {
          if (
            error instanceof ApiError &&
            error.code === 'IN_PROGRESS' &&
            attempt < MAX_IN_PROGRESS_RETRIES
          ) {
            attempt += 1;
            await sleep(EDITOR_CONFIG.inProgressRetryMs);
            continue;
          }
          throw error;
        }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.errors.length > 0) {
          setErrors(error.errors);
          onFieldErrors(error.errors);
          setMessage(error.message);
        } else if (error.code === 'VERSION_CONFLICT') {
          setMessage(error.message);
          const server = (error.details as { server?: ServerCopy } | null)?.server;
          if (server) {
            onConflict?.(server);
          }
        } else {
          setMessage(error.message);
        }
      } else {
        setMessage('발행하지 못했어요. 연결을 확인하고 다시 시도해 주세요');
      }
    } finally {
      setBusy(false);
      onSettled?.();
    }
  }

  const visibilityError = errorFor('visibility');
  const tagsError = errorFor('tags');

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="publish-dialog-title"
      className="publish-dialog"
    >
      <form onSubmit={publish}>
        <h2 id="publish-dialog-title">발행 설정</h2>

        <fieldset>
          <legend>태그</legend>
          <ul className="tag-chips">
            {tags.map((tag, index) => {
              const error = errorFor(`tags[${index}]`);
              return (
                <li key={tag} aria-invalid={error ? 'true' : undefined}>
                  <span>{tag}</span>
                  <button
                    type="button"
                    aria-label={`${tag} 태그 빼기`}
                    onClick={() => removeTag(index)}
                  >
                    ×
                  </button>
                  {error ? <span className="field-error">{error.message}</span> : null}
                </li>
              );
            })}
          </ul>
          <input
            aria-label="태그 입력"
            value={tagInput}
            disabled={full}
            placeholder={full ? `태그는 ${EDITOR_CONFIG.maxTags}개까지예요` : 'Enter로 추가'}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={onTagKeyDown}
          />
          {tagsError ? <p className="field-error">{tagsError.message}</p> : null}
        </fieldset>

        <label>
          공개 범위
          <select
            aria-label="공개 범위"
            value={visibility}
            aria-invalid={visibilityError ? 'true' : undefined}
            onChange={(e) => setVisibility(e.target.value as Visibility)}
          >
            {(Object.keys(VISIBILITY_LABELS) as Visibility[]).map((value) => (
              <option key={value} value={value}>
                {VISIBILITY_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        {visibilityError ? <p className="field-error">{visibilityError.message}</p> : null}

        {message ? (
          <p role="alert" className="form-error">
            {message}
          </p>
        ) : null}

        <div className="dialog-actions">
          <button type="button" onClick={onClose} disabled={busy}>
            닫기
          </button>
          <button type="submit" disabled={busy}>
            발행
          </button>
        </div>
      </form>
    </div>
  );
}
