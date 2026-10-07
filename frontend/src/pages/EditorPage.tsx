import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, type FieldError } from '../api/client';
import {
  autosave,
  autosaveKeepalive,
  createPost,
  discardWorkingCopy,
  getWorkingCopy,
  saveWorkingCopy,
  type PublishResponse,
  type SaveResponse,
  type ServerCopy,
} from '../api/posts';
import ConfirmDialog from '../components/editor/ConfirmDialog';
import ConflictBanner from '../components/editor/ConflictBanner';
import DiffDialog from '../components/editor/DiffDialog';
import PreviewPane from '../components/editor/PreviewPane';
import PublishDialog, { type PublishContent } from '../components/editor/PublishDialog';
import SaveStatus from '../components/editor/SaveStatus';
import { useSession } from '../features/auth/useSession';
import { AutosaveQueue, type AutosaveStatus } from '../features/editor/autosaveQueue';
import { ConflictController, serverCopyOf, type ConflictState } from '../features/editor/conflict';
import { EDITOR_CONFIG } from '../features/editor/editorConfig';
import { registerLifecycle } from '../features/editor/lifecycle';
import { removeDraft, saveDraft } from '../features/editor/localDraftStore';
import { openEditor, type OpenedEditor } from '../features/editor/openEditor';
import { setActiveEditor } from '../features/editor/pendingWork';
import NotFoundPage from './NotFoundPage';
import './editor.css';

/** [저장된 내용 불러오기] 뒤 안내 (US5 #4). */
export const BACKUP_NOTICE = '편집 중이던 내용은 이 기기에 7일 동안 보관해 두었어요';
/** [변경 취소] 확인 문구 (006 `confirmDialogs.ts`가 생기면 그쪽 문구로 바꾼다). */
export const DISCARD_CONFIRM = '고치던 내용을 버리고 발행한 내용으로 돌아갈까요?';

/** `/write/new` — 임시글을 만들고 `/write/{postId}`로 바꾼다 (FR-001, B-1). */
export function NewPostPage() {
  const navigate = useNavigate();
  const { loading, me } = useSession();
  const started = useRef(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (loading) {
      return;
    }
    if (!me) {
      navigate('/login', { replace: true });
      return;
    }
    // StrictMode에서 효과가 두 번 돌아도 임시글은 하나만 만든다.
    if (started.current) {
      return;
    }
    started.current = true;
    createPost()
      .then((created) => navigate(`/write/${created.postId}`, { replace: true }))
      .catch((e: unknown) => {
        setError(e instanceof ApiError ? e.message : '새 글을 만들지 못했어요');
      });
  }, [loading, me, navigate]);

  return (
    <main className="editor-page">
      {error ? <p role="alert">{error}</p> : <p>새 글을 준비하고 있어요…</p>}
    </main>
  );
}

function fieldError(errors: FieldError[], field: string): FieldError | undefined {
  return errors.find((e) => e.field === field);
}

/** `/write/{postId}` — 에디터 (FR-002·007~014·020~026·034·035, US1~US5). */
export default function EditorPage() {
  const params = useParams();
  const postId = Number(params.postId);
  const validId = Number.isSafeInteger(postId) && postId > 0;
  const navigate = useNavigate();
  const { loading, me } = useSession();
  const memberId = me?.memberId ?? null;

  const [opened, setOpened] = useState<OpenedEditor | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [contentMd, setContentMd] = useState('');
  const [status, setStatus] = useState<AutosaveStatus | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([]);
  const [publishing, setPublishing] = useState(false);
  const [saving, setSaving] = useState(false);
  /** 발행 글을 고치는 중인가 (작업본 또는 서버 쪽 보관분이 있음, FR-034). */
  const [editing, setEditing] = useState(false);
  const [confirmingDiscard, setConfirmingDiscard] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  /** 바뀌면 서버에서 다시 연다 ([변경 취소] 뒤). */
  const [reloadKey, setReloadKey] = useState(0);
  /** 저장 충돌 (US5): 배너·비교 창. */
  const [conflict, setConflict] = useState<ConflictState | null>(null);
  const [comparing, setComparing] = useState<{ title: string; contentMd: string } | null>(null);
  const queueRef = useRef<AutosaveQueue | null>(null);
  const conflictRef = useRef<ConflictController | null>(null);
  const latest = useRef({ title: '', contentMd: '' });

  useEffect(() => {
    if (loading || !validId) {
      return;
    }
    if (memberId === null) {
      navigate('/login', { replace: true });
      return;
    }
    let cancelled = false;
    const cleanups: (() => void)[] = [];
    openEditor(memberId, postId)
      .then((result) => {
        if (cancelled) {
          return;
        }
        const published = result.server.status === 'PUBLISHED';
        setOpened(result);
        setEditing(published && (result.server.editing || result.dirty));
        setTitle(result.initial.title);
        setContentMd(result.initial.contentMd);
        latest.current = { title: result.initial.title, contentMd: result.initial.contentMd };
        setNotice(result.notice);
        setConflict(null);
        setComparing(null);
        setStatus(
          result.dirty ? { kind: 'local' } : { kind: 'saved', savedAt: result.server.savedAt },
        );
        const queue = new AutosaveQueue({
          initial: result.initial,
          server: { title: result.server.title, contentMd: result.server.contentMd },
          send: (body) => autosave(postId, body),
          saveLocal: (draft) => saveDraft(memberId, postId, draft),
          onStatus: setStatus,
          onConflict: (error) => {
            const server = serverCopyOf(error);
            if (server) {
              controller.report(server);
              return;
            }
            // 409에 서버 내용이 없으면(드묾) 다시 읽어 비교한다
            void getWorkingCopy(postId)
              .then((copy) => controller.report(copy))
              .catch(() => undefined);
          },
          onSaved: () => {
            if (published) {
              setEditing(true);
            }
          },
          isOnline: () => (typeof navigator === 'undefined' ? true : navigator.onLine !== false),
        });
        const controller = new ConflictController({
          queue,
          memberId,
          postId,
          onChange: setConflict,
          onOpenCompare: () => setComparing({ ...latest.current }),
        });
        queueRef.current = queue;
        conflictRef.current = controller;
        if (result.conflict) {
          // 이 기기의 안 보낸 변경이 서버와 갈라졌다 — 바로 비교 창 (FR-022)
          controller.report(result.conflict, { open: true });
        }
        const goOnline = () => queue.setOnline(true);
        const goOffline = () => queue.setOnline(false);
        window.addEventListener('online', goOnline);
        window.addEventListener('offline', goOffline);
        cleanups.push(
          () => window.removeEventListener('online', goOnline),
          () => window.removeEventListener('offline', goOffline),
          registerLifecycle({
            flushNow: () => void queue.flush(),
            hasUnsent: () => queue.hasUnsent(),
            sendKeepalive: () => autosaveKeepalive(postId, queue.pendingBody()),
          }),
          setActiveEditor({ memberId, postId, flush: () => queue.flush() }),
          () => queue.dispose(),
        );
      })
      .catch((e: unknown) => {
        if (cancelled) {
          return;
        }
        if (e instanceof ApiError && e.status === 401) {
          navigate('/login', { replace: true });
          return;
        }
        // 404는 공통 404 화면으로 바뀐다(client onNotFound). 그 밖의 오류만 여기서 알린다.
        setLoadError(e instanceof ApiError ? e.message : '글을 불러오지 못했어요');
      });
    return () => {
      cancelled = true;
      cleanups.forEach((cleanup) => cleanup());
      queueRef.current = null;
      conflictRef.current = null;
    };
  }, [loading, memberId, postId, validId, navigate, reloadKey]);

  const onTitle = (value: string) => {
    setTitle(value);
    latest.current = { ...latest.current, title: value };
    setFieldErrors((errors) => errors.filter((e) => e.field !== 'title'));
    queueRef.current?.update(value, latest.current.contentMd);
  };

  const onContent = (value: string) => {
    setContentMd(value);
    latest.current = { ...latest.current, contentMd: value };
    setFieldErrors((errors) => errors.filter((e) => e.field !== 'contentMd'));
    queueRef.current?.update(latest.current.title, value);
  };

  /** [저장] — 즉시 DB에 반영한다 (FR-009, D-3). */
  const onSave = async () => {
    const queue = queueRef.current;
    if (!queue || saving || conflictRef.current?.intercept()) {
      return;
    }
    setSaving(true);
    setMessage(null);
    await queue.pause();
    const snapshot = { ...latest.current };
    try {
      const response = await saveWorkingCopy(postId, {
        ...snapshot,
        baseVersion: queue.baseVersion,
      });
      queue.markSaved(response, snapshot);
      if (opened?.server.status === 'PUBLISHED') {
        setEditing(true);
      }
    } catch (e) {
      const server = serverCopyOf(e);
      if (server) {
        // 직접 누른 [저장]이 충돌했다 — 바로 비교 창 (US5 #2)
        conflictRef.current?.report(server, { open: true });
      } else {
        setMessage(
          e instanceof ApiError
            ? (e.errors[0]?.message ?? e.message)
            : '저장하지 못했어요. 이 기기에는 저장돼 있어요',
        );
      }
    } finally {
      queue.resume();
      setSaving(false);
    }
  };

  const getPublishContent = useCallback(async (): Promise<PublishContent> => {
    const queue = queueRef.current;
    if (queue) {
      await queue.pause();
    }
    return {
      ...latest.current,
      baseVersion: queue?.baseVersion ?? opened?.initial.baseVersion ?? 0,
    };
  }, [opened]);

  const onPublished = async (response: PublishResponse) => {
    queueRef.current?.dispose();
    if (memberId !== null) {
      await removeDraft(memberId, postId).catch(() => undefined);
    }
    window.location.assign(response.url);
  };

  /** [변경 취소] 확인 뒤 — 작업본을 버리고 이 기기 초안을 지운 다음 발행본으로 다시 연다 (FR-035, US4 #3). */
  const onDiscard = async () => {
    const queue = queueRef.current;
    setDiscarding(true);
    setMessage(null);
    if (queue) {
      await queue.pause();
    }
    try {
      await discardWorkingCopy(postId);
      queue?.dispose();
      if (memberId !== null) {
        await removeDraft(memberId, postId).catch(() => undefined);
      }
      setConfirmingDiscard(false);
      setNotice(null);
      setFieldErrors([]);
      setReloadKey((key) => key + 1);
    } catch (e) {
      queue?.resume();
      setConfirmingDiscard(false);
      setMessage(e instanceof ApiError ? e.message : '변경을 취소하지 못했어요');
    } finally {
      setDiscarding(false);
    }
  };

  /** 발행이 409 `VERSION_CONFLICT` — 발행 창을 닫고 비교 창을 연다 (US5·US6). */
  const onPublishConflict = (server: ServerCopy) => {
    setPublishing(false);
    conflictRef.current?.report(server, { open: true });
  };

  const onPublishClick = () => {
    if (conflictRef.current?.intercept()) {
      return;
    }
    setPublishing(true);
  };

  const onCompare = () => {
    conflictRef.current?.intercept();
  };

  // ---- 비교 창의 선택 (US5 #3~#5) ----
  const onKeptMine = (response: SaveResponse, mine: { title: string; contentMd: string }) => {
    conflictRef.current?.keptMine(response, mine);
    setComparing(null);
    setNotice(null);
    if (opened?.server.status === 'PUBLISHED') {
      setEditing(true);
    }
  };

  const onLoadServer = async () => {
    const controller = conflictRef.current;
    if (!controller) {
      return;
    }
    const content = await controller.loadServer({ ...latest.current });
    setTitle(content.title);
    setContentMd(content.contentMd);
    latest.current = { title: content.title, contentMd: content.contentMd };
    setFieldErrors([]);
    setComparing(null);
    setNotice(BACKUP_NOTICE);
  };

  const onCreatedCopy = async (newPostId: number) => {
    queueRef.current?.dispose();
    if (memberId !== null) {
      // 이 기기 내용은 새 임시글로 옮겨 갔다. 원래 글을 다시 열 때 같은 충돌이 뜨지 않게 지운다.
      await removeDraft(memberId, postId).catch(() => undefined);
    }
    setComparing(null);
    navigate(`/write/${newPostId}`);
  };

  const onCloseCompare = () => {
    conflictRef.current?.dismiss();
    setComparing(null);
  };

  if (!validId) {
    return <NotFoundPage />;
  }
  if (loadError) {
    return (
      <main className="editor-page">
        <p role="alert">{loadError}</p>
      </main>
    );
  }
  if (!opened) {
    return (
      <main className="editor-page">
        <p>글을 불러오고 있어요…</p>
      </main>
    );
  }

  const titleError = fieldError(fieldErrors, 'title');
  const contentError = fieldError(fieldErrors, 'contentMd');

  return (
    <main className="editor-page">
      <header className="editor-toolbar">
        <div className="editor-state">
          {opened.server.status === 'PUBLISHED' && editing ? (
            <span className="editing-badge">수정 중</span>
          ) : null}
          <SaveStatus status={status} onCompare={conflict ? onCompare : undefined} />
        </div>
        <div className="editor-actions">
          {opened.server.status === 'PUBLISHED' && editing ? (
            <button type="button" onClick={() => setConfirmingDiscard(true)} disabled={discarding}>
              변경 취소
            </button>
          ) : null}
          <button type="button" onClick={onSave} disabled={saving}>
            저장
          </button>
          <button type="button" onClick={onPublishClick}>
            발행하기
          </button>
        </div>
      </header>
      {conflict ? <ConflictBanner savedAt={conflict.server.savedAt} onCompare={onCompare} /> : null}
      {notice ? <p className="editor-notice">{notice}</p> : null}
      {message ? (
        <p role="alert" className="form-error">
          {message}
        </p>
      ) : null}

      <input
        className="editor-title"
        aria-label="제목"
        placeholder="제목"
        value={title}
        maxLength={EDITOR_CONFIG.titleMax}
        aria-invalid={titleError ? 'true' : undefined}
        aria-describedby={titleError ? 'title-error' : undefined}
        onChange={(e) => onTitle(e.target.value)}
      />
      {titleError ? (
        <p id="title-error" className="field-error">
          {titleError.message}
        </p>
      ) : null}

      <div className="editor-body">
        <div className="editor-input">
          <textarea
            aria-label="본문"
            placeholder="Markdown으로 쓰세요"
            value={contentMd}
            aria-invalid={contentError ? 'true' : undefined}
            aria-describedby={contentError ? 'content-error' : undefined}
            onChange={(e) => onContent(e.target.value)}
          />
          {contentError ? (
            <p id="content-error" className="field-error">
              {contentError.message}
            </p>
          ) : null}
        </div>
        <PreviewPane contentMd={contentMd} />
      </div>

      {comparing && conflict ? (
        <DiffDialog
          postId={postId}
          server={conflict.server}
          mine={comparing}
          onKeptMine={onKeptMine}
          onLoadServer={onLoadServer}
          onCreated={(id) => void onCreatedCopy(id)}
          onServerChanged={(server) => conflictRef.current?.updateServer(server)}
          onClose={onCloseCompare}
        />
      ) : null}

      {confirmingDiscard ? (
        <ConfirmDialog
          message={DISCARD_CONFIRM}
          confirmLabel="버리기"
          cancelLabel="계속 고치기"
          busy={discarding}
          onConfirm={() => void onDiscard()}
          onCancel={() => setConfirmingDiscard(false)}
        />
      ) : null}

      {publishing ? (
        <PublishDialog
          postId={postId}
          getContent={getPublishContent}
          initialTags={opened.server.tags}
          initialVisibility={opened.server.visibility}
          onPublished={onPublished}
          onFieldErrors={setFieldErrors}
          onConflict={onPublishConflict}
          onSettled={() => queueRef.current?.resume()}
          onClose={() => setPublishing(false)}
        />
      ) : null}
    </main>
  );
}
