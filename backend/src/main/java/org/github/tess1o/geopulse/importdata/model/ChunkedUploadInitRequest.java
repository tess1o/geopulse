package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;

/**
 * Request DTO for initializing a chunked upload session.
 */
@Data
public class ChunkedUploadInitRequest {
    private String fileName;
    private long fileSize;
    private String importFormat;
    private ImportOptions options;  // Optional; defaults apply when omitted
}
