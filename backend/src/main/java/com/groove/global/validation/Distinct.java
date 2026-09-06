package com.groove.global.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 컬렉션에 중복 원소가 없어야 함을 나타낸다. 클래스 레벨 {@code @AssertTrue} 와 달리
 * 검증 실패 시 fieldErrors 의 field 가 프로퍼티명이 아닌 실제 필드명으로 나간다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctValidator.class)
public @interface Distinct {

	String message() default "중복된 값이 있습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
