package com.tothework.operations.checklist;

import com.tothework.operations.api.Checklists;
import com.tothework.operations.api.DailyChecklistProgress;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ChecklistProgress implements Checklists {

    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistCompletionRepository checklistCompletionRepository;

    @Override
    public DailyChecklistProgress progressOn(Long storeId, LocalDate workDate) {
        List<ChecklistItem> items = checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId);
        Set<Long> checkedItemIds = checklistCompletionRepository.findAllByStoreIdAndWorkDate(storeId, workDate).stream()
                .map(ChecklistCompletion::getItemId)
                .collect(Collectors.toSet());
        List<ChecklistItem> open = ofType(items, ChecklistType.OPEN);
        List<ChecklistItem> close = ofType(items, ChecklistType.CLOSE);
        return new DailyChecklistProgress(countChecked(open, checkedItemIds), open.size(),
                countChecked(close, checkedItemIds), close.size());
    }

    private static List<ChecklistItem> ofType(List<ChecklistItem> items, ChecklistType type) {
        return items.stream().filter(item -> item.getType() == type).toList();
    }

    private static int countChecked(List<ChecklistItem> items, Set<Long> checkedItemIds) {
        return (int) items.stream().filter(item -> checkedItemIds.contains(item.getId())).count();
    }
}
