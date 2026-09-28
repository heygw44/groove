package com.groove.order;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderShippingAddressRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class OrderShippingAddressIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Nested
	@DisplayName("PATCH /api/v1/orders/{id}/shipping-address")
	class ChangeShippingAddress {

		@Test
		@DisplayName("PENDING 주문의 배송지를 새 주소로 바꾼다")
		void changesAddress() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address originalAddress = addressRepository.save(AddressFixture.create(member));
			Address newAddress = addressRepository.save(AddressFixture.create(member, "김바이닐"));
			Product product = seedProduct(5);
			long orderId = createOrder(accessToken, product.getId(), 1, originalAddress.getId());

			// when & then
			mockMvc.perform(patch("/api/v1/orders/" + orderId + "/shipping-address")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderShippingAddressRequest(newAddress.getId()))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.shippingAddress.recipientName", is("김바이닐")));
		}

		@Test
		@DisplayName("한정반 주문도 배송지를 바꿀 수 있다")
		void changesAddressForLimitedOrder() throws Exception {
			// given
			Member member = signup();
			Address originalAddress = addressRepository.save(AddressFixture.create(member));
			Address newAddress = addressRepository.save(AddressFixture.create(member, "김바이닐"));
			Product product = seedProduct(5);
			Order limitedOrder = Order.create("20260928-LIMITED2", member, ShippingAddress.from(originalAddress),
					OrderSource.LIMITED, LocalDateTime.now());
			limitedOrder.addItem(product, 1);
			orderRepository.save(limitedOrder);
			String accessToken = login(member.getEmail());

			// when & then
			mockMvc.perform(patch("/api/v1/orders/" + limitedOrder.getId() + "/shipping-address")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderShippingAddressRequest(newAddress.getId()))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.shippingAddress.recipientName", is("김바이닐")));
		}

		@Test
		@DisplayName("확정된 주문이면 409 ORDER_INVALID_STATUS 를 반환한다")
		void returnsConflictWhenAlreadyPlaced() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address originalAddress = addressRepository.save(AddressFixture.create(member));
			Address newAddress = addressRepository.save(AddressFixture.create(member, "김바이닐"));
			Product product = seedProduct(5);
			long orderId = createOrder(accessToken, product.getId(), 1, originalAddress.getId());
			Order order = orderRepository.findById(orderId).orElseThrow();
			order.place(LocalDateTime.now());
			orderRepository.save(order);

			// when & then
			mockMvc.perform(patch("/api/v1/orders/" + orderId + "/shipping-address")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderShippingAddressRequest(newAddress.getId()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_INVALID_STATUS")));
		}

		@Test
		@DisplayName("결제가 READY 이면 409 ORDER_INVALID_STATUS 를 반환한다")
		void returnsConflictWhenPaymentReady() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address originalAddress = addressRepository.save(AddressFixture.create(member));
			Address newAddress = addressRepository.save(AddressFixture.create(member, "김바이닐"));
			Product product = seedProduct(5);
			long orderId = createOrder(accessToken, product.getId(), 1, originalAddress.getId());
			Order order = orderRepository.findById(orderId).orElseThrow();
			paymentRepository.save(Payment.ready(order));

			// when & then
			mockMvc.perform(patch("/api/v1/orders/" + orderId + "/shipping-address")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderShippingAddressRequest(newAddress.getId()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_INVALID_STATUS")));
		}

		@Test
		@DisplayName("본인 소유가 아닌 배송지면 404 MEMBER_ADDRESS_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenAddressNotOwned() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address originalAddress = addressRepository.save(AddressFixture.create(member));
			Member otherMember = signup();
			Address othersAddress = addressRepository.save(AddressFixture.create(otherMember));
			Product product = seedProduct(5);
			long orderId = createOrder(accessToken, product.getId(), 1, originalAddress.getId());

			// when & then
			mockMvc.perform(patch("/api/v1/orders/" + orderId + "/shipping-address")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderShippingAddressRequest(othersAddress.getId()))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));
		}
	}

	private long createOrder(String accessToken, Long productId, int quantity, Long addressId) throws Exception {
		OrderCreateRequest request = new OrderCreateRequest(null, productId, quantity, addressId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("orderId").asLong();
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member signup() throws Exception {
		String email = "order-address-" + UUID.randomUUID() + "@groove.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, "password1", "그루버"))))
				.andExpect(status().isCreated());
		return memberRepository.findByEmail(email).orElseThrow();
	}

	private String login(String email) throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "password1"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
	}
}
