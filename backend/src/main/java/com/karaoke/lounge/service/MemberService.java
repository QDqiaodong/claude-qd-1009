package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.repository.MemberRepository;

/**
 * 会员业务。
 *
 * <p>规则：① 会员号唯一；② 手机号唯一；③ 等级须为合法枚举；④ 预存余额 &ge; 0。</p>
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<Member> list(String level) {
        if (level != null && !level.isBlank()) {
            if (!Dict.isValidMemberLevel(level)) {
                throw new BizException("会员等级不合法：" + level);
            }
            return memberRepository.findByLevel(level);
        }
        return memberRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
    }

    @Transactional
    public Member create(Member input) {
        if (input.getMemberNo() == null || input.getMemberNo().isBlank()) {
            throw new BizException("会员号不能为空");
        }
        if (memberRepository.existsByMemberNo(input.getMemberNo().trim())) {
            throw new BizException("会员号已存在：" + input.getMemberNo());
        }
        if (input.getMemberName() == null || input.getMemberName().isBlank()) {
            throw new BizException("会员姓名不能为空");
        }
        if (input.getPhone() == null || input.getPhone().isBlank()) {
            throw new BizException("手机号不能为空");
        }
        if (memberRepository.existsByPhone(input.getPhone().trim())) {
            throw new BizException("手机号已注册：" + input.getPhone());
        }
        String level = (input.getLevel() == null || input.getLevel().isBlank()) ? "普通" : input.getLevel();
        if (!Dict.isValidMemberLevel(level)) {
            throw new BizException("会员等级不合法：" + level + "，应为 " + Dict.MEMBER_LEVELS);
        }
        if (input.getBalance() == null || input.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("预存余额不能为负数");
        }
        Member member = new Member();
        member.setMemberNo(input.getMemberNo().trim());
        member.setMemberName(input.getMemberName().trim());
        member.setPhone(input.getPhone().trim());
        member.setLevel(level);
        member.setBalance(input.getBalance() == null ? BigDecimal.ZERO : input.getBalance());
        return memberRepository.save(member);
    }

    @Transactional
    public Member updateLevel(Long id, String level) {
        Member member = get(id);
        if (!Dict.isValidMemberLevel(level)) {
            throw new BizException("会员等级不合法：" + level);
        }
        member.setLevel(level);
        return memberRepository.save(member);
    }
}
