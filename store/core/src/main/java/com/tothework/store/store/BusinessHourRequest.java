package com.tothework.store.store;

import java.time.LocalTime;

public record BusinessHourRequest(LocalTime openTime, LocalTime closeTime, boolean closed) {
}
