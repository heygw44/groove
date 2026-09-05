package com.groove.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.dto.AdminMemberActivitySummary;
import com.groove.member.dto.AdminMemberSearchCondition;
import com.groove.member.dto.AdminMemberSummaryResponse;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.order.entity.Order;
import com.groove.payment.entity.Payment;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 회원이 섞이므로 자기 id 로만 단언한다. */
class MemberQueryMapperTest extends MybatisTestSupport {

	private static final BigDecimal PRODUCT_PRICE = new BigDecimal("45000.00");

	@Autowired
	private MemberQueryMapper memberQueryMapper;

	@Autowired
	private EntityManager em;

	private Artist artist;
	private Product product;

	@BeforeEach
	void setUp() {
		artist = ArtistFixture.create();
		em.persist(artist);
		product = ProductFixture.create(artist, "MQM Kind of Blue");
		em.persist(product);
	}

	private static AdminMemberSearchCondition condition(String keyword, MemberStatus status, MemberRole role) {
		return new AdminMemberSearchCondition(keyword, status, role, 0, 20);
	}

	private Order paidOrderWithDonePayment(Member member, String orderNumber) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, 1);
		order.markPaid();
		em.persist(order);
		// uk_payment_key 위반을 피하려 주문번호 기반으로 매번 다른 결제 키를 쓴다.
		Payment payment = PaymentFixture.approved(order, "MQM-" + orderNumber);
		em.persist(payment);
		em.flush();
		return order;
	}

	@Nested
	@DisplayName("findAdminMembers() / countAdminMembers()")
	class FindAndCountAdminMembers {

		@Test
		@DisplayName("keyword 로 이메일을 검색하면 해당 회원만 반환한다")
		void filtersByEmailKeyword() {
			// given
			Member target = MemberFixture.create("mqm-target@groove.com");
			Member other = MemberFixture.create("mqm-other@groove.com");
			em.persist(target);
			em.persist(other);
			em.flush();
			em.clear();

			// when
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition("mqm-target", null, null));

			// then
			assertThat(result).extracting(AdminMemberSummaryResponse::id).contains(target.getId());
			assertThat(result).extracting(AdminMemberSummaryResponse::id).doesNotContain(other.getId());
		}

		@Test
		@DisplayName("keyword 로 닉네임을 검색하면 해당 회원만 반환한다")
		void filtersByNicknameKeyword() {
			// given
			Member target = MemberFixture.create("mqm-nick-target@groove.com", "MQM닉네임그루버");
			em.persist(target);
			em.flush();
			em.clear();

			// when
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition("MQM닉네임그루버", null, null));

			// then
			assertThat(result).extracting(AdminMemberSummaryResponse::id).containsExactly(target.getId());
		}

		@Test
		@DisplayName("status 로 필터링하면 해당 상태의 회원만 반환한다")
		void filtersByStatus() {
			// given
			Member suspended = MemberFixture.createSuspended("mqm-suspended@groove.com");
			Member active = MemberFixture.create("mqm-active@groove.com");
			em.persist(suspended);
			em.persist(active);
			em.flush();
			em.clear();

			// when
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition(null, MemberStatus.SUSPENDED, null));

			// then
			assertThat(result).extracting(AdminMemberSummaryResponse::id).contains(suspended.getId());
			assertThat(result).extracting(AdminMemberSummaryResponse::id).doesNotContain(active.getId());
		}

		@Test
		@DisplayName("role 로 필터링하면 해당 권한의 회원만 반환한다")
		void filtersByRole() {
			// given
			Member admin = MemberFixture.createAdmin("mqm-admin@groove.com");
			Member user = MemberFixture.create("mqm-user@groove.com");
			em.persist(admin);
			em.persist(user);
			em.flush();
			em.clear();

			// when
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition(null, null, MemberRole.ADMIN));

			// then
			assertThat(result).extracting(AdminMemberSummaryResponse::id).contains(admin.getId());
			assertThat(result).extracting(AdminMemberSummaryResponse::id).doesNotContain(user.getId());
		}

		@Test
		@DisplayName("PENDING/CANCELED 를 제외한 주문 수와 DONE 결제 합계를 반환한다")
		void aggregatesOrderCountAndDonePaymentAmount() {
			// given
			Member member = MemberFixture.create("mqm-agg@groove.com");
			em.persist(member);
			em.flush();
			paidOrderWithDonePayment(member, "20260905-MQM00001");
			paidOrderWithDonePayment(member, "20260905-MQM00002");
			Order pendingOrder = OrderFixture.create(member, "20260905-MQM00003");
			pendingOrder.addItem(product, 1);
			em.persist(pendingOrder);
			em.flush();
			em.clear();

			// when
			AdminMemberSummaryResponse result = memberQueryMapper.findAdminMembers(
					condition("mqm-agg", null, null)).stream()
					.filter(summary -> summary.id().equals(member.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.orderCount()).isEqualTo(2);
			assertThat(result.totalPaymentAmount()).isEqualByComparingTo(PRODUCT_PRICE.multiply(BigDecimal.valueOf(2)));
		}

		@Test
		@DisplayName("주문·결제가 없으면 0 을 반환한다")
		void returnsZeroWhenNoOrders() {
			// given
			Member member = MemberFixture.create("mqm-empty@groove.com");
			em.persist(member);
			em.flush();
			em.clear();

			// when
			AdminMemberSummaryResponse result = memberQueryMapper.findAdminMembers(
					condition("mqm-empty", null, null)).stream()
					.filter(summary -> summary.id().equals(member.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.orderCount()).isZero();
			assertThat(result.totalPaymentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("최신순(created_at DESC, id DESC)으로 반환한다")
		void sortsByLatest() {
			// given
			Member first = MemberFixture.create("mqm-order-first@groove.com");
			em.persist(first);
			em.flush();
			Member second = MemberFixture.create("mqm-order-second@groove.com");
			em.persist(second);
			em.flush();
			em.clear();

			// when
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition("mqm-order-", null, null));

			// then
			assertThat(result).extracting(AdminMemberSummaryResponse::id)
					.containsExactly(second.getId(), first.getId());
		}

		@Test
		@DisplayName("countAdminMembers 는 findAdminMembers 결과 개수와 일치한다")
		void countMatchesFindResultSize() {
			// given
			Member member = MemberFixture.create("mqm-count@groove.com");
			em.persist(member);
			em.flush();
			em.clear();

			// when
			long count = memberQueryMapper.countAdminMembers(condition("mqm-count", null, null));
			List<AdminMemberSummaryResponse> result = memberQueryMapper.findAdminMembers(
					condition("mqm-count", null, null));

			// then
			assertThat(count).isEqualTo(result.size());
		}
	}

	@Nested
	@DisplayName("findActivitySummary()")
	class FindActivitySummary {

		@Test
		@DisplayName("사용 가능한 쿠폰만 usableCouponCount 에 포함한다")
		void countsOnlyUsableCoupons() {
			// given
			Member member = MemberFixture.create("mqm-coupon@groove.com");
			em.persist(member);
			em.flush();

			Coupon usable = CouponFixture.fixed("MQM-USABLE", new BigDecimal("1000"));
			Coupon used = CouponFixture.fixed("MQM-USED", new BigDecimal("1000"));
			Coupon expired = CouponFixture.expired(CouponFixture.fixed("MQM-EXPIRED", new BigDecimal("1000")));
			Coupon disabled = CouponFixture.fixed("MQM-DISABLED", new BigDecimal("1000"));
			disabled.disable();
			em.persist(usable);
			em.persist(used);
			em.persist(expired);
			em.persist(disabled);

			MemberCoupon usableMemberCoupon = MemberCouponFixture.create(member, usable);
			MemberCoupon usedMemberCoupon = MemberCouponFixture.create(member, used);
			usedMemberCoupon.use(1L);
			em.persist(usableMemberCoupon);
			em.persist(usedMemberCoupon);
			em.persist(MemberCouponFixture.create(member, expired));
			em.persist(MemberCouponFixture.create(member, disabled));
			em.flush();
			em.clear();

			// when
			AdminMemberActivitySummary result = memberQueryMapper.findActivitySummary(member.getId(),
					LocalDateTime.now());

			// then
			assertThat(result.usableCouponCount()).isEqualTo(1L);
		}

		@Test
		@DisplayName("주문·결제·쿠폰이 없으면 모두 0 을 반환한다")
		void returnsZeroWhenNoActivity() {
			// given
			Member member = MemberFixture.create("mqm-summary-empty@groove.com");
			em.persist(member);
			em.flush();
			em.clear();

			// when
			AdminMemberActivitySummary result = memberQueryMapper.findActivitySummary(member.getId(),
					LocalDateTime.now());

			// then
			assertThat(result.orderCount()).isZero();
			assertThat(result.totalPaymentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(result.usableCouponCount()).isZero();
		}

		@Test
		@DisplayName("PENDING/CANCELED 를 제외한 주문 수와 DONE 결제 합계를 반환한다")
		void aggregatesOrderCountAndDonePaymentAmount() {
			// given
			Member member = MemberFixture.create("mqm-summary-agg@groove.com");
			em.persist(member);
			em.flush();
			paidOrderWithDonePayment(member, "20260905-MQMSUM001");
			em.clear();

			// when
			AdminMemberActivitySummary result = memberQueryMapper.findActivitySummary(member.getId(),
					LocalDateTime.now());

			// then
			assertThat(result.orderCount()).isEqualTo(1);
			assertThat(result.totalPaymentAmount()).isEqualByComparingTo(PRODUCT_PRICE);
		}
	}
}
