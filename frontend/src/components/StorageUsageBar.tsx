import { useEffect, useState } from 'react';
import { getStorageUsage, type StorageUsage } from '../api/images';
import { formatBytes, usagePercent } from '../features/image-upload/storageHint';
import './storageUsage.css';

/**
 * 사진 저장 공간 막대 (003 T061, US4, FR-014·017). 설정 화면의 "사진 저장 공간" 항목에 쓴다. `usage`를 주면 그 값을 쓰고, 없으면
 * `GET /api/me/storage`를 한 번 부른다.
 */
interface Props {
  usage?: StorageUsage | null;
}

export default function StorageUsageBar({ usage: given }: Props) {
  const [loaded, setLoaded] = useState<StorageUsage | null>(null);
  const [failed, setFailed] = useState(false);
  const usage = given ?? loaded;

  useEffect(() => {
    if (given) return;
    let cancelled = false;
    getStorageUsage()
      .then((value) => {
        if (!cancelled) setLoaded(value);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [given]);

  if (!usage) {
    return (
      <section className="storage-usage" aria-label="사진 저장 공간">
        {failed ? <p className="storage-usage-error">사진 저장 공간을 불러오지 못했어요</p> : null}
      </section>
    );
  }
  const percent = usagePercent(usage);
  return (
    <section className="storage-usage">
      <p className="storage-usage-text">
        사진 저장 공간 {formatBytes(usage.usedBytes)} / {formatBytes(usage.quotaBytes)}
      </p>
      <div
        className="storage-usage-bar"
        role="progressbar"
        aria-label="사진 저장 공간"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <div className="storage-usage-fill" style={{ width: `${percent}%` }} />
      </div>
      <p className="storage-usage-note">지운 사진의 공간은 7일 뒤 돌아와요</p>
    </section>
  );
}
