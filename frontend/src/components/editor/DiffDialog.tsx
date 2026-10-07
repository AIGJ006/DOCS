import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { createPost, saveWorkingCopy, type SaveResponse, type ServerCopy } from '../../api/posts';
import { serverCopyOf } from '../../features/editor/conflict';
import {
  buildDiff,
  DEFAULT_FOLD,
  foldUnchanged,
  nextChange,
  previousChange,
  sideBySideRows,
  type DiffLine,
  type FoldItem,
  type Segment,
} from '../../features/editor/diffModel';
import { formatSavedAt } from '../../features/editor/saveStatusText';
import ConfirmDialog from './ConfirmDialog';

/**
 * 충돌 비교 창 (002 T104, FR-023·024, US5 #2~#5).
 *
 * - 왼쪽(위) = 서버에 저장된 내용, 오른쪽(아래) = 지금 편집 중인 내용. 지운 부분은 빨간 배경 + `−`, 더한 부분은 초록 배경 + `+`(색과
 *   기호를 함께 써서 색을 구별하지 못해도 읽힌다). 바뀐 줄 안에서는 바뀐 낱말만 한 번 더 강조한다.
 * - 너비 768px 이상은 좌우 나란히, 그보다 좁으면 위아래로 합친 한 줄 목록(가로 스크롤 없이 줄바꿈).
 * - [이전 차이]·[다음 차이], 바뀌지 않은 긴 구간 접기·펼치기.
 * - 결과를 이름에 담은 버튼 세 개 + 닫기. [편집 중인 내용으로 저장]은 확인을 한 번 더 받는다.
 */
export const SPLIT_MIN_WIDTH = 768;

interface Props {
  postId: number;
  server: ServerCopy;
  mine: { title: string; contentMd: string };
  /** 편집 중인 내용으로 저장했다 (서버 버전 = `response.version`). */
  onKeptMine: (response: SaveResponse, mine: { title: string; contentMd: string }) => void;
  /** [저장된 내용 불러오기] — 백업·에디터 교체는 부모가 한다. */
  onLoadServer: () => Promise<void> | void;
  /** 새 임시글을 만들었다 — 부모가 그 글로 이동한다. */
  onCreated: (postId: number) => void;
  /** 저장하는 사이 서버가 또 바뀌었다 (409). */
  onServerChanged: (server: ServerCopy) => void;
  onClose: () => void;
}

function useWideLayout(): boolean {
  const read = () => (typeof window === 'undefined' ? true : window.innerWidth >= SPLIT_MIN_WIDTH);
  const [wide, setWide] = useState(read);
  useEffect(() => {
    const onResize = () => setWide(read());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return wide;
}

function Segments({
  segments,
  kind,
}: {
  segments: Segment[];
  kind: 'removed' | 'added' | 'equal';
}) {
  return (
    <>
      {segments.map((segment, index) =>
        segment.changed && kind === 'removed' ? (
          <del key={index} className="diff-word">
            {segment.text}
          </del>
        ) : segment.changed && kind === 'added' ? (
          <ins key={index} className="diff-word">
            {segment.text}
          </ins>
        ) : (
          <span key={index}>{segment.text}</span>
        ),
      )}
    </>
  );
}

const SIGN: Record<DiffLine['kind'], string> = { equal: ' ', removed: '−', added: '+' };
const LABEL: Record<DiffLine['kind'], string> = { equal: '같음', removed: '지움', added: '더함' };

function LineCell({ line, current }: { line: DiffLine | null; current: number | null }) {
  if (!line) {
    return <div className="diff-cell diff-empty" aria-hidden="true" />;
  }
  const isCurrent = line.changeIndex !== null && line.changeIndex === current;
  return (
    <div
      className={`diff-cell diff-${line.kind}${isCurrent ? ' diff-current' : ''}`}
      data-testid={line.kind === 'equal' ? undefined : `diff-${line.kind}`}
      data-change={line.changeIndex ?? undefined}
    >
      <span className="diff-sign" aria-label={LABEL[line.kind]}>
        {SIGN[line.kind]}
      </span>
      <span className="diff-text">
        <Segments segments={line.segments} kind={line.kind} />
      </span>
    </div>
  );
}

export default function DiffDialog({
  postId,
  server,
  mine,
  onKeptMine,
  onLoadServer,
  onCreated,
  onServerChanged,
  onClose,
}: Props) {
  const wide = useWideLayout();
  const diff = useMemo(() => buildDiff(server, mine), [server, mine]);
  const items = useMemo(() => foldUnchanged(diff.lines, DEFAULT_FOLD), [diff]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [current, setCurrent] = useState<number | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const savedTime = formatSavedAt(server.savedAt);

  useEffect(() => {
    if (current === null) {
      return;
    }
    const target = bodyRef.current?.querySelector(`[data-change="${current}"]`);
    if (target && 'scrollIntoView' in target && typeof target.scrollIntoView === 'function') {
      target.scrollIntoView({ block: 'center' });
    }
  }, [current]);

  // 지금 보는 차이가 접힌 구간에 있지 않도록 (바뀐 줄은 접지 않으므로 해당 없음) — 펼친 구간만 기억한다.
  const visible: FoldItem[] = items.flatMap((item) =>
    item.type === 'fold' && expanded.has(item.id)
      ? item.lines.map((line) => ({ type: 'line' as const, line }))
      : [item],
  );

  async function keepMine() {
    setBusy(true);
    setMessage(null);
    try {
      const response = await saveWorkingCopy(postId, { ...mine, baseVersion: server.version });
      setConfirming(false);
      onKeptMine(response, mine);
    } catch (error) {
      setConfirming(false);
      const newer = serverCopyOf(error);
      if (newer) {
        setMessage('그 사이 또 저장된 내용이 있어 비교를 새로 보여 드려요');
        onServerChanged(newer);
      } else {
        setMessage(error instanceof ApiError ? error.message : '저장하지 못했어요');
      }
    } finally {
      setBusy(false);
    }
  }

  async function saveAsNew() {
    setBusy(true);
    setMessage(null);
    try {
      const created = await createPost({ title: mine.title, contentMd: mine.contentMd });
      onCreated(created.postId);
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : '새 임시글을 만들지 못했어요');
    } finally {
      setBusy(false);
    }
  }

  async function loadServer() {
    setBusy(true);
    try {
      await onLoadServer();
    } finally {
      setBusy(false);
    }
  }

  function renderItems(layout: 'split' | 'unified') {
    if (layout === 'split') {
      // 접힌 구간 단위로 나눠 좌우 줄을 만든다
      const blocks: ({ type: 'rows'; lines: DiffLine[] } | Extract<FoldItem, { type: 'fold' }>)[] =
        [];
      for (const item of visible) {
        if (item.type === 'fold') {
          blocks.push(item);
        } else {
          const last = blocks[blocks.length - 1];
          if (last && last.type === 'rows') {
            last.lines.push(item.line);
          } else {
            blocks.push({ type: 'rows', lines: [item.line] });
          }
        }
      }
      return blocks.map((block, index) =>
        block.type === 'fold' ? (
          <FoldButton key={block.id} count={block.lines.length} onClick={() => expand(block.id)} />
        ) : (
          sideBySideRows(block.lines).map((row, k) => (
            <div className="diff-row" key={`${index}-${k}`}>
              <LineCell line={row.left} current={current} />
              <LineCell line={row.right} current={current} />
            </div>
          ))
        ),
      );
    }
    return visible.map((item, index) =>
      item.type === 'fold' ? (
        <FoldButton key={item.id} count={item.lines.length} onClick={() => expand(item.id)} />
      ) : (
        <div className="diff-row" key={index}>
          <LineCell line={item.line} current={current} />
        </div>
      ),
    );
  }

  function expand(id: string) {
    setExpanded((prev) => new Set(prev).add(id));
  }

  const layout = wide ? 'split' : 'unified';

  return (
    <div
      className="diff-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="diff-dialog-title"
    >
      <div className="diff-dialog-box">
        <h2 id="diff-dialog-title">저장된 내용과 비교</h2>
        <p className="diff-legend">
          <span className="diff-removed diff-chip">− {savedTime}에 저장된 내용</span>{' '}
          <span className="diff-added diff-chip">+ 지금 편집 중인 내용</span>
        </p>

        <div className="diff-nav">
          <span>차이 {diff.changeCount}개</span>
          {current !== null ? (
            <span>
              {current + 1} / {diff.changeCount}
            </span>
          ) : null}
          <button
            type="button"
            disabled={diff.changeCount === 0}
            onClick={() => setCurrent((c) => previousChange(c, diff.changeCount))}
          >
            이전 차이
          </button>
          <button
            type="button"
            disabled={diff.changeCount === 0}
            onClick={() => setCurrent((c) => nextChange(c, diff.changeCount))}
          >
            다음 차이
          </button>
        </div>

        {diff.title ? (
          <div className="diff-title" data-testid="diff-title" data-change={diff.title.changeIndex}>
            <div
              className={`diff-cell diff-removed${current === diff.title.changeIndex ? ' diff-current' : ''}`}
            >
              <span className="diff-sign" aria-label="지움">
                −
              </span>
              <span className="diff-text">
                제목: <Segments segments={diff.title.old} kind="removed" />
              </span>
            </div>
            <div
              className={`diff-cell diff-added${current === diff.title.changeIndex ? ' diff-current' : ''}`}
            >
              <span className="diff-sign" aria-label="더함">
                +
              </span>
              <span className="diff-text">
                제목: <Segments segments={diff.title.new} kind="added" />
              </span>
            </div>
          </div>
        ) : null}

        {layout === 'split' ? (
          <div className="diff-columns-head" aria-hidden="true">
            <span>저장된 내용</span>
            <span>편집 중인 내용</span>
          </div>
        ) : null}
        <div className="diff-body" data-testid="diff-body" data-layout={layout} ref={bodyRef}>
          {renderItems(layout)}
        </div>

        {message ? (
          <p role="alert" className="form-error">
            {message}
          </p>
        ) : null}

        <div className="diff-actions">
          <button type="button" disabled={busy} onClick={() => setConfirming(true)}>
            편집 중인 내용으로 저장
          </button>
          <button type="button" disabled={busy} onClick={() => void loadServer()}>
            저장된 내용 불러오기
          </button>
          <button type="button" disabled={busy} onClick={() => void saveAsNew()}>
            새 임시글로 따로 저장
          </button>
          <button type="button" disabled={busy} onClick={onClose}>
            닫기
          </button>
        </div>
      </div>

      {confirming ? (
        <ConfirmDialog
          message={`${savedTime}에 저장된 내용이 지금 편집 중인 내용으로 바뀌어요. 정말 저장할까요?`}
          confirmLabel="저장"
          cancelLabel="돌아가기"
          busy={busy}
          onConfirm={() => void keepMine()}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </div>
  );
}

function FoldButton({ count, onClick }: { count: number; onClick: () => void }) {
  return (
    <div className="diff-fold">
      <button type="button" onClick={onClick}>
        같은 줄 {count}개 펼치기
      </button>
    </div>
  );
}
