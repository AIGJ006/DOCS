import { useEffect, useState } from 'react';
import { checkHandle, checkNickname } from '../api/availability';
import { ApiError } from '../api/client';

/** 입력이 멈춘 뒤 확인을 보내기까지 (08 §4-2) */
export const AVAILABILITY_DELAY_MS = 500;

const MESSAGES: Record<string, string> = {
  HANDLE_INVALID_FORMAT:
    '영문 소문자·숫자·_로 3~36자까지 쓸 수 있어요 (처음과 끝은 영문 소문자나 숫자)',
  HANDLE_PREFIX_MISMATCH: '블로그 주소 접두어가 가입 수단과 맞지 않아요',
  HANDLE_RESERVED: '사용할 수 없는 주소예요',
  HANDLE_BANNED_WORD: '사용할 수 없는 단어가 들어 있어요',
  HANDLE_DUPLICATE: '이미 사용 중인 주소예요',
  NICKNAME_INVALID_FORMAT: '한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)',
  NICKNAME_LETTER_REQUIRED: '한글이나 영문을 1자 이상 넣어 주세요',
  NICKNAME_RESERVED: '사용할 수 없는 닉네임이에요',
  NICKNAME_BANNED_WORD: '사용할 수 없는 단어가 들어 있어요',
  NICKNAME_DUPLICATE: '이미 사용 중인 닉네임이에요',
};

type Hint = { available: true } | { available: false; message: string; suggestion: string | null };

interface AvailabilityHintProps {
  kind: 'handle' | 'nickname';
  /** 주소는 접두어 포함 전체 */
  value: string;
  /** 예약어·중복일 때 서버가 준 대안 주소(접두어 포함)를 고르면 부른다 */
  onSuggestion?: (suggestion: string) => void;
}

/**
 * 사용 가능 확인 안내 (FR-020·026). 입력이 0.5초 멈추면 한 번 묻는다. 안내용이며 최종 판정은 가입 요청이 한다.
 * 429(요청 제한)·네트워크 오류는 조용히 넘어간다.
 */
export default function AvailabilityHint({ kind, value, onSuggestion }: AvailabilityHintProps) {
  const [result, setResult] = useState<{ value: string; hint: Hint } | null>(null);
  const trimmed = value.trim();

  useEffect(() => {
    if (trimmed === '') {
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      const request =
        kind === 'handle'
          ? checkHandle(trimmed, controller.signal).then((r): Hint =>
              r.available
                ? { available: true }
                : {
                    available: false,
                    message: MESSAGES[r.reason ?? ''] ?? '사용할 수 없는 주소예요',
                    suggestion: r.suggestion,
                  },
            )
          : checkNickname(trimmed, controller.signal).then((r): Hint =>
              r.available
                ? { available: true }
                : {
                    available: false,
                    message: MESSAGES[r.code ?? ''] ?? '사용할 수 없는 닉네임이에요',
                    suggestion: null,
                  },
            );
      request
        .then((hint) => setResult({ value: trimmed, hint }))
        .catch((error: unknown) => {
          if (error instanceof ApiError || error instanceof Error) {
            setResult(null);
          }
        });
    }, AVAILABILITY_DELAY_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [kind, trimmed]);

  if (!result || result.value !== trimmed) {
    return null;
  }
  const { hint } = result;
  if (hint.available) {
    return (
      <p className="field-help" role="status">
        사용할 수 있어요
      </p>
    );
  }
  return (
    <div className="field-error" role="status">
      <p>{hint.message}</p>
      {hint.suggestion && onSuggestion && (
        <button
          type="button"
          className="link-button"
          onClick={() => onSuggestion(hint.suggestion as string)}
        >
          {hint.suggestion} 쓰기
        </button>
      )}
    </div>
  );
}
