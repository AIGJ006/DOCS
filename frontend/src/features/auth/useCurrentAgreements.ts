import { useEffect, useState } from 'react';
import { getCurrentAgreements, type CurrentAgreements } from '../../api/auth';

/** `GET /api/agreements/current` (비로그인 허용). 실패하면 `failed`. */
export function useCurrentAgreements() {
  const [agreements, setAgreements] = useState<CurrentAgreements | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    getCurrentAgreements()
      .then((value) => active && setAgreements(value))
      .catch(() => active && setFailed(true));
    return () => {
      active = false;
    };
  }, []);
  return { agreements, failed };
}
