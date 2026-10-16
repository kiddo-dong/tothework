package com.tothework.workforce.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import com.tothework.shared.error.ConflictException;
import com.tothework.store.api.StoreAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 업종 프리셋의 근무 유형을 한 번에 넣는 경로. */
@ExtendWith(MockitoExtension.class)
class ShiftTemplateBulkServiceTest {

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;
    @Mock
    private StoreAccess storeAccess;

    @InjectMocks
    private ShiftTemplateService shiftTemplateService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(shiftTemplateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ShiftTemplateRequest template(String name, String start, String end) {
        return new ShiftTemplateRequest(name, LocalTime.parse(start), LocalTime.parse(end), 30, null);
    }

    private ShiftTemplateBulkRequest request(ShiftTemplateRequest... items) {
        return new ShiftTemplateBulkRequest(List.of(items));
    }

    @Test
    void numbersTemplatesInTheOrderTheyWereSent() {
        when(shiftTemplateRepository.findMaxDisplayOrder(STORE_ID)).thenReturn(null);

        List<ShiftTemplateResponse> saved = shiftTemplateService.createTemplates(STORE_ID, USER_ID,
                request(template("오픈", "08:00", "15:00"), template("미들", "12:00", "18:00"),
                        template("마감", "15:00", "22:00")));

        assertThat(saved).extracting(ShiftTemplateResponse::name)
                .containsExactly("오픈", "미들", "마감");
        assertThat(saved).extracting(ShiftTemplateResponse::displayOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void appendsAfterTemplatesTheStoreAlreadyHas() {
        when(shiftTemplateRepository.findMaxDisplayOrder(STORE_ID)).thenReturn(2);

        List<ShiftTemplateResponse> saved = shiftTemplateService.createTemplates(STORE_ID, USER_ID,
                request(template("야간", "22:00", "06:00")));

        assertThat(saved).extracting(ShiftTemplateResponse::displayOrder).containsExactly(3);
    }

    @Test
    void rejectsANameTheStoreIsAlreadyUsing() {
        when(shiftTemplateRepository.existsByStoreIdAndName(STORE_ID, "오픈")).thenReturn(true);

        assertThatThrownBy(() -> shiftTemplateService.createTemplates(STORE_ID, USER_ID,
                request(template("오픈", "08:00", "15:00"))))
                .isInstanceOf(ConflictException.class);

        verify(shiftTemplateRepository, never()).saveAll(any());
    }

    /** 배치 안의 중복은 DB에 아직 없어서 개별 조회로는 안 잡힌다. */
    @Test
    void rejectsADuplicateNameWithinTheSameRequest() {
        lenient().when(shiftTemplateRepository.existsByStoreIdAndName(any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> shiftTemplateService.createTemplates(STORE_ID, USER_ID,
                request(template("오픈", "08:00", "15:00"), template("오픈", "09:00", "16:00"))))
                .isInstanceOf(ConflictException.class);

        verify(shiftTemplateRepository, never()).saveAll(any());
    }

    /** 이름 확인을 저장 전에 모두 끝내야, 일부만 들어간 뒤 실패하는 상황이 없다. */
    @Test
    void checksEveryNameBeforeSavingAnything() {
        lenient().when(shiftTemplateRepository.findMaxDisplayOrder(STORE_ID)).thenReturn(null);
        when(shiftTemplateRepository.existsByStoreIdAndName(STORE_ID, "오픈")).thenReturn(false);
        when(shiftTemplateRepository.existsByStoreIdAndName(STORE_ID, "마감")).thenReturn(true);

        assertThatThrownBy(() -> shiftTemplateService.createTemplates(STORE_ID, USER_ID,
                request(template("오픈", "08:00", "15:00"), template("마감", "15:00", "22:00"))))
                .isInstanceOf(ConflictException.class);

        verify(shiftTemplateRepository, never()).saveAll(any());
    }
}
