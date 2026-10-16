package com.tothework.operations.api;

import java.time.LocalDate;

/** 체크리스트 진행 상황. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface Checklists {

    DailyChecklistProgress progressOn(Long storeId, LocalDate workDate);
}
