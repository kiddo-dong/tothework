package com.tothework.workforce.employment;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.DayOfWeek;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = WorkforceSchema.NAME, name = "employment_available_days")
record AvailableDay(DayOfWeek dayOfWeek) {
}
