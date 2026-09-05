package com.groove.recommend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class ProductViewLogRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ProductViewLogRepository viewLogRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("비로그인 조회는 member 없이 저장된다")
		void savesWithoutMemberForAnonymousView() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			ProductViewLog saved = viewLogRepository.save(
					ProductViewLogFixture.createAnonymous(product, LocalDateTime.now()));

			// when
			ProductViewLog found = viewLogRepository.findById(saved.getId()).orElseThrow();

			// then
			assertThat(found.getMember()).isNull();
		}

		@Test
		@DisplayName("로그인 조회는 회원과 조회 시각을 함께 저장한다")
		void savesWithMemberAndViewedAt() {
			// given
			Member member = memberRepository.save(MemberFixture.create("view-log-save@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			LocalDateTime viewedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
			ProductViewLog saved = viewLogRepository.save(ProductViewLogFixture.create(member, product, viewedAt));
			entityManager.flush();
			entityManager.clear();

			// when
			ProductViewLog found = viewLogRepository.findById(saved.getId()).orElseThrow();

			// then
			assertThat(found.getMember().getId()).isEqualTo(member.getId());
			assertThat(found.getViewedAt()).isEqualTo(viewedAt);
		}
	}
}
