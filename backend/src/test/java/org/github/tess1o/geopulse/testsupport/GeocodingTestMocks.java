package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.junit.QuarkusMock;
import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.locationtech.jts.geom.Point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class GeocodingTestMocks {

    private GeocodingTestMocks() {
    }

    public static GeocodingProviderFactory install() {
        GeocodingProviderFactory provider = mock(GeocodingProviderFactory.class);
        when(provider.reverseGeocode(any(Point.class)))
                .thenAnswer(invocation -> Uni.createFrom().item(result(invocation.getArgument(0))));
        QuarkusMock.installMockForType(provider, GeocodingProviderFactory.class);
        return provider;
    }

    public static void failWith(GeocodingProviderFactory provider, RuntimeException failure) {
        doReturn(Uni.createFrom().failure(failure)).when(provider).reverseGeocode(any(Point.class));
    }

    public static SimpleFormattableResult result(Point point) {
        return SimpleFormattableResult.builder()
                .requestCoordinates(point)
                .resultCoordinates(point)
                .formattedDisplayName("Kyiv, Ukraine")
                .providerName("test-provider")
                .city("Kyiv")
                .country("Ukraine")
                .build();
    }
}
