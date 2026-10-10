import { DARK_MODE_ENABLED } from '../../config';
import { useFollowSystemTheme } from './useTheme';

/** 화면에 아무것도 그리지 않고 "시스템" 선택일 때 기기 설정 변경을 따라간다 (016 FR-006). 끈 빌드에서는 구독하지 않는다. */
export default function ThemeSystemFollower() {
  return DARK_MODE_ENABLED ? <Follower /> : null;
}

function Follower() {
  useFollowSystemTheme();
  return null;
}
