/**
 * Weather display (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * The condition names are climate terms, not prose -- worth a second look, since Ukrainian
 * meteorology distinguishes мряка (drizzle) from злива (a downpour) and both from дощ (rain).
 * `rainySamples` and `samplesWithAverage` carry three plural forms (one / few / many).
 */
export default {
    conditions: {
        clear: 'Ясно',
        partlyCloudy: 'Мінлива хмарність',
        cloudy: 'Хмарно',
        fog: 'Туман',
        drizzle: 'Мряка',
        rain: 'Дощ',
        snow: 'Сніг',
        rainShowers: 'Злива',
        snowShowers: 'Снігопад',
        storm: 'Гроза',
        unknown: 'Погода'
    },
    summary: {
        temperatureLabel: 'Температура',
        rangeLabel: 'Діапазон',
        precipitationLabel: 'Опади',
        windLabel: 'Вітер',
        notAvailable: 'н/д',
        titleRange: 'діапазон {range}',
        titlePrecipitation: 'опади {precipitation}',
        titleWind: 'вітер {wind}'
    },
    insights: {
        title: 'Погода в дорозі',
        hottestMoment: 'Найспекотніший момент',
        coldestMoment: 'Найхолодніший момент',
        wettestDay: 'Найдощовіший день',
        mostCommon: 'Найпоширеніша погода',
        dateUnavailable: 'Дата недоступна',
        rainySamples: '{count} зразок з дощем | {count} зразки з дощем | {count} зразків з дощем',
        samplesWithAverage: '{count} зразок · Сер. {temperature} | {count} зразки · Сер. {temperature} | {count} зразків · Сер. {temperature}',
        maxWind: 'Макс. вітер {speed}'
    }
}
