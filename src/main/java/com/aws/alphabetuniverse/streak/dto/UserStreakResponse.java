package com.aws.alphabetuniverse.streak.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Streak của người dùng hiện tại")
public record UserStreakResponse(

        @Schema(description = "Số ngày streak còn hiệu lực")
        int currentStreak,

        @Schema(description = "Ngày gần nhất ghi nhận streak")
        LocalDate lastStreakDate,

        @Schema(description = "Hôm nay đã hoàn thành hoạt động đủ điều kiện")
        boolean completedToday,

        @Schema(description = "Ngày hiện tại theo múi giờ nghiệp vụ")
        LocalDate today,

        @Schema(description = "Múi giờ nghiệp vụ")
        String timeZone

) {
}