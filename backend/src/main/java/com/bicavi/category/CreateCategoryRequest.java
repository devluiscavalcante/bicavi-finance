package com.bicavi.category;

import com.bicavi.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull TransactionType type
) {
}
