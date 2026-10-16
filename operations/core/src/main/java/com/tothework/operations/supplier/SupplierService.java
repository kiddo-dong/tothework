package com.tothework.operations.supplier;

import com.tothework.operations.api.OrderLine;
import com.tothework.operations.api.Suppliers;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StoreAccess;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService implements Suppliers {

    private final SupplierRepository supplierRepository;
    private final SupplierItemRepository supplierItemRepository;
    private final SupplierItemLists supplierItemLists;
    private final StoreAccess storeAccess;

    @Transactional
    public SupplierResponse createSupplier(Long storeId, Long userId, SupplierRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return SupplierResponse.from(supplierRepository.save(Supplier.register(storeId, request)), List.of());
    }

    /** 거래처 목록 + 각 거래처의 발주 품목(요일별 수량 포함) — OWNER/MANAGER 전용. */
    public List<SupplierResponse> getSuppliers(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        List<Supplier> suppliers = supplierRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId);
        Map<Long, List<SupplierItemResponse>> items = supplierItemLists.ofSuppliers(
                suppliers.stream().map(Supplier::getId).toList());
        return suppliers.stream()
                .map(supplier -> SupplierResponse.from(supplier, items.getOrDefault(supplier.getId(), List.of())))
                .toList();
    }

    @Transactional
    public SupplierResponse updateSupplier(Long storeId, Long supplierId, Long userId, SupplierRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        supplier.update(request);
        Supplier saved = supplierRepository.save(supplier);
        return SupplierResponse.from(saved,
                supplierItemLists.ofSuppliers(List.of(saved.getId())).getOrDefault(saved.getId(), List.of()));
    }

    /** 품목과 요일별 발주량은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Transactional
    public void deleteSupplier(Long storeId, Long supplierId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        supplierRepository.delete(getSupplierInStore(storeId, supplierId));
    }

    @Transactional
    public SupplierItemResponse addItem(Long storeId, Long supplierId, Long userId, SupplierItemRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        return SupplierItemResponse.from(supplierItemRepository.save(SupplierItem.add(supplier.getId(), request)));
    }

    @Transactional
    public SupplierItemResponse updateItem(Long storeId, Long itemId, Long userId, SupplierItemRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        SupplierItem item = getItemInStore(storeId, itemId);
        item.update(request);
        return SupplierItemResponse.from(supplierItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long storeId, Long itemId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        supplierItemRepository.delete(getItemInStore(storeId, itemId));
    }

    /** 그 요일에 발주 수량이 정해진 품목. 거래처·품목의 표시 순서를 따른다. */
    @Override
    public List<OrderLine> findOrderLinesFor(Long storeId, DayOfWeek day) {
        List<Supplier> suppliers = supplierRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId);
        Map<Long, List<SupplierItemResponse>> items = supplierItemLists.ofSuppliers(
                suppliers.stream().map(Supplier::getId).toList());
        List<OrderLine> lines = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            for (SupplierItemResponse item : items.getOrDefault(supplier.getId(), List.of())) {
                if (item.weeklyQuantities().containsKey(day)) {
                    lines.add(new OrderLine(supplier.getName(), item.name(), item.spec(),
                            item.weeklyQuantities().get(day)));
                }
            }
        }
        return lines;
    }

    private Supplier getSupplierInStore(Long storeId, Long supplierId) {
        return supplierRepository.findByIdAndStoreId(supplierId, storeId)
                .orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
    }

    private SupplierItem getItemInStore(Long storeId, Long itemId) {
        return supplierItemRepository.findInStore(itemId, storeId)
                .orElseThrow(() -> new NotFoundException("발주 품목을 찾을 수 없습니다."));
    }
}
