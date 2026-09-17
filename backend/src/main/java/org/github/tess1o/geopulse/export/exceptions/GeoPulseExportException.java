package org.github.tess1o.geopulse.export.exceptions;

import java.io.IOException;

public class GeoPulseExportException extends RuntimeException {
    public GeoPulseExportException(String s, IOException e) {
        super(s, e);
    }
}
