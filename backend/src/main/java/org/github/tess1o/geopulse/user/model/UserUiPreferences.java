package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * App-wide UI preferences, stored as the {@code users.ui_preferences} JSONB document.
 *
 * <p>The same type is the stored value, the PATCH body (inside {@link UpdateProfileRequest}) and the response
 * payload, so adding a preference means adding a field here and, if it has one, a default in
 * {@link #withDefaults()}. Only values the user has set are stored; {@code null} means "use the default".</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserUiPreferences implements Serializable {

    @Schema(examples = "KILOMETERS")
    private DistanceUnit distanceUnit;

    @Schema(examples = "CELSIUS")
    private TemperatureUnit temperatureUnit;

    /** Allowed values: MDY, DMY, YMD. Null lets the client pick its default. */
    @Pattern(regexp = "^(MDY|DMY|YMD)$", message = "Date format must be one of: MDY, DMY, YMD")
    @Schema(examples = "DMY")
    private String dateFormat;

    @Pattern(regexp = "^(24h|12h)$", message = "Time format must be one of: 24h, 12h")
    @Schema(examples = "24h")
    private String timeFormat;

    /**
     * BCP 47 language subtag for the UI (see {@link SupportedLanguages}).
     *
     * <p>Stored server-side but applied client-side, so the backend never formats anything with it.
     */
    @Pattern(regexp = SupportedLanguages.PATTERN, message = "Language must be one of: en, uk")
    @Size(max = 16, message = "Language cannot exceed 16 characters")
    @Schema(examples = "en")
    private String language;

    /** Internal path to open after login or when navigating to {@code /}. Empty string resets it. */
    @Size(max = 1000, message = "Default redirect URL cannot exceed 1000 characters")
    @Schema(examples = "/app/timeline")
    private String defaultRedirectUrl;

    /** A copy with every unset preference replaced by its application default. */
    public UserUiPreferences withDefaults() {
        return toBuilder()
                .distanceUnit(distanceUnit != null ? distanceUnit : DistanceUnit.KILOMETERS)
                .temperatureUnit(temperatureUnit != null ? temperatureUnit : TemperatureUnit.CELSIUS)
                .timeFormat(timeFormat != null ? timeFormat : "24h")
                .language(SupportedLanguages.normalizeOrDefault(language))
                .build();
    }
}
