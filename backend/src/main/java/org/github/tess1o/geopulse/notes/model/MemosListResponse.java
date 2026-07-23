package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemosListResponse {
    private List<MemosMemo> memos;
    private String nextPageToken;
}
