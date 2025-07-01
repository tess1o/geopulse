package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedLinksDto {

    @JsonProperty("links")
    private List<SharedLinkDto> links;

    @JsonProperty("active_count")
    private int activeCount;

    @JsonProperty("max_links")
    private int maxLinks;
}