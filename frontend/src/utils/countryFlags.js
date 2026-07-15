const FLAG_COUNTRY_CODES = new Set(
  'ad ae af ag ai al am an ao aq ar as at au aw az ba bb bd be bf bg bh bi bj bm bn bo br bs bt bv bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cx cy cz de dj dk dm do dz ec ee eg eh er es et fi fj fk fm fo fr ga gb gd ge gf gh gi gl gm gn gp gq gr gs gt gu gw gy hk hm hn hr ht hu id ie il in io iq ir is it jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr ls lt lu lv ly ma mc md me mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr pt pw py qa re ro rs ru rw sa sb sc sd se sg sh si sj sk sl sm sn so sr ss st sv sy sz tc td tf tg th tj tk tl tm tn to tp tr tt tv tw ty tz ua ug uk um us uy uz va vc ve vg vi vn vu wf ws ye za zm zr zw'
    .split(' ')
)

const COMMON_DISPLAY_NAME_LOCALES = [
  'en',
  'fr',
  'de',
  'es',
  'pt',
  'it',
  'nl',
  'pl',
  'uk',
  'ru',
  'tr'
]

const COUNTRY_CODE_ALIASES = {
  'bolivia': 'bo',
  'bolivia plurinational state of': 'bo',
  'bolivarian republic of venezuela': 've',
  'brunei': 'bn',
  'brunei darussalam': 'bn',
  'burma': 'mm',
  'cabo verde': 'cv',
  'cape verde': 'cv',
  'congo brazzaville': 'cg',
  'congo kinshasa': 'cd',
  'cote d ivoire': 'ci',
  'czech republic': 'cz',
  'democratic people s republic of korea': 'kp',
  'democratic republic of congo': 'cd',
  'democratic republic of the congo': 'cd',
  'dr congo': 'cd',
  'drc': 'cd',
  'east timor': 'tl',
  'england': 'gb',
  'eswatini': 'sz',
  'federated states of micronesia': 'fm',
  'great britain': 'gb',
  'holy see': 'va',
  'iran': 'ir',
  'iran islamic republic of': 'ir',
  'islamic republic of iran': 'ir',
  'ivory coast': 'ci',
  'korea democratic people s republic of': 'kp',
  'korea republic of': 'kr',
  'lao people s democratic republic': 'la',
  'laos': 'la',
  'macao': 'mo',
  'macau': 'mo',
  'macedonia': 'mk',
  'micronesia': 'fm',
  'moldova': 'md',
  'moldova republic of': 'md',
  'myanmar': 'mm',
  'netherlands antilles': 'an',
  'north korea': 'kp',
  'northern ireland': 'gb',
  'plurinational state of bolivia': 'bo',
  'republic of congo': 'cg',
  'republic of cote d ivoire': 'ci',
  'republic of korea': 'kr',
  'republic of moldova': 'md',
  'republic of the congo': 'cg',
  'russia': 'ru',
  'russian federation': 'ru',
  'scotland': 'gb',
  'south korea': 'kr',
  'swaziland': 'sz',
  'syria': 'sy',
  'syrian arab republic': 'sy',
  'taiwan': 'tw',
  'tanzania': 'tz',
  'tanzania united republic of': 'tz',
  'timor leste': 'tl',
  'turkiye': 'tr',
  'u k': 'gb',
  'u s': 'us',
  'u s a': 'us',
  'uk': 'gb',
  'united kingdom': 'gb',
  'united republic of tanzania': 'tz',
  'united states': 'us',
  'united states of america': 'us',
  'us': 'us',
  'usa': 'us',
  'vatican city': 'va',
  'venezuela': 've',
  'venezuela bolivarian republic of': 've',
  'viet nam': 'vn',
  'vietnam': 'vn',
  'wales': 'gb'
}

let countryNameToFlagCode = null

export const normalizeCountryName = (value) => {
  if (!value) return ''

  return value
    .toString()
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/&/g, ' and ')
    .replace(/[^\p{Letter}\p{Number}]+/gu, ' ')
    .replace(/^the\s+/i, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
}

const getNavigatorLanguages = () => {
  if (typeof navigator === 'undefined' || !Array.isArray(navigator.languages)) {
    return []
  }

  return navigator.languages
}

const normalizeFlagCode = (code) => {
  const normalizedCode = code?.toString().trim().toLowerCase()
  if (!normalizedCode) return null
  if (normalizedCode === 'uk') return 'gb'
  return FLAG_COUNTRY_CODES.has(normalizedCode) ? normalizedCode : null
}

const setCountryName = (map, name, code, overwrite = false) => {
  const normalizedName = normalizeCountryName(name)
  const normalizedCode = normalizeFlagCode(code)

  if (!normalizedName || !normalizedCode) return
  if (overwrite || !map.has(normalizedName)) {
    map.set(normalizedName, normalizedCode)
  }
}

const addDisplayNames = (map) => {
  if (typeof Intl === 'undefined' || !Intl.DisplayNames) return

  const locales = [...new Set([...getNavigatorLanguages(), ...COMMON_DISPLAY_NAME_LOCALES])]

  locales.forEach((locale) => {
    let displayNames

    try {
      displayNames = new Intl.DisplayNames([locale], { type: 'region' })
    } catch {
      return
    }

    FLAG_COUNTRY_CODES.forEach((code) => {
      try {
        setCountryName(map, displayNames.of(code.toUpperCase()), code)
      } catch {
        // Some legacy sprite codes are no longer valid Intl regions.
      }
    })
  })
}

const getCountryNameToFlagCode = () => {
  if (countryNameToFlagCode) return countryNameToFlagCode

  countryNameToFlagCode = new Map()
  addDisplayNames(countryNameToFlagCode)

  Object.entries(COUNTRY_CODE_ALIASES).forEach(([name, code]) => {
    setCountryName(countryNameToFlagCode, name, code, true)
  })

  return countryNameToFlagCode
}

export const getCountryFlagCode = (countryName) => {
  const directCode = normalizeFlagCode(countryName)
  if (directCode) return directCode

  const normalizedName = normalizeCountryName(countryName)
  if (!normalizedName) return null

  return getCountryNameToFlagCode().get(normalizedName) || null
}

export const getCountryFlagClass = (countryName) => {
  const flagCode = getCountryFlagCode(countryName)
  return flagCode ? `flag-${flagCode}` : null
}
