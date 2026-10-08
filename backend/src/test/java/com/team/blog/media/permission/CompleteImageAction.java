package com.team.blog.media.permission;

import com.team.blog.media.support.ImageApi;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 003 권한 매트릭스 실행기 {@code image.complete}(본인 사진)·{@code image.complete.others}(다른 회원 사진)
 * (T023·T031).
 *
 * <p>하네스는 행위자 번호를 넘기지 않는다. 행마다 DB가 비워지고 행위자가 마지막으로 만들어지므로({@code
 * AbstractPermissionMatrixIT#arrange}) 가장 큰 회원 번호가 행위자다(비회원 행은 작성자). 그 회원의 완료 전 행을 만들고 저장소에 두 파일을
 * 넣은 뒤 complete를 부른다. "다른 회원 사진"은 새 회원의 사진이다.
 */
@Profile("test")
@Component
public class CompleteImageAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public CompleteImageAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 다른 회원의 사진을 대상으로 하는가. */
    protected boolean others() {
        return false;
    }

    @Override
    public String name() {
        return others() ? "image.complete.others" : "image.complete";
    }

    @Override
    public String owner() {
        return "003";
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        long uploader = others() ? newMember() : latestMember();
        byte[] original = fixture("photo-1920.webp");
        byte[] thumb = fixture("thumb-640.webp");
        String key = ImageFixtures.newKey("webp");
        String thumbKey = ImageFixtures.thumbOf(key, "webp");
        long imageId =
                new ImageFixtures(jdbc)
                        .image(uploader)
                        .key(key)
                        .thumbKey(thumbKey)
                        .size(original.length, thumb.length)
                        .incomplete()
                        .create();
        MinioContainerSupport.putDirect(key, "image/webp", original);
        MinioContainerSupport.putDirect(thumbKey, "image/webp", thumb);
        return ActionResult.of(new ImageApi(mockMvc).complete(session, imageId));
    }

    private long latestMember() {
        return jdbc.queryForObject("SELECT max(id) FROM member", Long.class);
    }

    private long newMember() {
        return new com.team.blog.support.MemberFixtures(jdbc).member().create();
    }

    private static byte[] fixture(String name) {
        try (InputStream in = CompleteImageAction.class.getResourceAsStream("/images/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code image.complete.others} 실행기. */
    @Profile("test")
    @Component
    public static class Others extends CompleteImageAction {
        public Others(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        protected boolean others() {
            return true;
        }
    }
}
