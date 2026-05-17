package org.github.tess1o.geopulse.auth.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MobileAuthInitResponse {
    private String code;
    private String deeplinkUrl;
}
