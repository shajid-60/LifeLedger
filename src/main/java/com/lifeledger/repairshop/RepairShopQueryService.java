package com.lifeledger.repairshop;

import java.util.Optional;

/**
 * Cross-module contract (M1 → M3): read-only questions about repair shops.
 * M3's ServiceRecordService codes against this interface; M1 provides the
 * implementation once the repair_shops table exists (Day 5).
 */
public interface RepairShopQueryService {

    /**
     * @return true only if the shop exists and its status is APPROVED
     */
    boolean isApproved(Long shopId);

    /**
     * @param userId users.id of the logged-in shop manager
     * @return the id of the APPROVED shop this user manages, or empty if none
     */
    Optional<Long> findApprovedShopIdByManager(Long userId);
}
