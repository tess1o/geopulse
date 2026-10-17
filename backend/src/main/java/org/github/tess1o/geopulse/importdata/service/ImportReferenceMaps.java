package org.github.tess1o.geopulse.importdata.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Old-to-new id mappings collected while one GeoPulse ZIP is imported, so later files can link to rows imported
 * (or matched) from earlier ones. Snapshot restores keep ids, so their mappings are identities.
 */
final class ImportReferenceMaps {

    final Map<Long, Long> timelineLabelIds = new HashMap<>();
    final Map<Long, Long> favoriteIds = new HashMap<>();
    final Map<Long, Long> geocodingIds = new HashMap<>();
    final Map<Long, Long> timelineStayIds = new HashMap<>();
    final Map<Long, Long> timelineTripIds = new HashMap<>();
    final Map<Long, Long> timelineDataGapIds = new HashMap<>();
    final Map<Long, Long> tripIds = new HashMap<>();
    final Map<Long, Long> notificationTemplateIds = new HashMap<>();
}
