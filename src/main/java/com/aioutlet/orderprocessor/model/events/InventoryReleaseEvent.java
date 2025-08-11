package com.aioutlet.orderprocessor.model.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published during compensation when inventory is released
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseEvent {
    private UUID orderId;
    private String reservationId;
    private String reason;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime releasedAt;
}
