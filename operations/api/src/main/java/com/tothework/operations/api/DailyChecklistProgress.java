package com.tothework.operations.api;

/** 하루치 오픈·마감 체크리스트의 완료 항목 수와 전체 항목 수. */
public record DailyChecklistProgress(int openDone, int openTotal, int closeDone, int closeTotal) {
}
