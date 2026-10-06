package com.bicavi.category;

public record CategoryResponse(Long id, String name, CategoryType type) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}
