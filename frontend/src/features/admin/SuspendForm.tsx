import { useId, useState } from 'react';
import type { SuspensionDuration } from '../../api/types/moderation';
import { countChars } from '../moderation/useReport';
import { ADMIN_TEXT, DURATION_LABELS, SUSPENSION_REASON_MAX } from './adminText';
import './admin.css';

const DURATIONS = Object.keys(DURATION_LABELS) as SuspensionDuration[];

export interface SuspendFormProps {
  busy?: boolean;
  onSubmit: (duration: SuspensionDuration, reason: string) => void;
}

/** 정지 칸 (014 T054): 기간 4개 + 사유(필수, 200자). */
export default function SuspendForm({ busy = false, onSubmit }: SuspendFormProps) {
  const id = useId();
  const [duration, setDuration] = useState<SuspensionDuration>('P7D');
  const [reason, setReason] = useState('');
  const count = countChars(reason);
  const ready = !busy && reason.trim().length > 0 && count <= SUSPENSION_REASON_MAX;
  return (
    <form
      className="suspend-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (ready) {
          onSubmit(duration, reason.trim());
        }
      }}
    >
      <fieldset className="admin-fieldset" disabled={busy}>
        <legend>{ADMIN_TEXT.durationLegend}</legend>
        {DURATIONS.map((code) => (
          <label key={code} className="admin-radio">
            <input
              type="radio"
              name={`${id}-duration`}
              value={code}
              checked={duration === code}
              onChange={() => setDuration(code)}
            />
            <span>{DURATION_LABELS[code]}</span>
          </label>
        ))}
      </fieldset>
      <label htmlFor={`${id}-reason`}>{ADMIN_TEXT.suspendReasonLabel}</label>
      <textarea
        id={`${id}-reason`}
        className="admin-textarea"
        value={reason}
        maxLength={SUSPENSION_REASON_MAX}
        rows={3}
        required
        onChange={(event) => setReason(event.target.value)}
      />
      <span className="admin-muted admin-counter">
        {count}/{SUSPENSION_REASON_MAX}
      </span>
      <div className="admin-actions">
        <button type="submit" className="admin-button admin-button-danger" disabled={!ready}>
          {ADMIN_TEXT.suspend}
        </button>
      </div>
    </form>
  );
}
