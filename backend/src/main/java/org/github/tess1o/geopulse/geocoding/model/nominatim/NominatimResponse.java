package org.github.tess1o.geopulse.geocoding.model.nominatim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nominatim (OpenStreetMap) geocoding API response format.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NominatimResponse {
	@JsonProperty("osm_id")
	private long osmId;
	@JsonProperty("place_rank")
	private int placeRank;
	private String licence;
	private List<Double> boundingbox;
	private NominatimAddress address;
	private String lon;
	private String type;
	@JsonProperty("display_name")
	private String displayName;
	@JsonProperty("osm_type")
	private String osmType;
	private String name;
	private String addresstype;
	private String jsonMemberClass;
	@JsonProperty("place_id")
	private int placeId;
	private String lat;
}