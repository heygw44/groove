package com.groove.member.application;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberEmailDuplicatedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 회원가입.
     *
     * <p>이메일 중복(soft delete 포함) 검사 → BCrypt 해시 → 영속화.
     *
     * @throws MemberEmailDuplicatedException 동일 이메일이 이미 존재
     */
    @Transactional
    public Member signup(SignupCommand command) {
        if (memberRepository.existsByEmail(command.email())) {
            throw new MemberEmailDuplicatedException(command.email());
        }
        String passwordHash = passwordEncoder.encode(command.password());
        Member member = Member.register(
                command.email(),
                passwordHash,
                command.name(),
                command.phone()
        );
        return memberRepository.save(member);
    }
}
