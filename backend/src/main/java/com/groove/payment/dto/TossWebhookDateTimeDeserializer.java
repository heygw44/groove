package com.groove.payment.dto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * 토스 웹훅 본문의 createdAt 역직렬화. 토스 문서의 PAYMENT_STATUS_CHANGED·DEPOSIT_CALLBACK 예시는
 * "2022-01-01T00:00:00.000000" 처럼 오프셋 없이 오는데, 기본 OffsetDateTime 역직렬화는 이를 거부해 이벤트가
 * 파싱 실패로 조용히 버려진다. 오프셋이 있으면 그대로 쓰고, 없으면 토스 기준 시간대인 Asia/Seoul 로 해석한다.
 * 파싱할 수 없는 문자열은 그대로 Jackson 예외로 던져 호출부의 파싱 실패 처리를 탄다.
 */
public class TossWebhookDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {

	private static final ZoneId TOSS_ZONE = ZoneId.of("Asia/Seoul");

	/** 소수 초 0~9자리, 오프셋(Z·+09:00) 선택. */
	private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			.optionalStart()
			.appendOffsetId()
			.optionalEnd()
			.toFormatter();

	public TossWebhookDateTimeDeserializer() {
		super(OffsetDateTime.class);
	}

	@Override
	public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (!parser.hasToken(JsonToken.VALUE_STRING)) {
			return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
		}
		String text = parser.getText().trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			TemporalAccessor parsed = FORMATTER.parseBest(text, OffsetDateTime::from, LocalDateTime::from);
			if (parsed instanceof OffsetDateTime offsetDateTime) {
				return offsetDateTime;
			}
			return ((LocalDateTime) parsed).atZone(TOSS_ZONE).toOffsetDateTime();
		} catch (DateTimeParseException ex) {
			throw context.weirdStringException(text, OffsetDateTime.class, ex.getMessage());
		}
	}
}
