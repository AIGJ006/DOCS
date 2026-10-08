import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ApiError, type FieldError } from '../api/client';
import {
  getMyProfile,
  getMySettings,
  updateMyProfile,
  updateMySettings,
  type MyProfile,
  type MySettings,
  type ProfileUpdate,
  type Provider,
  type SettingsUpdate,
} from '../api/me';
import type { SetVisibilityResult, Visibility } from '../api/posts';
import DefaultAvatar from '../components/DefaultAvatar';
import ProfileImageCropper from '../components/ProfileImageCropper';
import { groupFieldErrors } from '../features/auth/fieldErrors';
import FriendLists from '../features/friends/FriendLists';
import { useSession } from '../features/auth/useSession';
import { uploadProfileImage } from '../features/profile/uploadProfileImage';
import PasswordChangeForm from '../features/settings/PasswordChangeForm';
import { formatDate } from '../features/time/dateFormat';
import VisibilitySelect from '../features/visibility/VisibilitySelect';
import StorageUsageBar from '../components/StorageUsageBar';
import '../features/auth/auth.css';
import '../features/settings/settings.css';

const BIO_MAX = 200;
const PROVIDER_LABEL: Record<Provider, string> = {
  LOCAL: '이메일',
  GOOGLE: 'Google',
  GITHUB: 'GitHub',
};
const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

const HOUR_MINUTE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** 직전 로그인 `YYYY.MM.DD HH:mm` (Asia/Seoul, FR-058). */
function formatLoginTime(value: string): string {
  return `${formatDate(value)} ${HOUR_MINUTE.format(new Date(value))}`;
}

type PendingPhoto =
  { kind: 'none' } | { kind: 'default' } | { kind: 'cropped'; blob: Blob; url: string };

/**
 * 설정 화면 `/settings` (001 T122, FR-046~053·045, 11 §5·§6). 비로그인은 `/login?returnTo=%2Fsettings`로.
 *
 * - 프로필: 사진([사진 바꾸기]·[기본 이미지로] — 고른 사진은 [저장] 때 올리고 연결한다), 닉네임(30일 제한 중이면 막고 다음 변경
 *   가능일), 소개(글자 수는 코드 포인트), 블로그 주소 `@handle` 읽기 전용. [저장] 한 번에 바꾼 칸만 보내고, 실패한 칸을 모두 보인다.
 * - 계정: 이메일 읽기 전용, 로그인 수단, 직전 로그인(없으면 "첫 로그인"), 비밀번호 변경(이메일 계정만), 새 글 기본 공개 범위·최근 활동 공개(바꾸면 바로 저장), 약관 링크.
 * - 친구: 받은 친구 요청·내 친구 목록(`FriendLists`, US7).
 * - 회원 탈퇴: 계정 칸 맨 아래 [회원 탈퇴] 링크 → `/settings/withdraw` (015).
 *
 * 닉네임·소개는 React 텍스트로만 그린다(HTML로 해석하지 않음).
 */
export default function SettingsPage() {
  const { me, loading, refresh } = useSession();
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [settings, setSettings] = useState<MySettings | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const memberId = me?.memberId;

  useEffect(() => {
    if (memberId === undefined) {
      return;
    }
    let active = true;
    Promise.all([getMyProfile(), getMySettings()])
      .then(([p, s]) => {
        if (active) {
          setProfile(p);
          setSettings(s);
        }
      })
      .catch(() => active && setLoadFailed(true));
    return () => {
      active = false;
    };
  }, [memberId]);

  if (!loading && !me) {
    return <Navigate to="/login?returnTo=%2Fsettings" replace />;
  }
  if (loadFailed) {
    return (
      <main className="settings-page">
        <h1>설정</h1>
        <p role="alert">설정을 불러오지 못했어요. 새로 고쳐 주세요</p>
      </main>
    );
  }
  if (!profile || !settings) {
    return (
      <main className="settings-page" aria-busy="true">
        <h1>설정</h1>
        <p>불러오는 중이에요</p>
      </main>
    );
  }
  return (
    <main className="settings-page">
      <h1>설정</h1>
      <ProfileSection
        key={profile.handle}
        profile={profile}
        onSaved={(saved) => {
          setProfile(saved);
          void refresh();
        }}
      />
      <AccountSection settings={settings} onChange={setSettings} />
      <section aria-labelledby="storage-title">
        <h2 id="storage-title">사진 저장 공간</h2>
        <StorageUsageBar />
      </section>
      <FriendLists />
      <section aria-labelledby="withdraw-title">
        <h2 id="withdraw-title">회원 탈퇴</h2>
        <p>회원 탈퇴는 준비 중이에요.</p>
      </section>
    </main>
  );
}

function ProfileSection({
  profile,
  onSaved,
}: {
  profile: MyProfile;
  onSaved: (profile: MyProfile) => void;
}) {
  const [nickname, setNickname] = useState(profile.nickname);
  const [bio, setBio] = useState(profile.bio ?? '');
  const [photo, setPhoto] = useState<PendingPhoto>({ kind: 'none' });
  const [cropping, setCropping] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, FieldError[]>>({});
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(
    () => () => {
      if (photo.kind === 'cropped') {
        URL.revokeObjectURL(photo.url);
      }
    },
    [photo],
  );

  const nicknameLocked = profile.nicknameChangeAvailableAt !== null;

  function changes(): ProfileUpdate {
    const update: ProfileUpdate = {};
    if (!nicknameLocked && nickname !== profile.nickname) {
      update.nickname = nickname;
    }
    if (bio !== (profile.bio ?? '')) {
      update.bio = bio === '' ? null : bio;
    }
    if (photo.kind === 'default') {
      update.profileImageId = null;
    }
    return update;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const update = changes();
    if (Object.keys(update).length === 0 && photo.kind !== 'cropped') {
      setMessage({ kind: 'error', text: '바꾼 내용이 없어요' });
      return;
    }
    setSaving(true);
    setMessage(null);
    setFieldErrors({});
    try {
      if (photo.kind === 'cropped') {
        try {
          update.profileImageId = await uploadProfileImage(photo.blob);
        } catch {
          setMessage({ kind: 'error', text: '사진을 올리지 못했어요. 다시 시도해 주세요' });
          return;
        }
      }
      const saved = await updateMyProfile(update);
      setPhoto({ kind: 'none' });
      setNickname(saved.nickname);
      setBio(saved.bio ?? '');
      setMessage({ kind: 'ok', text: '저장했어요' });
      onSaved(saved);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'NICKNAME_CHANGE_TOO_SOON') {
        setFieldErrors({
          nickname: [{ field: 'nickname', code: caught.code, message: caught.message }],
        });
      } else if (caught instanceof ApiError && caught.errors.length > 0) {
        setFieldErrors(groupFieldErrors(caught.errors));
      } else {
        setMessage({
          kind: 'error',
          text: caught instanceof ApiError ? caught.message : FAILED_MESSAGE,
        });
      }
    } finally {
      setSaving(false);
    }
  }

  function errorsOf(field: string) {
    const list = fieldErrors[field] ?? [];
    return list.length === 0 ? null : (
      <div className="field-error" id={`profile-${field}-error`}>
        {list.map((e) => (
          <p key={e.code}>{e.message}</p>
        ))}
      </div>
    );
  }

  const bioLength = [...bio].length;
  const currentPhoto =
    photo.kind === 'cropped' ? (
      <img src={photo.url} alt="새 프로필 사진 미리보기" />
    ) : photo.kind === 'default' || !profile.profileImageUrl ? (
      <DefaultAvatar nickname={nickname || profile.nickname} handle={profile.handle} size={64} />
    ) : (
      <img src={profile.profileImageUrl} alt="프로필 사진" />
    );

  return (
    <section aria-labelledby="profile-title">
      <h2 id="profile-title">프로필</h2>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <div className="profile-photo">
          {currentPhoto}
          <div className="settings-actions">
            <button type="button" className="link-button" onClick={() => setCropping(true)}>
              사진 바꾸기
            </button>
            {(profile.profileImageUrl || photo.kind === 'cropped') && photo.kind !== 'default' && (
              <button
                type="button"
                className="link-button"
                onClick={() => setPhoto({ kind: 'default' })}
              >
                기본 이미지로
              </button>
            )}
          </div>
        </div>
        {cropping && (
          <ProfileImageCropper
            onCropped={(blob) => {
              setPhoto({ kind: 'cropped', blob, url: URL.createObjectURL(blob) });
              setCropping(false);
            }}
            onCancel={() => setCropping(false)}
          />
        )}
        {errorsOf('profileImageId')}

        <div className="field">
          <label htmlFor="profile-nickname">닉네임</label>
          <input
            id="profile-nickname"
            value={nickname}
            disabled={nicknameLocked}
            maxLength={20}
            onChange={(e) => setNickname(e.target.value)}
            aria-invalid={fieldErrors.nickname ? true : undefined}
            aria-describedby={fieldErrors.nickname ? 'profile-nickname-error' : undefined}
          />
          {nicknameLocked && profile.nicknameChangeAvailableAt && (
            <p className="field-help">
              닉네임은 바꾼 뒤 30일이 지나야 다시 바꿀 수 있어요. 다음 변경 가능일{' '}
              {formatDate(profile.nicknameChangeAvailableAt)}
            </p>
          )}
          {errorsOf('nickname')}
        </div>

        <div className="field">
          <label htmlFor="profile-bio">소개</label>
          <textarea
            id="profile-bio"
            rows={4}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            aria-invalid={fieldErrors.bio ? true : undefined}
            aria-describedby={fieldErrors.bio ? 'profile-bio-error' : undefined}
          />
          <span className="counter" aria-live="polite">
            {bioLength}/{BIO_MAX}
          </span>
          {errorsOf('bio')}
        </div>

        <div className="field">
          <span>블로그 주소</span>
          <span>@{profile.handle}</span>
        </div>

        {message && (
          <p
            role={message.kind === 'error' ? 'alert' : 'status'}
            className={message.kind === 'error' ? 'form-error' : 'status-ok'}
          >
            {message.text}
          </p>
        )}
        <button type="submit" className="primary" disabled={saving}>
          저장
        </button>
      </form>
    </section>
  );
}

function AccountSection({
  settings,
  onChange,
}: {
  settings: MySettings;
  onChange: (settings: MySettings) => void;
}) {
  const [error, setError] = useState<string | null>(null);

  async function save(update: SettingsUpdate): Promise<MySettings | null> {
    setError(null);
    try {
      const saved = await updateMySettings(update);
      onChange(saved);
      return saved;
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : FAILED_MESSAGE);
      return null;
    }
  }

  /**
   * 004 `VisibilitySelect`의 즉시 저장 모드: `PATCH /api/me/settings`로 저장하고 저장된 값을 돌려준다. 실패하면 위에서 문구를
   * 이미 보였으므로 null — 선택 상자는 이전 값으로 돌아간다. 글이 아니라 `firstPublicAt`은 늘 null이다.
   */
  async function saveDefaultVisibility(next: Visibility): Promise<SetVisibilityResult | null> {
    const saved = await save({ defaultVisibility: next });
    return saved ? { visibility: saved.defaultVisibility, firstPublicAt: null } : null;
  }

  return (
    <section aria-labelledby="account-title">
      <h2 id="account-title">계정</h2>
      <dl className="settings-dl">
        <dt>이메일</dt>
        <dd>{settings.email ?? '없음'}</dd>
        <dt>로그인 수단</dt>
        <dd>{PROVIDER_LABEL[settings.provider]}</dd>
        <dt>직전 로그인</dt>
        <dd>
          {settings.previousLogin
            ? `${formatLoginTime(settings.previousLogin.at)}, ${PROVIDER_LABEL[settings.previousLogin.provider] ?? settings.previousLogin.provider}`
            : '첫 로그인'}
        </dd>
      </dl>
      <div className="field">
        {/* 004 T057: 선택지·라벨은 004 visibilityOptions.ts 한 곳에서 온다 */}
        <VisibilitySelect
          label="새 글 기본 공개 범위"
          value={settings.defaultVisibility}
          save={saveDefaultVisibility}
        />
      </div>
      <div className="check">
        <input
          id="settings-last-active"
          type="checkbox"
          checked={settings.lastActiveVisible}
          aria-describedby="settings-last-active-help"
          onChange={(e) => void save({ lastActiveVisible: e.target.checked })}
        />
        <label htmlFor="settings-last-active">최근 활동을 친구에게 보이기</label>
        <p className="field-help" id="settings-last-active-help">
          친구에게 "오늘·어제·N일 전·1주 이상"으로만 보여요. 끄면 나도 친구의 최근 활동을 볼 수
          없어요.
        </p>
      </div>
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      <PasswordChangeForm available={settings.passwordChangeAvailable} />
      <p>
        <Link to="/terms">이용약관</Link> · <Link to="/privacy">개인정보 처리방침</Link>
      </p>
      {/* 015 회원 탈퇴 (001 T122 자리) */}
      <p>
        <Link to="/settings/withdraw">회원 탈퇴</Link>
      </p>
    </section>
  );
}
