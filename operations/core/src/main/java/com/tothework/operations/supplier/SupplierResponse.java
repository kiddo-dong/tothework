package com.tothework.operations.supplier;

import java.util.List;

public record SupplierResponse(
        Long id,
        String name,
        String category,
        String siteUrl,
        String phone,
        String memo,
        int displayOrder,
        List<SupplierItemResponse> items
) {

    static SupplierResponse from(Supplier supplier, List<SupplierItemResponse> items) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getCategory(),
                supplier.getSiteUrl(), supplier.getPhone(), supplier.getMemo(), supplier.getDisplayOrder(), items);
    }
}
