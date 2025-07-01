package org.github.tess1o.geopulse.geocoding.model.nominatim;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nominatim address component from geocoding API response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NominatimAddress {
	private String country;
	@JsonProperty("country_code")
	private String countryCode;
	private String road;
	private String town;
	private String city;
	private String district;
	@JsonProperty("ISO3166-2-lvl4")
	private String iSO31662Lvl4;
	private String municipality;
	private String postcode;
	private String suburb;
	@JsonProperty("house_number")
	private String houseNumber;
	private String state;
	private String quarter;
}