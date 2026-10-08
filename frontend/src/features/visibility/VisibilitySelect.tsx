import { useId, useState, type ChangeEvent } from 'react';
import { ApiError, type RequestOptions } from '../../api/client';
import { setVisibility, type SetVisibilityResult } from '../../api/posts';
import {
  INVALID_VISIBILITY_MESSAGE,
  optionText,
  selectableVisibilities,
  type VisibilityValue,
} from './visibilityOptions';
import './visibility.css';

export interface VisibilitySelectProps {
  /** 지금 값 */
  value: VisibilityValue;
  /** 값만 고르는 모드(발행 설정): 고른 값을 알린다. 서버는 부르지 않는다 */
  onChange?: (value: VisibilityValue) => void;
  /**
   * 즉시 저장 모드: 글 번호가 있으면 고르는 즉시 `PUT /api/posts/{postId}/visibility`를 부르고 응답 값으로 표시를 바꾼다
   * (다시 발행 없음, 004 FR-016).
   */
  postId?: number;
  /** 즉시 저장이 성공한 뒤 */
  onSaved?: (result: SetVisibilityResult) => void;
  /**
   * 즉시 저장이 실패했을 때(400 `INVALID_VISIBILITY` 제외). 주지 않으면 서버 문구를 선택 상자 옆에 보인다. 404 `NOT_FOUND`는
   * 공통 API 클라이언트가 공통 404 화면으로 넘긴다(`requestOptions.notFoundScreen`이 `false`가 아니면).
   */
  onError?: (error: unknown) => void;
  /** 바꾸기 전 확인 (예: 비공개 → 공개 확인창). `false`면 바꾸지 않는다 */
  confirm?: (from: VisibilityValue, to: VisibilityValue) => boolean | Promise<boolean>;
  /** 즉시 저장 요청 옵션 (006 줄 단위 오류는 `{ notFoundScreen: false }`) */
  requestOptions?: RequestOptions;
  disabled?: boolean;
  /** 화면 글자 (기본 "공개 범위") */
  label?: string;
  /** 친구 공개 선택지를 보이나 (기본: 빌드 설정 `VITE_FRIENDS_VISIBILITY`) */
  friendsEnabled?: boolean;
  /** 서버 검증 오류 문구 (발행 설정의 `errors[field=visibility]`) */
  error?: string | null;
}

/**
 * 공개 범위 선택 (004 T039). 발행 설정(002)·글 상세(005)·내 글 관리(006)가 함께 쓴다.
 *
 * - 선택지: "🌐 전체 공개"·"🔒 나만 보기" (친구 공개는 선택 구현 빌드에서만).
 * - 즉시 저장 모드(`postId`)에서 400이면 "공개 범위를 다시 선택해 주세요"를 보이고 이전 값으로 되돌린다. 그 밖의 실패도 이전 값으로
 *   되돌리고 `onError`(없으면 서버 문구)로 넘긴다.
 * - 서버 판정이 먼저이고 이 부품은 작성자에게만 보이는 자리에 둔다(42 P-1, FR-045).
 */
export default function VisibilitySelect({
  value,
  onChange,
  postId,
  onSaved,
  onError,
  confirm,
  requestOptions,
  disabled = false,
  label = '공개 범위',
  friendsEnabled,
  error,
}: VisibilitySelectProps) {
  const id = useId();
  const [current, setCurrent] = useState<VisibilityValue>(value);
  const [shownValue, setShownValue] = useState<VisibilityValue>(value);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // 부모가 값을 바꾸면 따라간다 (렌더 중 상태 조정 — react-hooks 규칙)
  if (value !== shownValue) {
    setShownValue(value);
    setCurrent(value);
  }

  const values = selectableVisibilities(friendsEnabled);
  const choices = values.includes(current) ? values : [...values, current];

  async function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const next = event.target.value as VisibilityValue;
    const previous = current;
    if (next === previous) {
      return;
    }
    setMessage(null);
    if (confirm && !(await confirm(previous, next))) {
      setCurrent(previous);
      return;
    }
    if (postId === undefined) {
      setCurrent(next);
      onChange?.(next);
      return;
    }
    setCurrent(next);
    setSaving(true);
    try {
      const result = await setVisibility(postId, next, requestOptions);
      setCurrent(result.visibility);
      onChange?.(result.visibility);
      onSaved?.(result);
    } catch (caught) {
      setCurrent(previous);
      if (caught instanceof ApiError && caught.code === 'INVALID_VISIBILITY') {
        setMessage(INVALID_VISIBILITY_MESSAGE);
      } else if (caught instanceof ApiError && caught.status === 404) {
        onError?.(caught);
      } else if (onError) {
        onError(caught);
      } else {
        setMessage(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요');
      }
    } finally {
      setSaving(false);
    }
  }

  const shownError = message ?? error ?? null;
  return (
    <span className="visibility-select">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={current}
        disabled={disabled || saving}
        aria-invalid={shownError ? 'true' : undefined}
        aria-describedby={shownError ? `${id}-error` : undefined}
        onChange={(event) => void handleChange(event)}
      >
        {choices.map((choice) => (
          <option key={choice} value={choice}>
            {optionText(choice)}
          </option>
        ))}
      </select>
      {shownError ? (
        <span id={`${id}-error`} role="alert" className="visibility-select-error">
          {shownError}
        </span>
      ) : null}
    </span>
  );
}
