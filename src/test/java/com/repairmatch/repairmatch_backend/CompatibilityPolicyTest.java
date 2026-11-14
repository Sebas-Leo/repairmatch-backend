package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.service.CompatibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The single rule shared by the opportunity list and proposal submission. */
class CompatibilityPolicyTest {
    private static final double LAT = -12.0464;
    private static final double LON = -77.0428;

    private final CompatibilityPolicy policy = new CompatibilityPolicy();
    private final ApplianceType served = type(1L);
    private final ApplianceType other = type(2L);

    @Test
    void compatibleWhenTypeIsServedAndRequestIsInsideTheRadius() {
        Request request = request(served, LAT + 0.01, LON, Request.RequestStatus.PUBLICADA);
        assertThat(policy.matches(technician(5.0), request)).isTrue();
        assertThat(policy.rejectionReason(technician(5.0), request)).isEmpty();
    }

    @Test
    void radiusIsInclusiveAtTheExactDistance() {
        double distance = CompatibilityPolicy.distanceKm(LAT, LON, LAT + 0.02, LON);
        Request request = request(served, LAT + 0.02, LON, Request.RequestStatus.PUBLICADA);

        assertThat(policy.matches(technician(distance), request)).isTrue();
        assertThat(policy.matches(technician(Math.nextDown(distance)), request)).isFalse();
        assertThat(policy.rejectionReason(technician(Math.nextDown(distance)), request)).hasValueSatisfying(
                reason -> assertThat(reason).contains("service radius"));
    }

    @Test
    void rejectionReasonsAreSpecific() {
        Request wrongType = request(other, LAT, LON, Request.RequestStatus.PUBLICADA);
        Request noLocation = request(served, null, null, Request.RequestStatus.PUBLICADA);
        Technician unconfigured = new Technician(new User(UUID.randomUUID(), "T", "t@example.com", "x", Role.TECHNICIAN));
        unconfigured.setApplianceTypes(Set.of(served));

        assertThat(policy.rejectionReason(technician(5.0), wrongType)).hasValueSatisfying(r -> assertThat(r).contains("appliance type"));
        assertThat(policy.rejectionReason(technician(5.0), noLocation)).hasValueSatisfying(r -> assertThat(r).contains("no location"));
        assertThat(policy.rejectionReason(unconfigured, wrongType)).hasValueSatisfying(r -> assertThat(r).contains("not configured"));
    }

    @Test
    void onlyPublishedAndWithProposalsRequestsAreOpen() {
        for (Request.RequestStatus status : Request.RequestStatus.values()) {
            boolean open = status == Request.RequestStatus.PUBLICADA || status == Request.RequestStatus.CON_PROPUESTAS;
            assertThat(policy.matches(technician(5.0), request(served, LAT, LON, status))).as(status.name()).isEqualTo(open);
        }
    }

    private Technician technician(double radiusKm) {
        Technician technician = new Technician(new User(UUID.randomUUID(), "Tech", "tech@example.com", "x", Role.TECHNICIAN));
        technician.setLatitude(LAT);
        technician.setLongitude(LON);
        technician.setMaxRadiusKm(radiusKm);
        technician.setApplianceTypes(Set.of(served));
        return technician;
    }

    private Request request(ApplianceType type, Double latitude, Double longitude, Request.RequestStatus status) {
        return Request.builder().applianceType(type).latitude(latitude).longitude(longitude).status(status).build();
    }

    private static ApplianceType type(long id) {
        ApplianceType type = new ApplianceType();
        type.setId(id);
        type.setName("Type-" + id);
        return type;
    }
}
