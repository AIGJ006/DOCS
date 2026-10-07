package com.team.blog.shared.infra.markdown;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * commonmark-java 파서·렌더러 (12 §1 S-1~S-3, research A-9). 둘 다 스레드 안전하므로 한 번만 만든다.
 *
 * <ul>
 *   <li>확장: GFM 표·취소선·체크리스트·주소 자동 링크만. 각주·수식·Mermaid·임베드는 넣지 않는다(FR-003, S-7).
 *   <li>{@code escapeHtml(true)}: 본문에 직접 쓴 HTML은 글자로 보인다(S-2).
 *   <li>{@code sanitizeUrls(true)}: 위험한 주소를 1차로 지운다(최종 판정은 {@link SanitizerPolicy}).
 *   <li>속성: 외부 링크 새 탭·{@code rel}, 이미지 지연 로딩({@link LinkAttributeProvider}), 제목 {@code id}({@link
 *       HeadingAnchorProvider} — 렌더링 한 번마다 새로 만들어 같은 이름 번호를 센다).
 * </ul>
 */
@Component
public class CommonmarkFactory {

    private static final List<Extension> EXTENSIONS =
            List.of(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    TaskListItemsExtension.create(),
                    AutolinkExtension.create());

    private final Parser parser;
    private final HtmlRenderer renderer;

    public CommonmarkFactory() {
        this.parser = Parser.builder().extensions(EXTENSIONS).build();
        this.renderer =
                HtmlRenderer.builder()
                        .extensions(EXTENSIONS)
                        .escapeHtml(true)
                        .sanitizeUrls(true)
                        .attributeProviderFactory(context -> new LinkAttributeProvider())
                        .attributeProviderFactory(context -> new HeadingAnchorProvider())
                        .build();
    }

    public Parser parser() {
        return parser;
    }

    public HtmlRenderer renderer() {
        return renderer;
    }
}
