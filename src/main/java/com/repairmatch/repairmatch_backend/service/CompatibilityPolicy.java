package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Technician;
import org.springframework.stereotype.Component;

/** One eligibility rule shared by discovery and proposal submission. */
@Component
public class CompatibilityPolicy {
    public boolean matches(Technician technician, Request request) {
        return isOpen(request) && configured(technician)
                && request.getLatitude() != null && request.getLongitude() != null
                && request.getApplianceType() != null
                && technician.getApplianceTypes().stream().anyMatch(t -> t.getId().equals(request.getApplianceType().getId()))
                && distanceKm(technician.getLatitude(), technician.getLongitude(), request.getLatitude(), request.getLongitude())
                <= technician.getMaxRadiusKm();
    }

    public boolean configured(Technician t) {
        return t.getLatitude() != null && t.getLongitude() != null && t.getMaxRadiusKm() != null;
    }

    public static boolean isOpen(Request r) {
        return r.getStatus() == Request.RequestStatus.PUBLICADA || r.getStatus() == Request.RequestStatus.CON_PROPUESTAS;
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
    }
}
