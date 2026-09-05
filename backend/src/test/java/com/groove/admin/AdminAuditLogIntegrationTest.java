package com.groove.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.entity.DiscountType;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 관리자 결제 취소 경로(ORDER_STATUS_CHANGE + PAYMENT_CANCEL)는
 * {@link com.groove.payment.PaymentFlowIntegrationTest} 의 AdminCancel 에서 검증한다.
 * PaymentClient 목이 필요 없는 경로만 모아 별도 컨텍스트 재생성을 늘리지 않는다.
 */
@AutoConfigureMockMvc
class AdminAuditLogIntegrationTest extends IntegrationTestSupport {

	private static final String FORWARDED_IP = "203.0.113.7";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Nested
	@DisplayName("여러 관리자 경로에서 감사 로그를 남긴다")
	class RecordsAcrossAdminPaths {

		@Test
		@DisplayName("상품 숨김·쿠폰 등록·한정반 오픈·재고 조정 모두 IP 를 포함한 감사 로그를 남긴다")
		void recordsIpAddressAcrossAdminPaths() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("audit-flow-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when: 상품 숨김
			Product hideTarget = seedProduct(10);
			mockMvc.perform(delete("/api/v1/admin/products/{id}", hideTarget.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", FORWARDED_IP))
					.andExpect(status().isOk());

			// when: 쿠폰 등록
			String code = "AUDIT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			CouponCreateRequest couponRequest = new CouponCreateRequest(code, "감사 로그 테스트 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, 10, LocalDateTime.now().plusDays(7));
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", FORWARDED_IP)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(couponRequest)))
					.andExpect(status().isCreated());

			// when: 한정반 강제 오픈
			Product limitedProduct = seedProduct(50);
			LimitedDropCreateRequest dropRequest = new LimitedDropCreateRequest(limitedProduct.getId(), 50, 2,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
			MvcResult dropResult = mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", FORWARDED_IP)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(dropRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long dropId = objectMapper.readTree(dropResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", FORWARDED_IP))
					.andExpect(status().isOk());

			// when: 재고 조정
			Product stockTarget = seedProduct(10);
			StockAdjustRequest stockRequest = StockFixture.adjustRequest(StockChangeType.IN, 5);
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", stockTarget.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.header("X-Forwarded-For", FORWARDED_IP)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(stockRequest)))
					.andExpect(status().isOk());

			// then
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction).containsExactly(
					AdminAuditAction.PRODUCT_HIDE, AdminAuditAction.COUPON_CREATE,
					AdminAuditAction.LIMITED_DROP_CREATE, AdminAuditAction.LIMITED_DROP_OPEN,
					AdminAuditAction.STOCK_ADJUST);
			assertThat(logs).extracting(AdminAuditLog::getIpAddress).containsOnly(FORWARDED_IP);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/audit-logs")
	class GetAuditLogs {

		@Test
		@DisplayName("action·targetType 으로 필터링하면 새로 남긴 로그를 관리자 닉네임과 함께 반환한다")
		void filtersByActionAndTargetType() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("audit-query-" + UUID.randomUUID() + "@groove.com", "encoded", "감사관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Product product = seedProduct(10);
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", product.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									StockFixture.adjustRequest(StockChangeType.IN, 5))))
					.andExpect(status().isOk());

			// when & then: STOCK_ADJUST 필터
			mockMvc.perform(get("/api/v1/admin/audit-logs")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("action", "STOCK_ADJUST")
							.param("adminId", admin.getId().toString()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].action", is("STOCK_ADJUST")))
					.andExpect(jsonPath("$.data.content[0].adminNickname", is("감사관리자")));

			// when & then: PAYMENT 필터에는 걸리지 않는다
			mockMvc.perform(get("/api/v1/admin/audit-logs")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("targetType", "PAYMENT")
							.param("adminId", admin.getId().toString()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content", hasSize(0)));
		}
	}

	@Nested
	@DisplayName("감사 로그 저장 실패")
	class SaveFailure {

		// 존재하지 않는 관리자 id 로 감사 로그 INSERT 가 FK 위반으로 실패하는 상황을 재현한다.
		// JWT 인증은 DB 조회 없이 클레임만으로 통과하므로 본 업무 로직에는 영향이 없다.
		private static final Long NON_EXISTENT_ADMIN_ID = 999_999_999L;

		@Test
		@DisplayName("저장이 실패해도 관리자 요청은 성공하고 상태 변경은 반영되지만 로그는 남지 않는다")
		void requestSucceedsAndStateChangesEvenWhenAuditLogSaveFails() throws Exception {
			// given
			String adminToken = "Bearer " + jwtProvider.createAccessToken(NON_EXISTENT_ADMIN_ID, MemberRole.ADMIN);
			Product product = seedProduct(10);

			// when
			mockMvc.perform(patch("/api/v1/admin/products/{productId}/stock", product.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									StockFixture.adjustRequest(StockChangeType.IN, 5))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.quantity", is(15)));

			// then
			assertThat(stockRepository.findWithProductByProductId(product.getId()).orElseThrow().getQuantity())
					.isEqualTo(15);
			assertThat(adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(NON_EXISTENT_ADMIN_ID)).isEmpty();
		}
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}
}
