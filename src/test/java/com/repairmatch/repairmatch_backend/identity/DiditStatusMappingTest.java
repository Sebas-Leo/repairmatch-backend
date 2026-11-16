package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.model.IdentityStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiditStatusMappingTest {

    @Test
    void mapsEveryDocumentedDiditStatus() {
        assertEquals(Optional.of(IdentityStatus.VERIFIED), IdentityService.map("Approved"));
        assertEquals(Optional.of(IdentityStatus.REJECTED), IdentityService.map("Declined"));
        for (String pending : new String[]{"Not Started", "In Progress", "In Review", "Resubmitted", "Awaiting User"}) {
            assertEquals(Optional.of(IdentityStatus.PENDING), IdentityService.map(pending), pending);
        }
        for (String restart : new String[]{"Expired", "Abandoned", "Kyc Expired"}) {
            assertEquals(Optional.of(IdentityStatus.NOT_STARTED), IdentityService.map(restart), restart);
        }
    }

    @Test
    void ignoresUnknownOrDifferentlyCasedStatuses() {
        assertEquals(Optional.empty(), IdentityService.map("approved"));
        assertEquals(Optional.empty(), IdentityService.map("Something New"));
        assertEquals(Optional.empty(), IdentityService.map(null));
    }
}
