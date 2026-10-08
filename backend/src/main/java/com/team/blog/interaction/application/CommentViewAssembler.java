package com.team.blog.interaction.application;

import com.team.blog.account.application.MemberDisplay;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.interaction.domain.CommentState;
import com.team.blog.interaction.infra.CommentRow;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.shared.security.Viewer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 행 → {@link CommentView} (007 T020, research R8·R10). 작성자·대상 회원 표시는 001 {@link
 * MemberQueryService#findDisplays}로, 프로필 사진은 {@link ProfileImageQuery#currentKeysOf}로 한 번씩만 읽는다 —
 * 댓글 수와 상관없이 SQL 2번(사진을 보일 작성자가 없으면 1번).
 */
@Component
public class CommentViewAssembler {

    private final MemberQueryService members;
    private final ProfileImageQuery profileImages;
    private final ImageUrlResolver imageUrls;

    public CommentViewAssembler(
            MemberQueryService members,
            ProfileImageQuery profileImages,
            ImageUrlResolver imageUrls) {
        this.members = members;
        this.profileImages = profileImages;
        this.imageUrls = imageUrls;
    }

    /** 조립 준비: 회원 표시·사진을 읽어 둔 상태. */
    public final class Batch {
        private final long postAuthorId;
        private final Viewer viewer;
        private final Map<Long, MemberDisplay> displays;
        private final Map<Long, ProfileImageKeys> photos;

        private Batch(
                long postAuthorId,
                Viewer viewer,
                Map<Long, MemberDisplay> displays,
                Map<Long, ProfileImageKeys> photos) {
            this.postAuthorId = postAuthorId;
            this.viewer = viewer;
            this.displays = displays;
            this.photos = photos;
        }

        public CommentView view(CommentRow row) {
            boolean mine = viewer.isAuthorOf(row.authorId());
            CommentState state = stateOf(row, displays);
            boolean reveals = state.reveals(mine);
            CommentView.Author author = null;
            CommentView.ReplyTo replyTo = null;
            if (reveals) {
                MemberDisplay display = displays.get(row.authorId());
                ProfileImageKeys photo = photos.get(row.authorId());
                author =
                        new CommentView.Author(
                                display.handle(),
                                display.nickname(),
                                photo == null ? null : imageUrls.publicUrl(photo.display()),
                                row.authorId() == postAuthorId);
                if (row.replyToMemberId() != null) {
                    MemberDisplay target = displays.get(row.replyToMemberId());
                    replyTo =
                            target == null || target.withdrawn()
                                    ? CommentView.ReplyTo.withdrawnMember()
                                    : CommentView.ReplyTo.of(target.handle(), target.nickname());
                }
            }
            return new CommentView(
                    row.id(),
                    state,
                    reveals ? row.content() : null,
                    row.createdAt(),
                    CommentState.edited(row.createdAt(), row.updatedAt()),
                    author,
                    replyTo,
                    mine,
                    row.parentId());
        }
    }

    /** 이 행들을 그릴 준비를 한다 (회원 표시 1번 + 사진 1번). */
    public Batch prepare(long postAuthorId, Viewer viewer, Collection<CommentRow> rows) {
        Set<Long> memberIds = new LinkedHashSet<>();
        for (CommentRow row : rows) {
            memberIds.add(row.authorId());
            if (row.replyToMemberId() != null) {
                memberIds.add(row.replyToMemberId());
            }
        }
        Map<Long, MemberDisplay> displays = members.findDisplays(memberIds);
        Set<Long> withPhoto = new LinkedHashSet<>();
        for (CommentRow row : rows) {
            if (stateOf(row, displays).reveals(viewer.isAuthorOf(row.authorId()))) {
                withPhoto.add(row.authorId());
            }
        }
        Map<Long, ProfileImageKeys> photos =
                withPhoto.isEmpty() ? new HashMap<>() : profileImages.currentKeysOf(withPhoto);
        return new Batch(postAuthorId, viewer, displays, photos);
    }

    /** 작성자가 탈퇴 유예·익명 처리가 아닌 회원인가 (답글 대상·바로 가기 확인). */
    public boolean isActiveAuthor(long memberId) {
        MemberDisplay display = members.findDisplays(List.of(memberId)).get(memberId);
        return display != null && !display.withdrawn();
    }

    /** 한 행만. */
    public CommentView single(long postAuthorId, Viewer viewer, CommentRow row) {
        return prepare(postAuthorId, viewer, List.of(row)).view(row);
    }

    static CommentState stateOf(CommentRow row, Map<Long, MemberDisplay> displays) {
        MemberDisplay author = displays.get(row.authorId());
        boolean withdrawn = author == null || author.withdrawn();
        return CommentState.of(withdrawn, row.deletedAt(), row.hiddenAt());
    }
}
