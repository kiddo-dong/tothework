package com.tothework.workforce.shift;

import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StoreAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final StoreAccess storeAccess;

    @Transactional
    public ShiftTemplateResponse createTemplate(Long storeId, Long userId, ShiftTemplateRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        if (shiftTemplateRepository.existsByStoreIdAndName(storeId, request.name())) {
            throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + request.name());
        }
        return ShiftTemplateResponse.from(shiftTemplateRepository.save(ShiftTemplate.create(storeId, request.name(),
                request.startTime(), request.endTime(), request.breakMinutes(), request.displayOrderOrDefault())));
    }

    /**
     * 여러 근무 유형을 한 번에 등록한다. 하나라도 어긋나면 전부 되돌아간다.
     *
     * <p>이름 중복은 저장 전에 모두 확인한다. 하나씩 저장하며 보면 앞의 몇 개가 들어간 뒤에 실패해, 어디까지 유효했는지
     * 알려 주지 못한다. 배치 안에서 이름이 겹치는 경우도 같이 거른다 — DB에는 아직 없어 개별 조회로는 안 잡힌다.
     */
    @Transactional
    public List<ShiftTemplateResponse> createTemplates(Long storeId, Long userId, ShiftTemplateBulkRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Set<String> namesInBatch = new HashSet<>();
        for (ShiftTemplateRequest item : request.items()) {
            if (!namesInBatch.add(item.name())) {
                throw new ConflictException("요청 안에 같은 이름이 두 번 있습니다: " + item.name());
            }
            if (shiftTemplateRepository.existsByStoreIdAndName(storeId, item.name())) {
                throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + item.name());
            }
        }
        Integer maxOrder = shiftTemplateRepository.findMaxDisplayOrder(storeId);
        int nextOrder = (maxOrder == null ? -1 : maxOrder) + 1;
        List<ShiftTemplate> templates = new ArrayList<>();
        for (ShiftTemplateRequest item : request.items()) {
            templates.add(ShiftTemplate.create(storeId, item.name(), item.startTime(), item.endTime(),
                    item.breakMinutes(), nextOrder++));
        }
        return shiftTemplateRepository.saveAll(templates).stream().map(ShiftTemplateResponse::from).toList();
    }

    public List<ShiftTemplateResponse> getTemplates(Long storeId, Long userId) {
        storeAccess.requireMember(storeId, userId);
        return shiftTemplateRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(ShiftTemplateResponse::from)
                .toList();
    }

    @Transactional
    public ShiftTemplateResponse updateTemplate(Long storeId, Long templateId, Long userId,
            ShiftTemplateRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        ShiftTemplate template = getTemplateInStore(storeId, templateId);
        if (shiftTemplateRepository.existsByStoreIdAndNameAndIdNot(storeId, request.name(), templateId)) {
            throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + request.name());
        }
        template.update(request.name(), request.startTime(), request.endTime(), request.breakMinutes(),
                request.displayOrderOrDefault());
        return ShiftTemplateResponse.from(shiftTemplateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long storeId, Long templateId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        shiftTemplateRepository.delete(getTemplateInStore(storeId, templateId));
    }

    private ShiftTemplate getTemplateInStore(Long storeId, Long templateId) {
        return shiftTemplateRepository.findByIdAndStoreId(templateId, storeId)
                .orElseThrow(() -> new NotFoundException("시프트 템플릿을 찾을 수 없습니다."));
    }
}
