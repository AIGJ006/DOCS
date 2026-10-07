package com.team.blog.shared.web.cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 풀린 커서.
 *
 * @param v 형식 버전 (현재 1)
 * @param l 목록 구분 ({@link ListScope#value()})
 * @param k 정렬 키 (정수는 {@link Long}, 그 밖에 문자열·실수·불리언·null)
 * @param extra 기능이 더한 추가 필드 (예: 006 {@code tab}·{@code vis})
 */
public record CursorPayload(int v, String l, List<Object> k, Map<String, Object> extra) {

    public CursorPayload {
        k = Collections.unmodifiableList(new ArrayList<>(k));
        extra = extra == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    /** {@code k[index]}를 정수로. 정수가 아니면 {@link InvalidCursorException}. */
    public long longAt(int index) {
        if (index < k.size() && k.get(index) instanceof Long value) {
            return value;
        }
        throw new InvalidCursorException("cursor key " + index + " is not an integer");
    }

    /** {@code k[index]}를 문자열로. 문자열이 아니면 {@link InvalidCursorException}. */
    public String stringAt(int index) {
        if (index < k.size() && k.get(index) instanceof String value) {
            return value;
        }
        throw new InvalidCursorException("cursor key " + index + " is not a string");
    }
}
