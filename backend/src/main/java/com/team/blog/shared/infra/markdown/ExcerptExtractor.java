package com.team.blog.shared.infra.markdown;

import java.util.Set;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.Block;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.springframework.stereotype.Component;

/**
 * 요약 (FR-027, research A-12·B-6). 변환 후 AST에서 뽑는다.
 *
 * <ul>
 *   <li>코드 블록·이미지(이미지에서 바뀐 링크 포함)·표는 통째로 뺀다
 *   <li>제목·문단·목록(·인용 안 문단)의 글자, 인라인 코드·링크는 글자로, 직접 쓴 HTML은 글자 그대로
 *   <li>줄바꿈·연속 공백 → 공백 하나, 앞 200자 — 200자째가 단어 중간이면 그 단어 앞에서 자른다(공백 없는 긴 글자는 200자에서)
 *   <li>대상 글자가 없으면 빈 문자열
 * </ul>
 */
@Component
public class ExcerptExtractor {

    public static final int MAX_LENGTH = 200;

    public String extract(Node document, Set<Node> skip) {
        StringBuilder sb = new StringBuilder();
        Node node = document.getFirstChild();
        while (node != null) {
            boolean descend = true;
            if (node instanceof FencedCodeBlock
                    || node instanceof IndentedCodeBlock
                    || node instanceof Image
                    || node instanceof TableBlock
                    || skip.contains(node)) {
                descend = false;
            } else if (node instanceof Text t) {
                sb.append(t.getLiteral());
            } else if (node instanceof Code c) {
                sb.append(c.getLiteral());
            } else if (node instanceof HtmlInline h) {
                sb.append(h.getLiteral());
            } else if (node instanceof HtmlBlock h) {
                sb.append(' ').append(h.getLiteral()).append(' ');
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                sb.append(' ');
            } else if (node instanceof Block) {
                sb.append(' ');
            }
            node = AstWalk.next(document, node, descend);
        }
        return truncate(sb.toString().replaceAll("\\s+", " ").strip());
    }

    static String truncate(String text) {
        int length = text.codePointCount(0, text.length());
        if (length <= MAX_LENGTH) {
            return text;
        }
        int end = text.offsetByCodePoints(0, MAX_LENGTH);
        String head = text.substring(0, end);
        boolean midWord =
                !Character.isWhitespace(text.codePointBefore(end))
                        && !Character.isWhitespace(text.codePointAt(end));
        if (midWord) {
            int space = head.lastIndexOf(' ');
            if (space > 0) {
                head = head.substring(0, space);
            }
        }
        return head.strip();
    }
}
