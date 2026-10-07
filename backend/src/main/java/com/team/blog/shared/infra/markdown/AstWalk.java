package com.team.blog.shared.infra.markdown;

import org.commonmark.node.Node;

/** 재귀 없이 AST를 문서 순서로 걷는다 (깊은 중첩에서도 스택이 넘치지 않게). */
final class AstWalk {

    private AstWalk() {}

    /**
     * {@code root} 아래에서 {@code current} 다음 노드. {@code descend}가 거짓이면 {@code current}의 자식은 건너뛴다. 끝이면
     * {@code null}.
     */
    static Node next(Node root, Node current, boolean descend) {
        if (descend && current.getFirstChild() != null) {
            return current.getFirstChild();
        }
        Node node = current;
        while (node != null && node != root) {
            if (node.getNext() != null) {
                return node.getNext();
            }
            node = node.getParent();
        }
        return null;
    }
}
