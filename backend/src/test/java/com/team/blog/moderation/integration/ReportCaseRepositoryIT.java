package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.ReportTarget;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** 사건·신고 저장 SQL (014 T012, contracts/moderation-sql.md §1·§3·§4·§5). */
class ReportCaseRepositoryIT extends IntegrationTestBase {

    @Autowired ReportCaseRepository cases;
    @Autowired ReportRepository reports;
    @Autowired TransactionTemplate tx;

    private ReportTarget postTarget(long postId, long author) {
        return new ReportTarget(ReportTargetType.POST, postId, postId, author, "제목", "내용");
    }

    @Test
    void 대기_사건이_있으면_빈_값_없으면_새_번호_HIDDEN_사건이_있어도_새_대기_사건() {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        ReportTarget target = postTarget(postId, author);

        Optional<Long> first = cases.insertPending(target, Instant.now());
        assertThat(first).isPresent();
        assertThat(cases.insertPending(target, Instant.now())).isEmpty();
        Optional<Long> locked = tx.execute(s -> cases.lockPending(ReportTargetType.POST, postId));
        assertThat(locked).isEqualTo(first);

        cases.close(first.get(), CaseStatus.HIDDEN, admin, Instant.now());
        long direct = cases.insertHidden(target, admin, Instant.now());
        Optional<Long> second = cases.insertPending(target, Instant.now());
        assertThat(second).isPresent().isNotEqualTo(first);
        assertThat(cases.statusesOf(ReportTargetType.POST, postId))
                .containsExactly(CaseStatus.HIDDEN, CaseStatus.HIDDEN, CaseStatus.PENDING);
        assertThat(direct).isNotEqualTo(first.get());
    }

    @Test
    void 같은_사건에_같은_신고자는_한_번만() {
        long author = members().member().create();
        long reporter = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = cases.insertPending(postTarget(postId, author), Instant.now()).orElseThrow();

        assertThat(reports.insertIfAbsent(caseId, reporter, ReportReason.SPAM, null, Instant.now()))
                .isTrue();
        assertThat(
                        reports.insertIfAbsent(
                                caseId, reporter, ReportReason.ABUSE, null, Instant.now()))
                .isFalse();
        assertThat(reports.findByCase(caseId)).hasSize(1);
    }

    @Test
    void ck_report_detail은_기타의_빈_설명을_막고_NULL은_통과() {
        ReportFixtures f = new ReportFixtures(jdbc);
        long author = members().member().create();
        long reporter = members().member().create();
        long other = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = f.pendingPost(postId, author);

        assertThatThrownBy(() -> f.report(caseId, reporter, "OTHER", "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
        long id = f.report(caseId, other, "OTHER", "설명");
        jdbc.update("UPDATE report SET detail = NULL WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT detail FROM report WHERE id = ?", String.class, id))
                .isNull();
    }

    @Test
    void 대상_없음으로_닫는_네_조건() {
        ReportFixtures f = new ReportFixtures(jdbc);
        long author = members().member().create();
        long other = members().member().create();
        long admin = members().member().role("ADMIN").create();
        PostFixtures posts = new PostFixtures(jdbc);
        long post1 = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long post2 = posts.create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long c1 = comments.on(post2, other).create();
        long c2 = comments.on(post2, other).create();

        long forPost = f.pendingPost(post1, author);
        long handled = f.handledPost(post1, author, "REJECTED", admin, Instant.now());
        long forC1 = f.pendingComment(c1, other);
        long forC2 = f.pendingComment(c2, other);
        long byAuthor = f.pendingPost(post2, other);

        Instant now = Instant.now();
        assertThat(cases.closeNoTargetForPost(post1, now)).isEqualTo(1);
        assertThat(cases.closeNoTargetForComments(List.of(c1), now)).isEqualTo(1);
        assertThat(cases.closeNoTargetForDeletedComment(c2, now)).isEqualTo(1);
        assertThat(cases.closeNoTargetForAuthor(other, now)).isEqualTo(1);

        assertThat(f.status(forPost)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(f.status(handled)).isEqualTo("REJECTED");
        assertThat(f.status(forC1)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(f.status(forC2)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(f.status(byAuthor)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT handled_by IS NULL AND handled_at IS NOT NULL FROM"
                                        + " report_case WHERE id = ?",
                                Boolean.class,
                                forPost))
                .isTrue();

        long orphanPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long orphan = f.pendingPost(orphanPost, author);
        jdbc.update("DELETE FROM post WHERE id = ?", orphanPost);
        assertThat(cases.closeOrphans(now)).isEqualTo(1);
        assertThat(f.status(orphan)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT snapshot_content FROM report_case WHERE id = ?",
                                String.class,
                                orphan))
                .isEqualTo("스냅샷 내용");
    }
}
