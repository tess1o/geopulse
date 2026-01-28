package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;

@Data
public class UpdateSettingRequest {
    private String key;
    private String value;
}
