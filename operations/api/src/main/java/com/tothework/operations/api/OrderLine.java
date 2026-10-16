package com.tothework.operations.api;

/** 특정 요일에 발주할 품목 한 줄. */
public record OrderLine(String supplierName, String itemName, String spec, String quantity) {
}
