package com.team.blog.shared.infra.markdown;

import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.application.markdown.ImageReferenceResolver;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.application.markdown.OwnedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.springframework.stereotype.Component;

/**
 * AST 변환 (12 §2 ②, FR-042·FR-044·FR-046). 원문 {@code content_md}는 바꾸지 않고 파싱한 트리만 바꾼다.
 *
 * <ol>
 *   <li>목록·인용 중첩 깊이 검사: {@code blog.markdown.max-nesting}(20) 초과 → {@link
 *       ContentTooComplexException}
 *   <li>제목 한 단계 낮춤: 1→2 … 5→6, 6은 6 (본문에 {@code h1} 없음)
 *   <li>이미지: 본문의 우리 저장소 키를 모아 {@link ImageReferenceResolver#findOwned}를 한 번 부르고, 작성자 사진이면 주소를 지금 공개
 *       주소로 바꾸고 키를 본문 순서로 모은다. 외부 이미지·남이 올린 사진은 "[이미지] 대체글"(없으면 주소) 링크로 바꾼다.
 *   <li>작성자 GIF(003 US6, research R11): 썸네일이 있으면 {@code Link(원본, title="움직이는 이미지 재생")} 안의 {@code
 *       Image(썸네일)}로 바꾼다 — 처음에는 정지 장면을 보이고 화면의 {@code gifPlayer}가 눌렀을 때 원본으로 바꾼다. 썸네일 주소는 {@link
 *       OwnedImage#thumbStorageKey()} 그대로(확장자를 가정하지 않음). 썸네일이 없는 옛 GIF와 이미 링크 안에 있는 GIF(링크 중첩 금지)는
 *       바꾸지 않는다.
 * </ol>
 *
 * 제목 {@code id}는 렌더링 때 {@link HeadingAnchorProvider}가 붙인다(commonmark 노드에 속성을 둘 곳이 없어서). 트리는 재귀 없이
 * 걷는다.
 */
@Component
public class AstTransformer {

    static final String IMAGE_LINK_PREFIX = "[이미지] ";

    /** 작성자 GIF를 감싼 링크의 title (003 US6, 화면 gifPlayer가 비어 있는 대체글 대신 쓴다). */
    static final String GIF_PLAY_TITLE = "움직이는 이미지 재생";

    private final ImageReferenceResolver images;
    private final int maxNesting;

    public AstTransformer(ImageReferenceResolver images, MarkdownProperties properties) {
        this.images = images;
        this.maxNesting = properties.maxNesting();
    }

    /**
     * @param document 파싱한 문서 (이 메서드가 바꾼다)
     * @param ownerMemberId 사진 판별 기준 회원
     */
    public Result transform(Node document, long ownerMemberId) {
        List<Image> imageNodes = new ArrayList<>();
        checkNestingAndCollect(document, imageNodes);
        return replaceImages(imageNodes, ownerMemberId);
    }

    private void checkNestingAndCollect(Node document, List<Image> imageNodes) {
        Map<Node, Integer> depthOf = new IdentityHashMap<>();
        depthOf.put(document, 0);
        Node node = document.getFirstChild();
        while (node != null) {
            int depth = depthOf.getOrDefault(node.getParent(), 0);
            if (node instanceof BlockQuote || node instanceof ListBlock) {
                depth++;
                if (depth > maxNesting) {
                    throw new ContentTooComplexException();
                }
            }
            if (node.getFirstChild() != null) {
                depthOf.put(node, depth);
            }
            if (node instanceof Heading heading) {
                heading.setLevel(Math.min(heading.getLevel() + 1, 6));
            } else if (node instanceof Image image) {
                imageNodes.add(image);
            }
            node = AstWalk.next(document, node, true);
        }
    }

    private Result replaceImages(List<Image> imageNodes, long ownerMemberId) {
        if (imageNodes.isEmpty()) {
            return Result.EMPTY;
        }
        Map<Image, String> keyOf = new IdentityHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (Image image : imageNodes) {
            Optional<String> key = images.keyOf(image.getDestination());
            key.ifPresent(
                    k -> {
                        keyOf.put(image, k);
                        keys.add(k);
                    });
        }
        Map<String, OwnedImage> owned =
                keys.isEmpty() ? Map.of() : images.findOwned(keys, ownerMemberId);

        Set<String> ownedKeys = new LinkedHashSet<>();
        Set<Node> imageLinks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Image image : imageNodes) {
            String key = keyOf.get(image);
            if (key != null && owned.containsKey(key)) {
                image.setDestination(images.publicUrlOf(key));
                ownedKeys.add(key);
                wrapPlayableGif(image, owned.get(key));
            } else {
                imageLinks.add(replaceWithLink(image));
            }
        }
        Map<String, OwnedImage> ownedInOrder = new LinkedHashMap<>();
        for (String key : ownedKeys) {
            ownedInOrder.put(key, owned.get(key));
        }
        return new Result(List.copyOf(ownedKeys), ownedInOrder, imageLinks);
    }

    /** 작성자 GIF → 정지 장면(썸네일)을 원본 링크로 감싼다. */
    private void wrapPlayableGif(Image image, OwnedImage owned) {
        if (owned == null
                || owned.thumbStorageKey() == null
                || !owned.storageKey().toLowerCase(Locale.ROOT).endsWith(".gif")
                || insideLink(image)) {
            return;
        }
        Link link = new Link(image.getDestination(), GIF_PLAY_TITLE);
        image.insertBefore(link);
        image.unlink();
        image.setDestination(images.publicUrlOf(owned.thumbStorageKey()));
        link.appendChild(image);
    }

    private static boolean insideLink(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof Link) {
                return true;
            }
        }
        return false;
    }

    private static Link replaceWithLink(Image image) {
        String alt = HeadingAnchorProvider.textOf(image).strip();
        String destination = image.getDestination() == null ? "" : image.getDestination();
        Link link = new Link(destination, image.getTitle());
        link.appendChild(new Text(IMAGE_LINK_PREFIX + (alt.isEmpty() ? destination : alt)));
        image.insertAfter(link);
        image.unlink();
        return link;
    }

    /**
     * 변환 결과.
     *
     * @param ownedKeys 작성자 사진 키 (본문 순서, 중복 없음)
     * @param owned 작성자 사진 (키 → 썸네일 키), {@code ownedKeys} 순서
     * @param imageLinks 이미지에서 바뀐 링크 노드 (요약에서 뺀다)
     */
    public record Result(
            List<String> ownedKeys, Map<String, OwnedImage> owned, Set<Node> imageLinks) {

        static final Result EMPTY = new Result(List.of(), Map.of(), Set.of());

        /** 첫 작성자 사진 (썸네일 후보). */
        public Optional<OwnedImage> firstOwned() {
            return ownedKeys.isEmpty()
                    ? Optional.empty()
                    : Optional.ofNullable(owned.get(ownedKeys.get(0)));
        }
    }
}
