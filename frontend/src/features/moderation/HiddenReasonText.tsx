import { hiddenNotice } from './hiddenNotice';

export default function HiddenReasonText({ reason }: { reason: string | null | undefined }) {
  return <span className="hidden-reason-text">{hiddenNotice(reason)}</span>;
}
