package org.github.tess1o.geopulse.poi;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.poi.client.commons.CommonsImageInfoResponse;
import org.github.tess1o.geopulse.poi.client.wikidata.SparqlResponse;
import org.github.tess1o.geopulse.poi.dto.PoiDto;
import org.github.tess1o.geopulse.poi.dto.PoiSearchResponseDto;
import org.github.tess1o.geopulse.poi.model.entity.PoiAreaQueryEntity;
import org.github.tess1o.geopulse.poi.model.entity.PoiCacheEntity;
import org.github.tess1o.geopulse.poi.model.entity.PoiImageCacheEntity;

@RegisterForReflection(targets = {
        // Entities
        PoiCacheEntity.class,
        PoiAreaQueryEntity.class,
        PoiImageCacheEntity.class,

        // DTOs
        PoiDto.class,
        PoiSearchResponseDto.class,

        // Provider response models (Jackson)
        SparqlResponse.class,
        SparqlResponse.Results.class,
        SparqlResponse.Binding.class,
        CommonsImageInfoResponse.class,
        CommonsImageInfoResponse.Query.class,
        CommonsImageInfoResponse.Page.class,
        CommonsImageInfoResponse.ImageInfo.class,
        CommonsImageInfoResponse.MetaValue.class,
})
public class PoiNativeConfig {
}
