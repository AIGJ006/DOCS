import { Link } from 'react-router-dom';
import type { FollowListItem as Item } from '../../api/types/follow';
import DefaultAvatar from '../../components/DefaultAvatar';
import FollowButton from './FollowButton';
import { firstLine } from './followMessages';
import './follow.css';

/**
 * 팔로워·팔로잉 목록 한 줄 (010 T038, FR-015, 24 §2-2): 사진(없으면 기본 아이콘) · `닉네임 @주소`(블로그 링크) · 소개 첫 줄 ·
 * [팔로우]. 나 자신이면 버튼이 없다. 닉네임·소개는 텍스트 노드로만 넣는다(원칙 IV).
 */
export default function FollowListItem({ item }: { item: Item }) {
  const bio = firstLine(item.bio);
  return (
    <li className="follow-list-item" data-testid="follow-list-item">
      {item.profileImageUrl ? (
        <img
          src={item.profileImageUrl}
          alt=""
          width={48}
          height={48}
          loading="lazy"
          className="follow-list-item__avatar"
        />
      ) : (
        <DefaultAvatar nickname={item.nickname} handle={item.handle} size={48} />
      )}
      <div className="follow-list-item__body">
        <p className="follow-list-item__name">
          <Link to={`/@${item.handle}`}>
            {item.nickname} <span className="follow-list-item__handle">@{item.handle}</span>
          </Link>
        </p>
        {bio ? (
          <p className="follow-list-item__bio" data-testid="follow-list-bio">
            {bio}
          </p>
        ) : null}
      </div>
      <FollowButton handle={item.handle} initialFollowing={item.followedByMe} isMe={item.isMe} />
    </li>
  );
}
