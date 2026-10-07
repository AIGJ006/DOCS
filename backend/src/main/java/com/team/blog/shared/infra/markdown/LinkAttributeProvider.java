package com.team.blog.shared.infra.markdown;

import java.util.Locale;
import java.util.Map;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.renderer.html.AttributeProvider;

/**
 * 링크·이미지 속성 (FR-043, 12 §5).
 *
 * <ul>
 *   <li>{@code http:}/{@code https:} 절대 주소와 프로토콜 상대 주소({@code //…}) → {@code target="_blank"} +
 *       {@code rel="noopener noreferrer nofollow ugc"} (tabnabbing·스팸 링크 방지)
 *   <li>{@code /}로 시작하는 우리 사이트 주소·{@code mailto:} → 추가 속성 없음(같은 탭)
 *   <li>{@code img} → {@code loading="lazy"}, {@code decoding="async"}
 * </ul>
 */
public class LinkAttributeProvider implements AttributeProvider {

    static final String EXTERNAL_REL = "noopener noreferrer nofollow ugc";

    @Override
    public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
        if (node instanceof Link && "a".equals(tagName)) {
            if (isExternal(attributes.get("href"))) {
                attributes.put("target", "_blank");
                attributes.put("rel", EXTERNAL_REL);
            }
        } else if (node instanceof Image && "img".equals(tagName)) {
            attributes.put("loading", "lazy");
            attributes.put("decoding", "async");
        }
    }

    static boolean isExternal(String href) {
        if (href == null) {
            return false;
        }
        String h = href.strip().toLowerCase(Locale.ROOT);
        return h.startsWith("http://") || h.startsWith("https://") || h.startsWith("//");
    }
}
