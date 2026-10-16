package com.tothework.operations.api;

import java.time.LocalDate;
import java.util.List;

/** 인수인계 노트 조회. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface HandoverNotes {

    /** 기간 내 노트를 최신순으로 최대 limit개. */
    List<HandoverNoteResponse> findRecent(Long storeId, LocalDate from, LocalDate to, int limit);
}
