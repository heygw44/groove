package com.groove.global.validation;

import java.util.Collection;
import java.util.HashSet;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DistinctValidator implements ConstraintValidator<Distinct, Collection<?>> {

	/** null 컬렉션은 {@code @NotNull} 의 몫이라 통과시킨다. 원소에 null 이 섞여도 세야 해서 {@code Set.copyOf} 는 쓸 수 없다. */
	@Override
	public boolean isValid(Collection<?> values, ConstraintValidatorContext context) {
		if (values == null) {
			return true;
		}
		return new HashSet<>(values).size() == values.size();
	}
}
