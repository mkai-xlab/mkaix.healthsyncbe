package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatDto {
    private String date; // Format: YYYY-MM-DD
    private long count;
}
