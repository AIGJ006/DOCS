package com.team.blog.post.config;

import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import com.team.blog.post.application.port.PostTagNamesQuery;
import com.team.blog.tag.application.TagService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 글 상세 부가 정보 포트의 기본 구현 (005 T033, research R-30). 소유 기능(008·009·010)이 같은 타입의 Bean을 등록하면 여기 Bean은
 * 만들어지지 않는다({@link ConditionalOnMissingBean}).
 *
 * <ul>
 *   <li>태그: 002가 둔 {@link TagService#tagNamesOf(long)}({@code post_tag.position} 순서)를 그대로 쓴다 —
 *       <b>008에서 교체</b>(정규화·금칙어 규칙이 생기면 그 Service로).
 *   <li>좋아요 여부: 항상 {@code false} — <b>009에서 교체</b>.
 *   <li>팔로우 여부: 항상 {@code false} — <b>010에서 교체</b>.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class PostReadingPorts {

    @Bean
    @ConditionalOnMissingBean
    PostTagNamesQuery postTagNamesQuery(TagService tagService) {
        return tagService::tagNamesOf;
    }

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
