package org.github.tess1o.geopulse.geofencing.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class NotificationDestinationParserTest {

    @Test
    void shouldParseMultilineDestinations() {
        List<String> urls = NotificationDestinationParser.parseUrls(" tgram://token1 \n\n discord://token2 ");
        assertThat(urls).containsExactly("tgram://token1", "discord://token2");
    }

    @Test
    void shouldRejectCommaSeparatedDestinations() {
        assertThatThrownBy(() -> NotificationDestinationParser.parseUrls("tgram://token,discord://token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one destination per line");
    }

    @Test
    void shouldNormalizeToNewlineSeparatedStorageValue() {
        String normalized = NotificationDestinationParser.normalize(" tgram://token1 \n discord://token2 ");
        assertThat(normalized).isEqualTo("tgram://token1\ndiscord://token2");
    }
}
