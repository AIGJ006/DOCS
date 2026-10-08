package com.team.blog.account.application;

import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 행 잠금 공개 Service (003 T018, plan Constitution II, research R8). 다른 모듈이 회원 행을 기준으로 직렬화할 때 쓴다(예:
 * 003 사진 저장 공간 판정 — 같은 회원의 동시 presign이 합계 한도를 넘지 않게). account 모듈의 {@code MemberRepository}를 밖에 열지
 * 않으려고 둔다.
 */
@Service
public class MemberLockService {

    private final MemberRepository members;

    public MemberLockService(MemberRepository members) {
        this.members = members;
    }

    /**
     * 호출자 트랜잭션 안에서 회원 행을 {@code SELECT … FOR UPDATE}로 잠근다. 잠금은 그 트랜잭션이 끝날 때 풀린다.
     *
     * @throws NotFoundException 없는 회원
     * @throws org.springframework.transaction.IllegalTransactionStateException 트랜잭션 밖에서 부름
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(long memberId) {
        members.findByIdForUpdate(memberId).orElseThrow(() -> new NotFoundException("잠글 회원이 없습니다"));
    }
}
