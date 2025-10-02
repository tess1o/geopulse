package org.github.tess1o.geopulse.digest.service;

import org.github.tess1o.geopulse.digest.model.TimeDigest;

import java.util.UUID;

public interface DigestService {
    TimeDigest getMonthlyDigest(UUID userId, int year, int month);
    TimeDigest getYearlyDigest(UUID userId, int year);
}
