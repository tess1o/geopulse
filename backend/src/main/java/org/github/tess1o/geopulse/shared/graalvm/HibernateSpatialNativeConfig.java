package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.geolatte.geom.codec.PostgisWkbDecoder;
import org.geolatte.geom.codec.PostgisWkbEncoder;
import org.geolatte.geom.codec.PostgisWkbV2Encoder;
import org.locationtech.jts.geom.*;

@RegisterForReflection(targets = {
        Coordinate.class,
        Envelope.class,
        Geometry.class,
        GeometryFactory.class,
        LineString.class,
        Point.class,
        Polygon.class,
        PostgisWkbDecoder.class,
        PostgisWkbEncoder.class,
        PostgisWkbV2Encoder.class,
})
public class HibernateSpatialNativeConfig {
}
