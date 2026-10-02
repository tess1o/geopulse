/**
 * Weather display.
 *
 * `conditions.*` mirrors the WMO weather-code groups in `utils/weatherDisplay.js`; the backend sends
 * a locale-neutral `weatherCode`, so components translate by code and use the server's English
 * `label` only as a fallback -- the same key-based contract used for backend messages.
 */
export default {
    conditions: {
        clear: 'Clear',
        partlyCloudy: 'Partly cloudy',
        cloudy: 'Cloudy',
        fog: 'Fog',
        drizzle: 'Drizzle',
        rain: 'Rain',
        snow: 'Snow',
        rainShowers: 'Rain showers',
        snowShowers: 'Snow showers',
        storm: 'Storm',
        unknown: 'Weather'
    },
    summary: {
        temperatureLabel: 'Temperature',
        rangeLabel: 'Range',
        precipitationLabel: 'Precipitation',
        windLabel: 'Wind',
        notAvailable: 'n/a',
        titleRange: 'range {range}',
        titlePrecipitation: 'precipitation {precipitation}',
        titleWind: 'wind {wind}'
    },
    insights: {
        title: 'Weather Along the Way',
        hottestMoment: 'Hottest Moment',
        coldestMoment: 'Coldest Moment',
        wettestDay: 'Wettest Day',
        mostCommon: 'Most Common Weather',
        dateUnavailable: 'Date unavailable',
        rainySamples: '{count} rainy sample | {count} rainy samples',
        samplesWithAverage: '{count} sample · Avg {temperature} | {count} samples · Avg {temperature}',
        maxWind: 'Max wind {speed}'
    }
}
