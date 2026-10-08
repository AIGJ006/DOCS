package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ImageUrls;
import com.team.blog.media.infra.ImageReferenceResolverAdapter;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.shared.infra.markdown.AstTransformer;
import com.team.blog.shared.infra.markdown.CommonmarkFactory;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import com.team.blog.shared.infra.markdown.ExcerptExtractor;
import com.team.blog.shared.infra.markdown.RenderExecutorConfig;
import com.team.blog.shared.infra.markdown.SanitizerPolicy;
import com.team.blog.shared.web.SecurityHeadersFilter;
import com.team.blog.support.IntegrationTestBase;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 공개 주소 변경 (003 T083 US8, research R17, FR-031, 23 I-5). 공개 주소를 새 주소로, 옛 주소를 {@code
 * legacy-base-urls}에 둔 설정으로 실제 부품(ImageUrls·media 어댑터·렌더러·정화·CSP 필터)을 직접 조립한다 — 설정만 다른 새 테스트 컨텍스트를
 * 만들지 않는다(tasks의 {@code @TestPropertySource} 대신). 사진 행은 실제 DB에 둔다.
 */
class PublicBaseUrlChangeIT extends IntegrationTestBase {

    private static final String OLD = "https://old-storage.example.com/blog";
    private static final String NEW = "https://img.devlog.example";

    @Autowired CoreProperties core;
    @Autowired ImageRepository images;

    @Autowired
    @Qualifier(RenderExecutorConfig.RENDER_EXECUTOR)
    ExecutorService executor;

    private CoreProperties changed;
    private DefaultContentRenderer renderer;
    private long author;

    @BeforeEach
    void setUp() {
        changed =
                new CoreProperties(
                        core.timeZone(),
                        new CoreProperties.Image(NEW, List.of(OLD)),
                        core.scheduling(),
                        core.async());
        ImageReferenceResolverAdapter resolver =
                new ImageReferenceResolverAdapter(
                        new ImageUrls(changed), images, ImageUrlResolver.of(NEW));
        MarkdownProperties markdown = MarkdownProperties.defaults();
        renderer =
                new DefaultContentRenderer(
                        new CommonmarkFactory(),
                        new AstTransformer(resolver, markdown),
                        new SanitizerPolicy(changed),
                        new ExcerptExtractor(),
                        resolver,
                        executor,
                        markdown);
        author = members().member().create();
    }

    @Test
    void US8_1_옛주소로_쓴_내_사진은_새주소_img가_되고_원문은_그대로다() {
        String key = ImageFixtures.newKey("webp");
        new ImageFixtures(jdbc).image(author).key(key).attached().create();
        String md = "![책상](" + OLD + "/" + key + ")";

        RenderedContent rendered = renderer.render(md, new ImageContext(author));

        assertThat(rendered.html()).contains("<img src=\"" + NEW + "/" + key + "\" alt=\"책상\"");
        assertThat(rendered.html()).doesNotContain(OLD).doesNotContain("[이미지]");
        assertThat(rendered.thumbnailUrl())
                .isEqualTo(NEW + "/" + ImageFixtures.thumbOf(key, "webp"));
        assertThat(rendered.ownedImageKeys()).containsExactly(key);
        assertThat(md).isEqualTo("![책상](" + OLD + "/" + key + ")");
    }

    @Test
    void 새주소로_쓴_사진도_그대로_img다() {
        String key = ImageFixtures.newKey("webp");
        new ImageFixtures(jdbc).image(author).key(key).attached().create();

        String html =
                renderer.render("![](" + NEW + "/" + key + ")", new ImageContext(author)).html();

        assertThat(html).contains("<img src=\"" + NEW + "/" + key + "\"");
    }

    @Test
    void US8_2_목록에_없는_주소는_링크가_된다() {
        String key = ImageFixtures.newKey("webp");
        new ImageFixtures(jdbc).image(author).key(key).attached().create();

        String html =
                renderer.render(
                                "![x](https://unknown.example.com/blog/" + key + ")",
                                new ImageContext(author))
                        .html();

        assertThat(html).doesNotContain("<img").contains("[이미지] x");
    }

    @Test
    void 옛주소의_GIF도_새주소_정지_장면과_원본_링크가_된다() {
        String key = ImageFixtures.newKey("gif");
        String thumb = ImageFixtures.thumbOf(key, "jpg");
        new ImageFixtures(jdbc)
                .image(author)
                .key(key)
                .thumbKey(thumb)
                .contentType("image/gif")
                .attached()
                .create();

        String html =
                renderer.render("![](" + OLD + "/" + key + ")", new ImageContext(author)).html();

        assertThat(html)
                .contains("href=\"" + NEW + "/" + key + "\"")
                .contains("<img src=\"" + NEW + "/" + thumb + "\"")
                .doesNotContain(OLD);
    }

    @Test
    void CSP_img_src에_새_출처가_들어간다() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(changed, List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

        String csp = response.getHeader(SecurityHeadersFilter.CSP);
        assertThat(csp).contains("img-src 'self' https://img.devlog.example");
    }
}
