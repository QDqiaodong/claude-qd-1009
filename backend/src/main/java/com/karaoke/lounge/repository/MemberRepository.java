package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberNo(String memberNo);

    boolean existsByPhone(String phone);

    List<Member> findByLevel(String level);
}
