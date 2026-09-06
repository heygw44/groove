package com.groove.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CatalogImportJobRequest(
		@NotNull @Positive Long discogsMasterId,
		@NotNull @Positive @Digits(integer = 8, fraction = 0) BigDecimal defaultPrice
) {
}
