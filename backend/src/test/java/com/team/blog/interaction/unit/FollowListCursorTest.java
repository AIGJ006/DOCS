package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.application.FollowListCursor;
import com.team.blog.interaction.application.FollowListCursor.Position;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 팔로워·팔로잉 목록 커서 (010 T008, data-model §4). */
class FollowListCursorTest {

    private final CursorCodec codec = new CursorCodec();
    private final FollowListCursor cursors = new FollowListCursor(codec);

    @Test
    void 범위_값은_followers_handle과_following_handle() {
        assertThat(ListScope.followers("alice").value()).isEqualTo("followers:alice");
        assertThat(ListScope.following("alice").value()).isEqualTo("following:alice");
        assertThat(ListScope.feed().value()).isEqualTo("feed");
    }

    @Test
    void 마이크로초까지_되돌린다() {
        Instant at = Instant.parse("2026-10-08T01:02:03.123456Z");
        String cursor = cursors.encode(ListScope.followers("alice"), at, 42);

        Position position = cursors.decode(cursor, ListScope.followers("alice"));

        assertThat(position.createdAt()).isEqualTo(at);
        assertThat(position.memberId()).isEqualTo(42);
    }

    @Test
    void 나노초는_마이크로초로_자른다() {
        Instant at = Instant.parse("2026-10-08T01:02:03.123456789Z");
        String cursor = cursors.encode(ListScope.following("bob"), at, 7);
        assertThat(cursors.decode(cursor, ListScope.following("bob")).createdAt())
                .isEqualTo(Instant.parse("2026-10-08T01:02:03.123456Z"));
    }

    @Test
    void 빈_값이면_처음부터() {
        assertThat(cursors.decode(null, ListScope.followers("alice"))).isNull();
        assertThat(cursors.decode("", ListScope.followers("alice"))).isNull();
    }

    @Test
    void 다른_목록의_커서는_거부() {
        Instant at = Instant.parse("2026-10-08T00:00:00Z");
        String followers = cursors.encode(ListScope.followers("alice"), at, 1);

        assertThatThrownBy(() -> cursors.decode(followers, ListScope.following("alice")))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursors.decode(followers, ListScope.followers("bob")))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursors.decode(followers, ListScope.feed()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 키_개수나_범위가_틀리면_거부() {
        ListScope scope = ListScope.followers("alice");
        assertThatThrownBy(() -> cursors.decode(codec.encode(scope, List.of(1L), null), scope))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursors.decode(codec.encode(scope, List.of(-1L, 1L), null), scope))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursors.decode(codec.encode(scope, List.of(1L, 0L), null), scope))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursors.decode("깨진값", scope))
                .isInstanceOf(InvalidCursorException.class);
    }
}
