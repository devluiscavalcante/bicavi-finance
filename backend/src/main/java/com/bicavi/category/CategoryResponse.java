package com.bicavi.category;

import com.bicavi.transaction.TransactionType;

public record CategoryResponse(Long id, String name, TransactionType type) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}
