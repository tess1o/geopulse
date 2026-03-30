package org.github.tess1o.geopulse.home;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;

@RegisterForReflection(targets = {
        HomeContentResponse.class,
        HomeContentResponse.Tip.class,
        HomeContentResponse.WhatsNewItem.class,
        HomeContentResponse.TipLink.class,
        HomeContentResponse.Meta.class
})
public class HomeNativeConfig {
}
