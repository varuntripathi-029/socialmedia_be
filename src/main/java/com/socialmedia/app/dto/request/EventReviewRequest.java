package com.socialmedia.app.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventReviewRequest {

    @NotNull(message = "Stars cannot be null")
    @Min(value = 0, message = "Stars must be at least 0")
    @Max(value = 5, message = "Stars cannot be more than 5")
    private Integer stars;

    private String reviewText;
}
