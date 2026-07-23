package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemosMemo {
    private String name;
    private String uid;
    private String content;
    private String visibility;
    private Instant createTime;
    private Instant updateTime;
    private String snippet;
    private List<String> tags;
    private MemosLocation location;
}
