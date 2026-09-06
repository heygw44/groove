package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class LocalDemoDataSeederTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	ProductRepository productRepository;

	@Mock
	ReviewRepository reviewRepository;

	@Nested
	@DisplayName("seed()")
	class Seed {

		@Test
		@DisplayName("회원 이메일이 이미 있으면 해당 회원 생성을 건너뛴다")
		void skipsMemberWhenEmailExists() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);
			given(reviewRepository.count()).willReturn(1L);

			// when
			seeder.seed();

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("회원이 하나도 없으면 관리자·데모 회원 3명을 생성한다")
		void seedsThreeMembersWhenNoneExist() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(memberRepository.existsByEmail(anyString())).willReturn(false);
			given(reviewRepository.count()).willReturn(1L);

			// when
			seeder.seed();

			// then
			verify(memberRepository, times(3)).save(any());
		}

		@Test
		@DisplayName("리뷰 데이터가 이미 있으면 리뷰 시딩을 건너뛴다")
		void skipsReviewsWhenReviewsExist() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(reviewRepository.count()).willReturn(1L);

			// when
			seeder.seed();

			// then
			verify(reviewRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("리뷰 데이터가 비어 있으면 상품마다 회원 2명의 리뷰를 시딩한다")
		void seedsTwoReviewsPerProductWhenEmpty() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(reviewRepository.count()).willReturn(0L);
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			given(memberRepository.findByEmail("user1@groove.com")).willReturn(Optional.of(user1));
			given(memberRepository.findByEmail("user2@groove.com")).willReturn(Optional.of(user2));
			int productCount = 5;
			List<Product> products = IntStream.range(0, productCount)
					.mapToObj(i -> ProductFixture.withId(ProductFixture.create(null), (long) (i + 1)))
					.toList();
			given(productRepository.findAll(any(Sort.class))).willReturn(products);

			// when
			seeder.seed();

			// then
			ArgumentCaptor<List<Review>> captor = ArgumentCaptor.forClass(List.class);
			verify(reviewRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).hasSize(productCount * 2);
			verify(productRepository, times(productCount)).refreshReviewStats(anyLong());
		}

		@Test
		@DisplayName("user1·user2 를 찾아 신호 시딩용 목록으로 돌려준다")
		void returnsUser1AndUser2AsDemoMembers() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(reviewRepository.count()).willReturn(1L);
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			given(memberRepository.findByEmail("user1@groove.com")).willReturn(Optional.of(user1));
			given(memberRepository.findByEmail("user2@groove.com")).willReturn(Optional.of(user2));

			// when
			List<Member> demoMembers = seeder.seed();

			// then
			assertThat(demoMembers).containsExactly(user1, user2);
		}
	}

	private LocalDemoDataSeeder newSeeder() {
		return new LocalDemoDataSeeder(memberRepository, passwordEncoder, productRepository, reviewRepository);
	}
}
