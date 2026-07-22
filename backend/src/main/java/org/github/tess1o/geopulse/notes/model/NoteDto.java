package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoteDto {
    private Long id;
    private NoteSource source;
    private String externalId;
    private String externalUrl;
    private String title;
    private String contentMarkdown;
    private String snippet;
    private Instant eventTime;
    private Double latitude;
    private Double longitude;
    private NoteLocationSource locationSource;
    private NoteAnchorType anchorType;
    private Long anchorId;
    private Boolean editable;
    private Boolean truncated;
    private Instant createdAt;
    private Instant updatedAt;
}
