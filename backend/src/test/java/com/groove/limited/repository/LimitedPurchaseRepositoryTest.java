package com.groove.limited.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.LimitedPurchaseFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class LimitedPurchaseRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 드롭·회원 조합을 두 번 저장하면 유니크 제약 위반이 발생한다")
		void throwsWhenDropAndMemberDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-uk@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품1");
			limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop, member));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseRepository.saveAndFlush(
					LimitedPurchaseFixture.create(drop, member)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("order 없이도 저장할 수 있다")
		void savesWithoutOrder() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-noorder@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품2");

			// when
			LimitedPurchase saved = limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop,
					member));

			// then
			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getOrder()).isNull();
		}

		@Test
		@DisplayName("같은 드롭이라도 다른 회원이면 저장에 성공한다")
		void savesWhenDifferentMember() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-member1@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("limited-repo-member2@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품3");
			limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop, member));

			// when
			LimitedPurchase saved = limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop,
					other));

			// then
			assertThat(saved.getId()).isNotNull();
		}
	}

	@Nested
	@DisplayName("existsByDropIdAndMemberId()")
	class ExistsByDropIdAndMemberId {

		@Test
		@DisplayName("구매 이력이 있으면 true 를 반환한다")
		void returnsTrueWhenExists() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-exists@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품4");
			limitedPurchaseRepository.save(LimitedPurchaseFixture.create(drop, member));

			// when
			boolean exists = limitedPurchaseRepository.existsByDropIdAndMemberId(drop.getId(), member.getId());

			// then
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("구매 이력이 없으면 false 를 반환한다")
		void returnsFalseWhenAbsent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-absent@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품5");

			// when
			boolean exists = limitedPurchaseRepository.existsByDropIdAndMemberId(drop.getId(), member.getId());

			// then
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("findAllWithMemberAndOrderByDropId()")
	class FindAllWithMemberAndOrderByDropId {

		@Test
		@DisplayName("회원을 즉시 로딩하며 주문이 없는 구매도 함께 조회한다")
		void returnsPurchasesWithMemberFetchedAndOrderNullable() {
			// given
			Member member = memberRepository.save(MemberFixture.create("limited-repo-fetch@groove.com"));
			LimitedDrop drop = createDrop("한정반 구매 상품6");
			LimitedPurchase saved = limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop,
					member));

			// when
			List<LimitedPurchase> purchases = limitedPurchaseRepository
					.findAllWithMemberAndOrderByDropId(drop.getId());

			// then
			assertThat(purchases).extracting("id").contains(saved.getId());
			LimitedPurchase found = purchases.stream()
					.filter(p -> p.getId().equals(saved.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(found.getMember().getId()).isEqualTo(member.getId());
			assertThat(found.getOrder()).isNull();
		}
	}

	private LimitedDrop createDrop(String productTitle) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist, productTitle));
		return limitedDropRepository.save(LimitedDropFixture.scheduled(product));
	}
}
