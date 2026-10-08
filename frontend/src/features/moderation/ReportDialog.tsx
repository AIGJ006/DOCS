import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import type { ReportReason, TargetType } from '../../api/types/moderation';
import '../../components/dialogs.css';
import './moderation.css';
import { REASON_LABELS } from './reasonLabels';
import { DETAIL_MAX, REPORT_TEXT, countChars, useReport } from './useReport';

export interface ReportDialogProps {
  targetType: TargetType;
  targetId: number;
  /** [취소]·Esc (요청 없음) */
  onClose: () => void;
  /** 접수됨 */
  onDone: () => void;
  /** 401·403 계정 상태 거부를 안내했으면 true (`useAuthGate().handle`) */
  onGate: (error: unknown) => boolean;
}

const REASONS = Object.keys(REASON_LABELS) as ReportReason[];

/**
 * 신고 창 (014 T024, FR-005~FR-008). 사유 6개 중 하나, 기타일 때만 설명(200자, 앞뒤 공백을 빼고 1자 이상). 처음 포커스는 첫 사유,
 * Tab은 창 안에서만 돌고 Esc·[취소]는 요청 없이 닫는다.
 */
export default function ReportDialog({
  targetType,
  targetId,
  onClose,
  onDone,
  onGate,
}: ReportDialogProps) {
  const id = useId();
  const boxRef = useRef<HTMLDivElement>(null);
  const firstRef = useRef<HTMLInputElement>(null);
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [detail, setDetail] = useState('');
  const { submit, submitting, error } = useReport(targetType, targetId, onGate);

  useEffect(() => {
    firstRef.current?.focus();
  }, []);

  const needsDetail = reason === 'OTHER';
  const detailCount = countChars(detail);
  const ready =
    reason !== null &&
    !submitting &&
    (!needsDetail || (detail.trim().length > 0 && detailCount <= DETAIL_MAX));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready || reason === null) {
      return;
    }
    const outcome = await submit(reason, detail);
    if (outcome === 'accepted') {
      onDone();
    } else if (outcome === 'gated') {
      onClose();
    }
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const focusables = boxRef.current?.querySelectorAll<HTMLElement>(
      'input:not([disabled]), textarea:not([disabled]), button:not([disabled])',
    );
    if (!focusables || focusables.length === 0) {
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="app-dialog-backdrop">
      <div
        ref={boxRef}
        className="app-dialog report-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        onKeyDown={onKeyDown}
      >
        <h2 id={`${id}-title`} className="app-dialog-title">
          {REPORT_TEXT.title(targetType)}
        </h2>
        <form onSubmit={(event) => void onSubmit(event)}>
          <fieldset className="report-reasons">
            <legend className="report-legend">신고 사유</legend>
            {REASONS.map((code, index) => (
              <label key={code} className="report-reason">
                <input
                  ref={index === 0 ? firstRef : undefined}
                  type="radio"
                  name={`${id}-reason`}
                  value={code}
                  checked={reason === code}
                  onChange={() => setReason(code)}
                />
                <span>{REASON_LABELS[code]}</span>
              </label>
            ))}
          </fieldset>
          {needsDetail ? (
            <div className="report-detail">
              <label htmlFor={`${id}-detail`}>{REPORT_TEXT.detailLabel}</label>
              <textarea
                id={`${id}-detail`}
                value={detail}
                maxLength={DETAIL_MAX}
                rows={3}
                placeholder={REPORT_TEXT.detailPlaceholder}
                onChange={(event) => setDetail(event.target.value)}
              />
              <span className="report-counter" aria-live="polite">
                {detailCount}/{DETAIL_MAX}
              </span>
            </div>
          ) : null}
          {error ? (
            <p role="alert" className="report-error">
              {error}
            </p>
          ) : null}
          <div className="app-dialog-actions">
            <button type="button" onClick={onClose}>
              {REPORT_TEXT.cancel}
            </button>
            <button type="submit" className="app-dialog-confirm" disabled={!ready}>
              {submitting ? REPORT_TEXT.submitting : REPORT_TEXT.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
