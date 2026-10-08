import { useId, useState } from 'react';
import type { ReportReason, ResolutionAction } from '../../api/types/moderation';
import { REASON_LABELS } from '../moderation/reasonLabels';
import { ADMIN_TEXT } from './adminText';
import './admin.css';

export interface ResolutionFormProps {
  /** 혼자 신고한 건 — 버튼 비활성 + 안내 */
  disabled?: boolean;
  busy?: boolean;
  onSubmit: (action: ResolutionAction, reason: ReportReason | null) => void;
}

const REASONS = Object.keys(REASON_LABELS) as ReportReason[];

/** 처리 칸 (014 T039): 숨김 사유 6개 중 하나를 골라야 [숨기기]가 켜진다. [문제없음]은 사유 없이. */
export default function ResolutionForm({
  disabled = false,
  busy = false,
  onSubmit,
}: ResolutionFormProps) {
  const id = useId();
  const [reason, setReason] = useState<ReportReason | null>(null);
  const off = disabled || busy;
  return (
    <div className="resolution-form">
      <fieldset className="admin-fieldset" disabled={off}>
        <legend>{ADMIN_TEXT.hideReasonLegend}</legend>
        {REASONS.map((code) => (
          <label key={code} className="admin-radio">
            <input
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
      {disabled ? <p className="admin-muted">{ADMIN_TEXT.onlyMyReport}</p> : null}
      <div className="admin-actions">
        <button
          type="button"
          className="admin-button admin-button-danger"
          disabled={off || reason === null}
          onClick={() => onSubmit('HIDE', reason)}
        >
          {ADMIN_TEXT.hide}
        </button>
        <button
          type="button"
          className="admin-button"
          disabled={off}
          onClick={() => onSubmit('REJECT', null)}
        >
          {ADMIN_TEXT.reject}
        </button>
      </div>
    </div>
  );
}
