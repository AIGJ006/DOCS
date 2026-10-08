import { useEffect, useId, useState } from 'react';
import { getAiConsent, revokeAi } from '../../api/tagSuggestions';
import type { AiConsent } from '../../api/types/tagSuggestions';
import { formatDate } from '../time/dateFormat';
import './aiSuggest.css';

export interface ConsentApi {
  getAiConsent: () => Promise<AiConsent>;
  revokeAi: () => Promise<AiConsent>;
}

const DEFAULT_API: ConsentApi = { getAiConsent, revokeAi };
const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

/**
 * 설정 화면 "AI 동의" 칸 (013 T037, US2 #4). 세 상태 — 동의함(날짜)·안 함·옛 버전("다시 동의가 필요해요").
 * 동의는 발행 창의 동의 창에서만 하고, 여기서는 [동의 취소](`DELETE`)만 한다. 실패는 이 칸 안에서만 알린다.
 */
export default function AiConsentSettings({ api = DEFAULT_API }: { api?: ConsentApi }) {
  const titleId = useId();
  const [consent, setConsent] = useState<AiConsent | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api
      .getAiConsent()
      .then((value) => active && setConsent(value))
      .catch(() => active && setLoadFailed(true));
    return () => {
      active = false;
    };
  }, [api]);

  async function revoke() {
    setBusy(true);
    setError(null);
    try {
      setConsent(await api.revokeAi());
    } catch {
      setError(FAILED_MESSAGE);
    } finally {
      setBusy(false);
    }
  }

  let body;
  if (loadFailed) {
    body = <p role="alert">AI 동의 상태를 불러오지 못했어요</p>;
  } else if (!consent) {
    body = <p aria-busy="true">불러오는 중이에요</p>;
  } else {
    const hasRow = consent.version !== null;
    let state: string;
    if (consent.agreed && consent.agreedAt) {
      state = `${formatDate(consent.agreedAt)}에 동의했어요`;
    } else if (hasRow) {
      state = '동의 문구가 바뀌어 다시 동의가 필요해요. 다음 추천 때 동의 창이 떠요';
    } else {
      state = '동의하지 않았어요. 발행 창에서 AI 태그 추천을 처음 쓸 때 동의 창이 떠요';
    }
    body = (
      <>
        <p>{state}</p>
        <p className="field-help">
          동의하면 AI 태그 추천을 쓸 때 공개 글의 제목과 본문 앞부분이 외부 AI 서비스(Google
          Gemini)로 전송돼요. 취소하면 다음 추천 때 다시 묻습니다.
        </p>
        {hasRow ? (
          <button type="button" onClick={() => void revoke()} disabled={busy}>
            동의 취소
          </button>
        ) : null}
        {error ? (
          <p role="alert" className="form-error">
            {error}
          </p>
        ) : null}
      </>
    );
  }

  return (
    <section aria-labelledby={titleId}>
      <h2 id={titleId}>AI 동의</h2>
      {body}
    </section>
  );
}
