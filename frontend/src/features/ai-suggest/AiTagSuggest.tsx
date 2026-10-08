import AiConsentDialog from './AiConsentDialog';
import { AI_MESSAGES } from './messages';
import { useTagSuggest, type SuggestApi } from './useTagSuggest';
import './aiSuggest.css';

/**
 * 발행 창의 AI 태그 추천 (013 T031·T043·T050, research R13). 008 `TagInput` 바로 아래에 둔다.
 *
 * - 상태 API `available = false`면 영역 전체를 그리지 않는다.
 * - [AI 태그 추천] → 결과가 있으면 [다시 추천](`refresh: true`). 요청 중에는 비활성 + "추천 중…"(예상 공급자가 자체 AI면 "자체 AI로
 *   추천 중이라 조금 걸려요").
 * - 칩 `+ spring`을 누를 때만 `onAdd`로 태그 입력에 더한다(서버 저장 없음 — 발행 때 확정, FR-004). 붙인 칩은 목록에서 빠진다.
 *   태그 이름은 글자로만 그린다.
 * - 태그가 최대 개수면 버튼을 끄고 "태그를 더 붙일 수 없어요".
 */
interface Props {
  postId: number;
  title: string;
  contentMd: string;
  /** 지금 붙인 태그 (정규화된 이름) */
  currentTags: string[];
  max: number;
  /** 칩을 눌렀다. 붙였으면 true */
  onAdd: (name: string) => boolean;
  disabled?: boolean;
  /** 시험에서 바꿔 끼운다 */
  api?: SuggestApi;
}

export default function AiTagSuggest({
  postId,
  title,
  contentMd,
  currentTags,
  max,
  onAdd,
  disabled = false,
  api,
}: Props) {
  const { state, request, agree, cancelConsent } = useTagSuggest(postId, api);
  const { status, phase, result } = state;

  if (!status || !status.available) {
    return null;
  }

  const full = currentTags.length >= max;
  const loading = phase === 'loading';
  const chips = (result?.tags ?? []).filter((tag) => !currentTags.includes(tag));
  const label = loading
    ? state.pendingProvider === 'OLLAMA'
      ? AI_MESSAGES.loadingOllama
      : AI_MESSAGES.loading
    : result
      ? '다시 추천'
      : 'AI 태그 추천';

  function press() {
    void request({ title, contentMd, currentTags, refresh: result !== null });
  }

  return (
    <div className="ai-suggest">
      <div className="ai-suggest__bar">
        <button
          type="button"
          className="ai-suggest__button"
          onClick={press}
          disabled={disabled || loading || full || state.blocked !== null || phase === 'agreeing'}
          aria-busy={loading || undefined}
        >
          {label}
        </button>
        {state.remainingToday !== null && state.blocked === null ? (
          <span className="ai-suggest__remaining">
            {AI_MESSAGES.remaining(state.remainingToday)}
          </span>
        ) : null}
      </div>

      {full ? <p className="ai-suggest__note">{AI_MESSAGES.full}</p> : null}
      {state.blocked ? <p className="ai-suggest__message">{state.blocked}</p> : null}
      {state.message ? (
        <p className="ai-suggest__message" role="status">
          {state.message}
        </p>
      ) : null}

      {chips.length > 0 ? (
        <ul className="ai-suggest__chips" aria-label="AI 추천 태그">
          {chips.map((tag) => (
            <li key={tag}>
              <button
                type="button"
                className="ai-suggest__chip"
                aria-label={`${tag} 태그 붙이기`}
                disabled={disabled || full}
                onClick={() => onAdd(tag)}
              >
                + {tag}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      {result && result.tags.length > 0 ? (
        <p className="ai-suggest__note">
          {result.truncated ? AI_MESSAGES.truncatedNote : AI_MESSAGES.aiNote}
        </p>
      ) : null}

      {phase === 'consent' || phase === 'agreeing' ? (
        <AiConsentDialog
          busy={phase === 'agreeing'}
          error={state.consentError}
          onAgree={() => void agree()}
          onCancel={cancelConsent}
        />
      ) : null}
    </div>
  );
}
