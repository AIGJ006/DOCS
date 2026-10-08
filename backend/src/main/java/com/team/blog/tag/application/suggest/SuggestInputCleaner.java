package com.team.blog.tag.application.suggest;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * 입력 정리 (013 T010, research R5, contracts/providers.md §1). 002와 같은 commonmark 파서(GFM 표·취소선)로 본문을
 * 읽어 글자만 모은다.
 *
 * <ul>
 *   <li>제목·강조·목록·인용 기호는 버리고 안의 글자만, 링크는 글자만(주소 버림), 인라인 코드는 글자 그대로
 *   <li>이미지는 통째로(대체 글자 포함), HTML 블록·인라인 HTML 태그는 버린다(태그 사이 글자는 남는다)
 *   <li>코드 블록은 언어 이름 + 앞 5줄, 표는 칸 글자를 공백으로 잇는다
 *   <li>결과 = {@code strip(NFC(공백 묶음 → 공백 하나(제목 + "\n" + 본문)))}. 대소문자는 바꾸지 않는다
 * </ul>
 */
@Component
public class SuggestInputCleaner {

    /** 코드 블록에서 남기는 줄 수. */
    static final int CODE_LINES = 5;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Parser parser =
            Parser.builder()
                    .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
                    .build();

    public CleanedInput clean(String title, String contentMd) {
        StringBuilder out = new StringBuilder();
        out.append(title == null ? "" : title).append('\n');
        if (contentMd != null && !contentMd.isBlank()) {
            parser.parse(contentMd).accept(new Collector(out));
        }
        String text = WHITESPACE.matcher(out).replaceAll(" ");
        text = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        return CleanedInput.of(text);
    }

    private static final class Collector extends AbstractVisitor {

        private final StringBuilder out;

        Collector(StringBuilder out) {
            this.out = out;
        }

        @Override
        protected void visitChildren(Node parent) {
            super.visitChildren(parent);
            if (parent instanceof Block) {
                out.append(' ');
            }
        }

        @Override
        public void visit(Text text) {
            out.append(text.getLiteral());
        }

        @Override
        public void visit(Code code) {
            out.append(code.getLiteral());
        }

        @Override
        public void visit(Image image) {
            // 대체 글자까지 버린다
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {}

        @Override
        public void visit(HtmlInline htmlInline) {}

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            out.append(' ');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            out.append(' ');
        }

        @Override
        public void visit(FencedCodeBlock block) {
            String info = block.getInfo();
            if (info != null && !info.isBlank()) {
                out.append(info.strip().split("\\s+")[0]).append(' ');
            }
            appendLines(block.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock block) {
            appendLines(block.getLiteral());
        }

        @Override
        public void visit(CustomBlock customBlock) {
            visitChildren(customBlock);
        }

        @Override
        public void visit(CustomNode customNode) {
            visitChildren(customNode);
            out.append(' ');
        }

        private void appendLines(String literal) {
            if (literal == null) {
                return;
            }
            String[] lines = literal.split("\n", -1);
            for (int i = 0; i < Math.min(CODE_LINES, lines.length); i++) {
                out.append(lines[i]).append(' ');
            }
            out.append(' ');
        }
    }
}
