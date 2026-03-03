-- GeoNames dataset cache used for city-name normalization and matching
-- Source: https://download.geonames.org/export/dump/cities500.zip

CREATE TABLE IF NOT EXISTS geonames_city (
    geonameid BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    asciiname VARCHAR(200),
    alternatenames TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    feature_class CHAR(1),
    feature_code VARCHAR(10),
    country_code CHAR(2),
    cc2 VARCHAR(200),
    admin1_code VARCHAR(20),
    admin2_code VARCHAR(80),
    admin3_code VARCHAR(20),
    admin4_code VARCHAR(20),
    population BIGINT,
    elevation INTEGER,
    dem INTEGER,
    timezone VARCHAR(40),
    modification_date DATE
);

CREATE INDEX IF NOT EXISTS idx_geonames_city_country_code
    ON geonames_city (country_code);

CREATE INDEX IF NOT EXISTS idx_geonames_city_name_lower
    ON geonames_city (LOWER(name));

CREATE INDEX IF NOT EXISTS idx_geonames_city_asciiname_lower
    ON geonames_city (LOWER(asciiname));

CREATE INDEX IF NOT EXISTS idx_geonames_city_lat_lon
    ON geonames_city (latitude, longitude);
