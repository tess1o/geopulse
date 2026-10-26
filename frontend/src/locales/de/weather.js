/**
 * Weather display (German). `conditions.*` mirrors the WMO weather-code groups in
 * `utils/weatherDisplay.js`.
 */
export default {
    conditions: {
        clear: 'Klar',
        partlyCloudy: 'Teilweise bewölkt',
        cloudy: 'Bewölkt',
        fog: 'Nebel',
        drizzle: 'Nieselregen',
        rain: 'Regen',
        snow: 'Schnee',
        rainShowers: 'Regenschauer',
        snowShowers: 'Schneeschauer',
        storm: 'Gewitter',
        unknown: 'Wetter'
    },
    summary: {
        temperatureLabel: 'Temperatur',
        rangeLabel: 'Spanne',
        precipitationLabel: 'Niederschlag',
        windLabel: 'Wind',
        notAvailable: 'k. A.',
        titleRange: 'Spanne {range}',
        titlePrecipitation: 'Niederschlag {precipitation}',
        titleWind: 'Wind {wind}'
    },
    insights: {
        title: 'Wetter unterwegs',
        hottestMoment: 'Wärmster Moment',
        coldestMoment: 'Kältester Moment',
        wettestDay: 'Nassester Tag',
        mostCommon: 'Häufigstes Wetter',
        dateUnavailable: 'Datum nicht verfügbar',
        rainySamples: '{count} Messwert mit Regen | {count} Messwerte mit Regen',
        samplesWithAverage: '{count} Messwert · Ø {temperature} | {count} Messwerte · Ø {temperature}',
        maxWind: 'Max. Wind {speed}'
    }
}
