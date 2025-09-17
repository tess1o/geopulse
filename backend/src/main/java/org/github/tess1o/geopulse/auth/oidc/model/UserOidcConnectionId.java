package org.github.tess1o.geopulse.auth.oidc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserOidcConnectionId implements Serializable {
    private UUID userId;
    private String providerName;
}