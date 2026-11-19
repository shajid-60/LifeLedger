package com.lifeledger.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Cross-module contract (Observer pattern). M2 and M3 services publish this through
 * {@code ApplicationEventPublisher.publishEvent(...)}; M1's AuditEventListener stores it
 * in audit_events. Publishers never write to audit_events themselves.
 *
 * <pre>{@code
 * events.publishEvent(AuditableEvent.of(userId, "TRANSFER_COMPLETED", "OwnershipTransfer", transfer.getId(),
 *         Map.of("productId", productId, "toUserId", toUserId)));
 * }</pre>
 *
 * @param actorId    users.id of who did it; null for system actions
 * @param action     UPPER_SNAKE_CASE verb, e.g. PRODUCT_REGISTERED, SERVICE_RECORD_VOIDED
 * @param entityType simple class name of the changed entity, e.g. "Product"
 * @param entityId   id of the changed entity
 * @param details    extra JSON-serialisable data, stored as JSONB
 * @param occurredAt when it happened
 */
public record AuditableEvent(
        Long actorId,
        String action,
        String entityType,
        Long entityId,
        Map<String, Object> details,
        Instant occurredAt) {

    public AuditableEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditableEvent of(Long actorId, String action, String entityType, Long entityId,
                                    Map<String, Object> details) {
        return new AuditableEvent(actorId, action, entityType, entityId, details, Instant.now());
    }
}
