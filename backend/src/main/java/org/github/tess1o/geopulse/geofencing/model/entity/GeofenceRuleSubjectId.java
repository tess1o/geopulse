package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceRuleSubjectId implements Serializable {

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "subject_user_id")
    private UUID subjectUserId;
}
