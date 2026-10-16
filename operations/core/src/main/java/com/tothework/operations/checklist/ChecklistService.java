package com.tothework.operations.checklist;

import com.tothework.identity.api.UserSummary;
import com.tothework.operations.people.MemberProfiles;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistCompletionRepository checklistCompletionRepository;
    private final StoreAccess storeAccess;
    private final MemberProfiles memberProfiles;

    @Transactional
    public ChecklistItemResponse addItem(Long storeId, Long userId, ChecklistItemRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return ChecklistItemResponse.from(checklistItemRepository.save(ChecklistItem.create(storeId, request.type(),
                request.content(), request.displayOrderOrDefault())));
    }

    /**
     * 여러 항목을 한 번에 등록한다. 업종 프리셋처럼 10개 안팎을 넣을 때 쓰인다.
     *
     * <p>한 트랜잭션이라 하나라도 실패하면 전부 되돌아간다. 단건 등록을 반복하면 앞의 몇 개만 남는 상태가 생기는데,
     * 온보딩에서는 항목이 하나만 있어도 완료로 보여 그 절반의 실패를 알아채기 어렵다.
     *
     * <p>{@code displayOrder}는 요청 배열의 순서로 매긴다. 다만 0부터 다시 매기면 이미 있는 항목과 겹치므로, 그
     * 매장·구분의 현재 최대 순번 뒤에 이어 붙인다.
     */
    @Transactional
    public List<ChecklistItemResponse> addItems(Long storeId, Long userId, ChecklistItemBulkRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Map<ChecklistType, Integer> nextOrder = new EnumMap<>(ChecklistType.class);
        List<ChecklistItem> items = new ArrayList<>();
        for (ChecklistItemRequest item : request.items()) {
            int order = nextOrder.computeIfAbsent(item.type(), type -> {
                Integer max = checklistItemRepository.findMaxDisplayOrder(storeId, type.name());
                return max == null ? 0 : max + 1;
            });
            nextOrder.put(item.type(), order + 1);
            items.add(ChecklistItem.create(storeId, item.type(), item.content(), order));
        }
        return checklistItemRepository.saveAll(items).stream().map(ChecklistItemResponse::from).toList();
    }

    public List<ChecklistItemResponse> getItems(Long storeId, Long userId) {
        storeAccess.requireMember(storeId, userId);
        return checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId).stream()
                .map(ChecklistItemResponse::from)
                .toList();
    }

    @Transactional
    public ChecklistItemResponse updateItem(Long storeId, Long itemId, Long userId, ChecklistItemRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        item.update(request.type(), request.content(), request.displayOrderOrDefault());
        return ChecklistItemResponse.from(checklistItemRepository.save(item));
    }

    /** 체크 기록은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Transactional
    public void deleteItem(Long storeId, Long itemId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        checklistItemRepository.delete(getItemInStore(storeId, itemId));
    }

    /** 특정 날짜의 체크리스트 현황: 항목 + 체크 여부 + 누가 언제 체크했는지. */
    public List<DailyChecklistEntry> getDailyChecklist(Long storeId, Long userId, LocalDate date) {
        storeAccess.requireMember(storeId, userId);
        Map<Long, ChecklistCompletion> completionByItemId = checklistCompletionRepository
                .findAllByStoreIdAndWorkDate(storeId, date).stream()
                .collect(Collectors.toMap(ChecklistCompletion::getItemId, Function.identity()));
        Map<Long, UserSummary> checkers = memberProfiles.of(storeId,
                completionByItemId.values().stream().map(ChecklistCompletion::getCheckedBy).distinct().toList());
        return checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId).stream()
                .map(item -> {
                    ChecklistCompletion completion = completionByItemId.get(item.getId());
                    return new DailyChecklistEntry(item.getId(), item.getType(), item.getContent(),
                            item.getDisplayOrder(), completion != null,
                            completion == null ? null : MemberProfiles.nameOf(checkers.get(completion.getCheckedBy())),
                            completion == null ? null : completion.getCheckedAt());
                })
                .toList();
    }

    /** 항목 체크. 이미 체크된 항목이면 그대로 둔다 (멱등). */
    @Transactional
    public void check(Long storeId, Long itemId, Long userId, LocalDate date) {
        Membership me = storeAccess.requireMember(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        checklistCompletionRepository.insertIfAbsent(item.getId(), storeId, date, me.memberId(), LocalDateTime.now());
    }

    /** 체크 해제 (실수로 체크한 경우). */
    @Transactional
    public void uncheck(Long storeId, Long itemId, Long userId, LocalDate date) {
        storeAccess.requireMember(storeId, userId);
        checklistCompletionRepository.deleteByItemIdAndWorkDate(getItemInStore(storeId, itemId).getId(), date);
    }

    private ChecklistItem getItemInStore(Long storeId, Long itemId) {
        return checklistItemRepository.findByIdAndStoreId(itemId, storeId)
                .orElseThrow(() -> new NotFoundException("체크리스트 항목을 찾을 수 없습니다."));
    }
}
