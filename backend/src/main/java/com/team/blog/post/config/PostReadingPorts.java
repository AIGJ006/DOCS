package com.team.blog.post.config;

import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 글 상세 부가 정보 포트의 기본 구현 (005 T033, research R-30). 소유 기능(008·009·010)이 같은 타입의 Bean을 등록하면 여기 Bean은
 * 만들어지지 않는다({@link ConditionalOnMissingBean}).
 *
 * <ul>
 *   <li>태그: 008 {@code TagNamesQueryAdapter}가 맡는다(여기 기본 구현은 지웠다 — 일반 설정 클래스의 {@link
 *       ConditionalOnMissingBean}은 컴포넌트 스캔 순서에 따라 판정이 흔들릴 수 있다).
 *   <li>좋아요 여부: 항상 {@code false} — <b>009에서 교체</b>.
 *   <li>팔로우 여부: 항상 {@code false} — <b>010에서 교체</b>.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class PostReadingPorts {

    @Bean
    @ConditionalOnMissingBean
    PostLikeStatusQuery postLikeStatusQuery() {
        return (postId, memberId) -> false;
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorFollowStatusQuery authorFollowStatusQuery() {
        return (followerId, followeeId) -> false;
    }
}
