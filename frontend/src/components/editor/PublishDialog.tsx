import { useRef, useState, type FormEvent } from 'react';
import type { FieldError } from '../../api/client';
import type { PublishResponse, ServerCopy, Visibility } from '../../api/posts';
import { EDITOR_CONFIG } from '../../features/editor/editorConfig';
import { PUBLISH_FAILED, publishOnce } from '../../features/editor/publish';
import VisibilitySelect from '../../features/visibility/VisibilitySelect';
import AltTextPanel from './AltTextPanel';
import TagInput from './TagInput';

/**
 * 발행 설정 창 (002 T055, FR-026·038, US1 #2·#3).
 *
 * - 태그: 008 `TagInput`(정규화한 칩·순서 바꾸기·"2 / 10"·오류 칩). 보내는 `tags`는 칩의 정규화된 이름 배열(순서 그대로)이다.
 *   태그는 발행할 때만 확정된다(자동 저장 대상 아님) — 발행하지 않고 닫으면 `onTagsChange`로 받은 칩을 에디터 화면이 들고 있다가
 *   다시 열 때 넘긴다(그 브라우저 화면에만, 008 FR-014).
 * - 공개 범위: 004 `VisibilitySelect`의 값만 고르는 모드(서버는 발행 때 함께 받는다, 초기값 = working copy `visibility`).
 * - [발행]을 누를 때마다 `crypto.randomUUID()`로 새 `Idempotency-Key`를 만든다. 409 `IN_PROGRESS`만 같은 키로 1초 뒤 다시
 *   (`features/editor/publish.ts`, US6 T112). 응답 전에는 버튼을 끄고 "발행 중…"으로 보인다.
 * - 409 `VERSION_CONFLICT`면 `onConflict`로 서버 내용을 넘겨 비교 창(US5)을 연다.
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
  /** 칩이 바뀔 때마다 (발행하지 않고 닫아도 다시 열 때 그대로 보이게, 008 FR-014) */
  onTagsChange?: (tags: string[]) => void;
  initialVisibility: Visibility;
  onPublished: (response: PublishResponse) => void;
  onFieldErrors: (errors: FieldError[]) => void;
  onConflict?: (server: ServerCopy) => void;
  onClose: () => void;
  /** 발행 시도가 끝났다 (성공·실패 모두) — 에디터가 자동 저장을 다시 켠다. */
  onSettled?: () => void;
  /** 지금 본문과 본문 바꾸기 — 주면 대체글 권유(003 US5)를 창 위쪽에 보인다. */
  contentMd?: string;
  onContentChange?: (contentMd: string) => void;
}

export default function PublishDialog({
  postId,
  getContent,
  initialTags,
  onTagsChange,
  initialVisibility,
  onPublished,
  onFieldErrors,
  onConflict,
  onClose,
  onSettled,
  contentMd,
  onContentChange,
}: Props) {
  const [tags, setTags] = useState<string[]>(initialTags);
  const [visibility, setVisibility] = useState<Visibility>(initialVisibility);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** 다시 그리기 전에 들어온 두 번째 제출도 막는다. */
  const inFlight = useRef(false);

  function changeTags(next: string[]) {
    setTags(next);
    // 칩이 바뀌면 칸 번호가 어긋나므로 태그 칸 오류를 지운다
    setErrors((current) => current.filter((e) => !e.field.startsWith('tags')));
    onTagsChange?.(next);
  }

  function errorFor(field: string): FieldError | undefined {
    return errors.find((e) => e.field === field);
  }

  async function publish(event: FormEvent) {
    event.preventDefault();
    if (inFlight.current) {
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setMessage(null);
    setErrors([]);
    try {
      const content = await getContent();
      const outcome = await publishOnce(postId, { ...content, tags, visibility });
      switch (outcome.kind) {
        case 'published':
          onFieldErrors([]);
          onPublished(outcome.response);
          return;
        case 'invalid':
          setErrors(outcome.errors);
          onFieldErrors(outcome.errors);
          setMessage(outcome.message);
          return;
        case 'conflict':
          setMessage(outcome.message);
          onConflict?.(outcome.server);
          return;
        case 'failed':
          setMessage(outcome.message);
          return;
      }
    } catch {
      setMessage(PUBLISH_FAILED);
    } finally {
      inFlight.current = false;
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

        {contentMd !== undefined && onContentChange ? (
          <AltTextPanel contentMd={contentMd} onChange={onContentChange} />
        ) : null}

        <fieldset>
          <legend>태그</legend>
          <TagInput
            value={tags}
            onChange={changeTags}
            max={EDITOR_CONFIG.maxTags}
            errors={errors}
            disabled={busy}
          />
          {tagsError ? <p className="field-error">{tagsError.message}</p> : null}
        </fieldset>

        <VisibilitySelect
          value={visibility}
          onChange={setVisibility}
          disabled={busy}
          error={visibilityError?.message ?? null}
        />

        {message ? (
          <p role="alert" className="form-error">
            {message}
          </p>
        ) : null}

        <div className="dialog-actions">
          <button type="button" onClick={onClose} disabled={busy}>
            닫기
          </button>
          <button type="submit" disabled={busy} aria-busy={busy || undefined}>
            {busy ? '발행 중…' : '발행'}
          </button>
        </div>
      </form>
    </div>
  );
}
