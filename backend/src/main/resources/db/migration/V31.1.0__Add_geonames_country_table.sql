-- GeoNames country cache used to resolve ISO country code -> country name
-- Source: https://download.geonames.org/export/dump/countryInfo.txt

CREATE TABLE IF NOT EXISTS geonames_country (
    iso_alpha2 CHAR(2) PRIMARY KEY,
    iso_alpha3 CHAR(3),
    iso_numeric INTEGER,
    fips_code VARCHAR(10),
    country_name VARCHAR(200) NOT NULL,
    capital VARCHAR(200),
    area_sq_km DOUBLE PRECISION,
    population BIGINT,
    continent CHAR(2),
    tld VARCHAR(10),
    currency_code VARCHAR(3),
    currency_name VARCHAR(80),
    phone VARCHAR(32),
    postal_code_format VARCHAR(200),
    postal_code_regex VARCHAR(255),
    languages VARCHAR(500),
    geonameid BIGINT,
    neighbors VARCHAR(200),
    equivalent_fips_code VARCHAR(10)
);

CREATE INDEX IF NOT EXISTS idx_geonames_country_name_lower
    ON geonames_country (LOWER(country_name));

CREATE INDEX IF NOT EXISTS idx_geonames_country_iso_alpha3
    ON geonames_country (iso_alpha3);

CREATE INDEX IF NOT EXISTS idx_geonames_country_geonameid
    ON geonames_country (geonameid);
