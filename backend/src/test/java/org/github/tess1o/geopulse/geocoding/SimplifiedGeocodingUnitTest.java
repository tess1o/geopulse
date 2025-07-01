package org.github.tess1o.geopulse.geocoding;

import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddress;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddressFormatter;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the simplified geocoding approach focusing on address formatting.
 * This test doesn't require Docker/Testcontainers.
 */
class SimplifiedGeocodingUnitTest {

    private NominatimAddressFormatter addressFormatter;

    @BeforeEach
    void setUp() {
        addressFormatter = new NominatimAddressFormatter();
    }

    @Test
    void testFormatAddressWithNameAndStreet() {
        // Given: complete Nominatim response with name and street
        NominatimResponse response = createMockNominatimResponse();
        
        // When: format the address
        String formatted = addressFormatter.formatAddress(response, -73.9857, 40.7484);
        
        // Then: should format as "Name (Street)"
        assertEquals("Times Square (Broadway)", formatted);
    }
    
    @Test
    void testFormatAddressWithNameAndStreetWithHouseNumber() {
        // Given: Nominatim response with house number
        NominatimResponse response = createMockNominatimResponse();
        response.getAddress().setHouseNumber("1234");
        
        // When: format the address
        String formatted = addressFormatter.formatAddress(response, -73.9857, 40.7484);
        
        // Then: should include house number
        assertEquals("Times Square (Broadway 1234)", formatted);
    }
    
    @Test
    void testFormatAddressWithDisplayNameFallback() {
        // Given: Nominatim response with no road but display name
        NominatimResponse response = createMockNominatimResponse();
        response.getAddress().setRoad(null);
        response.setDisplayName("Times Square, Broadway, Manhattan, New York County, New York, 10036, United States");
        
        // When: format the address
        String formatted = addressFormatter.formatAddress(response, -73.9857, 40.7484);
        
        // Then: should use display name
        assertEquals("Times Square, Broadway, Manhattan, New York County, New York, 10036, United States", formatted);
    }
    
    @Test
    void testFormatAddressNoName() {
        // Given: Nominatim response with only street, no name
        NominatimResponse response = createMockNominatimResponse();
        response.setName(null);
        
        // When: format the address
        String formatted = addressFormatter.formatAddress(response, -73.9857, 40.7484);
        
        // Then: should return just the street
        assertEquals("Broadway", formatted);
    }
    
    @Test
    void testFormatAddressNotFound() {
        // When: format null response
        String formatted = addressFormatter.formatAddress(null, -73.9857, 40.7484);
        
        // Then: should return not found message with coordinates
        assertEquals("Address not found (-73.9857, 40.7484)", formatted);
    }
    
    @Test
    void testNotFoundAddress() {
        // When: call notFoundAddress directly
        String notFound = addressFormatter.notFoundAddress(-73.9857, 40.7484);
        
        // Then: should format with coordinates
        assertEquals("Address not found (-73.9857, 40.7484)", notFound);
    }
    
    @Test
    void testFormatAddressWithBlankName() {
        // Given: Nominatim response with blank name
        NominatimResponse response = createMockNominatimResponse();
        response.setName("   "); // blank name
        
        // When: format the address
        String formatted = addressFormatter.formatAddress(response, -73.9857, 40.7484);
        
        // Then: should return just the street
        assertEquals("Broadway", formatted);
    }
    
    /**
     * Create a mock Nominatim response for testing.
     */
    private NominatimResponse createMockNominatimResponse() {
        NominatimResponse response = new NominatimResponse();
        response.setName("Times Square");
        response.setDisplayName("Times Square, Broadway, Manhattan, New York County, New York, 10036, United States");
        response.setLat("40.7484");
        response.setLon("-73.9857");
        response.setPlaceId(123456);
        response.setOsmType("node");
        response.setOsmId(123456L);
        response.setLicence("Data © OpenStreetMap contributors, ODbL 1.0. https://osm.org/copyright");
        
        NominatimAddress address = new NominatimAddress();
        address.setRoad("Broadway");
        address.setCity("New York");
        address.setState("New York");
        address.setCountry("United States");
        address.setPostcode("10036");
        
        response.setAddress(address);
        
        return response;
    }
}