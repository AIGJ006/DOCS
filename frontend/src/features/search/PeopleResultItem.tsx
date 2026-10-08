import { Link } from 'react-router-dom';
import type { PersonItem } from '../../api/types/discovery';
import DefaultAvatar from '../../components/DefaultAvatar';
import './search.css';

/**
 * 사람 검색 결과 한 줄 (012 T037, 33 §5): 프로필 사진 · 닉네임 · `@주소` · 소개 첫 줄. 누르면 그 블로그.
 * 값은 텍스트 노드로만 넣는다.
 */
export interface PeopleResultItemProps {
  person: PersonItem;
}

export default function PeopleResultItem({ person }: PeopleResultItemProps) {
  return (
    <li className="people-item" data-testid="person-item">
      <Link to={`/@${person.handle}`}>
        {person.profileImageUrl ? (
          <img
            src={person.profileImageUrl}
            alt=""
            width={40}
            height={40}
            style={{ borderRadius: '50%', objectFit: 'cover', flex: '0 0 auto' }}
          />
        ) : (
          <DefaultAvatar nickname={person.nickname} handle={person.handle} size={40} />
        )}
        <span className="people-item-text">
          <span className="people-item-name">{person.nickname}</span>
          <span className="people-item-handle">@{person.handle}</span>
          {person.bioFirstLine ? (
            <span className="people-item-bio">{person.bioFirstLine}</span>
          ) : null}
        </span>
      </Link>
    </li>
  );
}
