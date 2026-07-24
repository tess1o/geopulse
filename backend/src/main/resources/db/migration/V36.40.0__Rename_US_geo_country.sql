UPDATE geonames_country
SET country_name = 'United States of America'
WHERE iso_alpha2 = 'US'
  AND country_name = 'United States';
