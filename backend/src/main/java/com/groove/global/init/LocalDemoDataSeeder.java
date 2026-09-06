package com.groove.global.init;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자/데모 회원 계정과 리뷰를 시딩한다. 자격증명이 공개 저장소에 상수로 박혀 있어 운영에서 실행되면
 * 그대로 노출된다 — local 전용이어야 한다. 실제로 운영에 ADMIN 계정이 생성된 적이 있어 분리했다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDemoDataSeeder {

	private static final String ADMIN_EMAIL = "admin@groove.com";
	private static final String ADMIN_PASSWORD = "admin1234!";
	private static final String ADMIN_NICKNAME = "관리자";

	private static final String USER1_EMAIL = "user1@groove.com";
	private static final String USER1_NICKNAME = "그루브1";
	private static final String USER2_EMAIL = "user2@groove.com";
	private static final String USER2_NICKNAME = "그루브2";
	private static final String USER_PASSWORD = "user1234!";

	private static final List<String> REVIEW_TITLES = List.of(
			"자주 듣게 되는 앨범", "믹싱이 훌륭합니다", "소장 가치 있음", "기대 이상이었어요", "재구매 의사 있습니다");

	private static final List<String> REVIEW_CONTENTS = List.of(
			"판 상태도 좋고 배송도 빨랐습니다.",
			"음질이 생각보다 훨씬 좋네요.",
			"자켓 디자인이 마음에 듭니다.",
			"플레이어에 걸어두고 매일 듣고 있어요.",
			"선물용으로 구매했는데 반응이 좋았습니다.");

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final ProductRepository productRepository;
	private final ReviewRepository reviewRepository;

	/** 계정·리뷰를 시딩하고, 취향/행동 신호 시딩(조회 로그)의 대상인 user1·user2 를 돌려준다. */
	public List<Member> seed() {
		seedMembers();
		seedReviews();
		return demoMembers();
	}

	private void seedMembers() {
		createMemberIfAbsent(ADMIN_EMAIL,
				() -> Member.createAdmin(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), ADMIN_NICKNAME));
		createMemberIfAbsent(USER1_EMAIL,
				() -> Member.create(USER1_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER1_NICKNAME));
		createMemberIfAbsent(USER2_EMAIL,
				() -> Member.create(USER2_EMAIL, passwordEncoder.encode(USER_PASSWORD), USER2_NICKNAME));
	}

	/** 조회 로그는 이 둘에게만 심는다. 취향 클러스터 회원에 심으면 추천 후보에서 빠져 품질 측정이 흔들린다. */
	private List<Member> demoMembers() {
		return Stream.of(USER1_EMAIL, USER2_EMAIL)
				.map(memberRepository::findByEmail)
				.flatMap(Optional::stream)
				.toList();
	}

	private void createMemberIfAbsent(String email, Supplier<Member> factory) {
		if (memberRepository.existsByEmail(email)) {
			log.info("더미 회원 계정이 이미 있어 건너뛴다: {}", email);
			return;
		}
		memberRepository.save(factory.get());
		log.info("더미 회원 계정을 생성했다: {}", email);
	}

	private void seedReviews() {
		if (reviewRepository.count() > 0) {
			log.info("리뷰 데이터가 이미 있어 시딩을 건너뛴다");
			return;
		}

		Member user1 = memberRepository.findByEmail(USER1_EMAIL).orElseThrow();
		Member user2 = memberRepository.findByEmail(USER2_EMAIL).orElseThrow();
		List<Member> reviewers = List.of(user1, user2);
		List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

		List<Review> reviews = new ArrayList<>();
		for (int productIndex = 0; productIndex < products.size(); productIndex++) {
			Product product = products.get(productIndex);
			for (int memberIndex = 0; memberIndex < reviewers.size(); memberIndex++) {
				Member reviewer = reviewers.get(memberIndex);
				int rating = (productIndex * 7 + memberIndex * 3) % 5 + 1;
				int phraseIndex = (productIndex + memberIndex) % REVIEW_TITLES.size();
				reviews.add(Review.create(product, reviewer, rating, REVIEW_TITLES.get(phraseIndex),
						REVIEW_CONTENTS.get(phraseIndex)));
			}
		}
		reviewRepository.saveAll(reviews);
		products.forEach(product -> productRepository.refreshReviewStats(product.getId()));

		log.info("더미 리뷰를 시딩했다: {}건", reviews.size());
	}
}
