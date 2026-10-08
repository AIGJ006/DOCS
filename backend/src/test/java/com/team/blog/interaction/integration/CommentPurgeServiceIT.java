package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.CommentPurgeService;
import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴 30일 정리 (007 T046, US4 #5, FR-039, contracts/events.md §2-2). */
class CommentPurgeServiceIT extends IntegrationTestBase {

    @Autowired CommentPurgeService purge;
    @Autowired TransactionTemplate tx;

    private boolean exists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM comment WHERE id = ?", Long.class, id) > 0;
    }

    @Test
    void 남의_답글_있는_내_최상위만_자리로_나머지는_지운다() throws Exception {
        long author = members().member().create();
        long me = members().member().create();
        long other = members().member().nickname("남은사람").create();
        PostFixtures posts = new PostFixtures(jdbc);
        long p1 = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long p2 = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures c = new CommentFixtures(jdbc);

        long keep = c.on(p1, me).content("남의 답글 있는 내 최상위").create();
        long othersReply = c.on(p1, other).parent(keep).create();
        long myReplyThere = c.on(p1, me).parent(keep).create();
        long othersRoot = c.on(p1, other).create();
        long replyToMe = c.on(p1, other).parent(othersRoot).replyTo(me).create();
        long othersRoot2 = c.on(p2, other).create();
        long myReply = c.on(p2, me).parent(othersRoot2).create();
        long lonely = c.on(p2, me).create();
        long onlyMine = c.on(p2, me).create();
        long onlyMineReply = c.on(p2, me).parent(onlyMine).create();
        long hiddenMine = c.on(p2, me).hidden().create();
        long oldPlaceholder = c.on(p2, other).deleted().create();
        long myReplyUnderOld = c.on(p2, me).parent(oldPlaceholder).create();
        long myOldPlaceholder = c.on(p2, me).deleted().create();
        long othersReplyUnderMine = c.on(p2, other).parent(myOldPlaceholder).create();

        assertThat_purgeNeedsTransaction(me);
        tx.executeWithoutResult(s -> purge.purgeByAuthor(me));

        assertThat(exists(keep)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content FROM comment WHERE id = ?", String.class, keep))
                .isEmpty();
        assertThat(exists(othersReply)).isTrue();
        assertThat(exists(myReplyThere)).isFalse();
        assertThat(exists(othersRoot)).isTrue();
        assertThat(exists(replyToMe)).isTrue();
        assertThat(exists(myReply)).isFalse();
        assertThat(exists(othersRoot2)).isTrue();
        assertThat(exists(lonely)).isFalse();
        assertThat(exists(onlyMine)).isFalse();
        assertThat(exists(onlyMineReply)).isFalse();
        assertThat(exists(hiddenMine)).isFalse();
        assertThat(exists(oldPlaceholder)).as("내 답글만 있던 남의 자리").isFalse();
        assertThat(exists(myReplyUnderOld)).isFalse();
        assertThat(exists(myOldPlaceholder)).as("남의 답글 있는 내 예전 자리").isTrue();
        assertThat(exists(othersReplyUnderMine)).isTrue();
        for (long postId : new long[] {p1, p2}) {
            assertThat(c.commentCount(postId)).isEqualTo((int) c.normalCount(postId));
        }

        // 익명 처리 뒤 남의 답글의 대상은 "탈퇴한 사용자에게"
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now(), deleted_at = now(),"
                        + " nickname = NULL WHERE id = ?",
                me);
        MvcResult page = new CommentApi(mockMvc).list(null, p1);
        List<Boolean> withdrawn =
                read(page, "$.items[*].replies[?(@.id == " + replyToMe + ")].replyTo.withdrawn");
        assertThat(withdrawn).containsExactly(true);
        List<String> states = read(page, "$.items[?(@.id == " + keep + ")].state");
        assertThat(states).containsExactly("WITHDRAWN_AUTHOR");
    }

    private void assertThat_purgeNeedsTransaction(long me) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> purge.purgeByAuthor(me))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
