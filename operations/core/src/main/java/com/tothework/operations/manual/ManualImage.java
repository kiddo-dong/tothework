package com.tothework.operations.manual;

import com.tothework.operations.config.OperationsSchema;
import org.springframework.data.relational.core.mapping.Table;

/** 매뉴얼 본문 이미지 한 장의 S3 key (전체 URL이 아님). 순서는 매뉴얼의 목록 순서(sort_order)다. */
@Table(schema = OperationsSchema.NAME, name = "manual_images")
record ManualImage(String imageKey) {
}
