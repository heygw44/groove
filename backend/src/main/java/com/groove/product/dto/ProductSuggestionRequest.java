package com.groove.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSuggestionRequest(
		@NotBlank @Size(max = 50) String keyword
) {
}
