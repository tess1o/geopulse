/**
 * Weather display.
 *
 * `conditions.*` mirrors the WMO weather-code groups in `utils/weatherDisplay.js`; the backend sends
 * a locale-neutral `weatherCode`, so components translate by code and use the server\'s English
 * `label` only as a fallback -- the same key-based contract used for backend messages.
 */
export default {
    conditions: {
        clear: 'Helder',
        partlyCloudy: 'Gedeeltelijk bewolkt',
        cloudy: 'Bewolkt',
        fog: 'Mist',
        drizzle: 'Motregen',
        rain: 'Regen',
        snow: 'Sneeuw',
        rainShowers: 'Regenbuien',
        snowShowers: 'Sneeuwbuien',
        storm: 'Storm',
        unknown: 'Weer'
    },
    summary: {
        temperatureLabel: 'Temperatuur',
        rangeLabel: 'Bereik',
        precipitationLabel: 'Neerslag',
        windLabel: 'Wind',
        notAvailable: 'nvt',
        titleRange: 'bereik {range}',
        titlePrecipitation: 'neerslag {precipitation}',
        titleWind: 'wind {wind}'
    },
    insights: {
        title: 'Weer Langs de Weg',
        hottestMoment: 'Heetste Moment',
        coldestMoment: 'Koudste Moment',
        wettestDay: 'Natste Dag',
        mostCommon: 'Meest Voorkomend Weer',
        dateUnavailable: 'Datum onbeschikbaar',
        rainySamples: '{count} regenachtig monster | {count} regenachtige monsters',
        samplesWithAverage: '{count} monster · Gem {temperature} | {count} monsters · Gem {temperature}',
        maxWind: 'Max wind {speed}'
    }
}

