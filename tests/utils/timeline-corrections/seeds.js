import { TestSetupHelper } from '../test-setup-helper.js';
import { at, places, iso, staySpec, tripSpec } from './helpers.js';

// York points and speeds quoted in #666; all context, date and correction boundaries are synthetic.
// Velocities in gps_points are km/h, so the reported m/s values are multiplied by 3.6.
export const yorkPlaces = {
  'Cycle home': [53.95, -1.11],
  "King's Street Cycle Parking": [53.95743962728902, -1.082041520721363],
  'Santander Bank': [53.958055342331036, -1.0817396989075032],
  Tesco: [53.957545403109926, -1.0827279701131602],
};
export const yorkReportedPoints = [
  ['10:42:51', 53.957474, -1.082013, null], ['10:44:25', 53.958068, -1.081741, .06],
  ['10:45:31', 53.958059, -1.081725, .04], ['10:46:33', 53.957970, -1.081822, 0],
  ['10:47:18', 53.957589, -1.082675, null], ['10:48:28', 53.957530, -1.082639, .12],
  ['10:49:30', 53.957555, -1.082660, null], ['10:50:18', 53.957427, -1.082025, null],
  ['10:51:17', 53.957517, -1.081935, .16], ['10:51:26', 53.957524, -1.082965, 6.29],
  ['10:51:31', 53.957435, -1.083497, 6.78],
];
const yAt = t => `2026-09-15T${t}.000Z`;
export const yorkRecipe = async ({ db, user, favorites, point, stay, drive }) => {
  for (const [name, [latitude, longitude]] of Object.entries(yorkPlaces))
    favorites[name] = await TestSetupHelper.createFavoritePoint(db, user, { name, latitude, longitude });
  stay(yorkPlaces['Cycle home'], at('09:00'), at('10:00'));
  // Synthetic approach begins outside the home radius at 10:01; an origin duplicate would
  // keep the detector at home until 10:03 and break the declared nine-activity oracle.
  for (let i = 0; i <= 40; i++) {
    const ratio = .1 + .9 * i / 40;
    const from = yorkPlaces['Cycle home'], to = yorkPlaces["King's Street Cycle Parking"];
    point([from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio],
      iso(Date.parse(at('10:01')) + i * 60000), 45);
  }
  for (const [time, lat, lon, speed] of yorkReportedPoints) point([lat, lon], yAt(time), speed === null ? 0 : speed * 3.6);
  drive(yorkPlaces["King's Street Cycle Parking"], yorkPlaces['Cycle home'], at('10:52'), at('11:30'));
  stay(yorkPlaces['Cycle home'], at('11:31'), at('13:00'));
};
const parking = "King's Street Cycle Parking";
export const yorkActivities = [
  { type: 'trip', start: yAt('10:01:00'), end: yAt('10:42:51'), from: 'Cycle home', to: parking, movement: 'Bicycle' },
  { type: 'stay', start: yAt('10:42:51'), end: yAt('10:43:00'), name: parking },
  { type: 'trip', start: yAt('10:43:00'), end: yAt('10:44:25'), from: parking, to: 'Santander Bank', movement: 'Walk' },
  { type: 'stay', start: yAt('10:44:25'), end: yAt('10:46:33'), name: 'Santander Bank' },
  { type: 'trip', start: yAt('10:46:33'), end: yAt('10:47:18'), from: 'Santander Bank', to: 'Tesco', movement: 'Walk' },
  { type: 'stay', start: yAt('10:47:18'), end: yAt('10:49:30'), name: 'Tesco' },
  { type: 'trip', start: yAt('10:49:30'), end: yAt('10:50:18'), from: 'Tesco', to: parking, movement: 'Walk' },
  { type: 'stay', start: yAt('10:50:18'), end: yAt('10:51:26'), name: parking },
  { type: 'trip', start: yAt('10:51:26'), end: yAt('11:31:00'), from: parking, to: 'Cycle home', movement: 'Bicycle' },
];
export const yorkOracle = yorkActivities.map(a => ({ type: a.type, start: a.start, finish: a.end,
  ...(a.type === 'stay' ? { name: a.name } : { movement: a.movement.toUpperCase(), source: 'MANUAL' }) }));
export const threeTripsRecipe = ({ stay, drive }) => {
  stay(places.Home, at('06:00'), at('08:00'));
  drive(places.Home, places.Office, at('08:01'), at('09:00'));
  stay(places.Office, at('09:01'), at('10:00'));
  drive(places.Office, places.Home, at('10:01'), at('11:00'));
  stay(places.Home, at('11:01'), at('12:00'));
  drive(places.Home, places.Office, at('12:01'), at('13:00'));
  stay(places.Office, at('13:01'), at('14:00'));
};
export const holeSeed = {
  preferences: { dataGapThresholdSeconds: 10800 },
  recipe: ({ stay, point }) => {
    stay(places.Home, at('06:00'), at('07:00'));
    point([49.84, 24.04], at('07:01'), 45);
    point([49.836, 24.065], at('08:31'), 45);
    stay(places.Office, at('08:32'), at('10:00'));
  },
};
export const prefixSeed = {
  recipe: ({ stay, drive }) => {
    stay(places.Home, at('06:00'), at('07:00'));
    drive([49.839, 24.053], places.Office, at('07:30'), at('07:59'));
    stay(places.Office, at('08:00'), at('10:00'));
  },
};
export const berlinSeed = {
  timezone: 'Europe/Berlin', date: '2026-10-07', endDate: '2026-10-11',
  recipe: ({ stay, point }) => {
    stay(places.Home, '2026-10-07T16:00:23Z', '2026-10-07T17:11:23Z');
    stay(places.Office, '2026-10-08T01:00:07Z', '2026-10-08T17:00:07Z');
    point(places.Office, '2026-10-08T17:00:28Z');
    for (let i = 1; i <= 22; i++) point([
      places.Office[0] + (places.Home[0] - places.Office[0]) * i / 24,
      places.Office[1] + (places.Home[1] - places.Office[1]) * i / 24,
    ], iso(Date.parse('2026-10-08T17:00:58Z') + (i - 1) * 60000), 5);
    stay(places.Home, '2026-10-08T17:22:56Z', '2026-10-10T09:00:30Z');
    point(places.Home, '2026-10-10T09:00:30Z');
    point([49.841, 24.034], '2026-10-10T09:00:31Z', 45);
    point(places.Office, '2026-10-10T09:20:30Z', 45);
    stay(places.Office, '2026-10-10T09:21:30Z', '2026-10-10T12:00:30Z');
  },
};
export const manualRoute = [places.Home, [49.841, 24.034], [49.837, 24.055], places.Office];
export const manualTrip = tripSpec('18:30', '20:00', { via: manualRoute.slice(1, -1), movement: 'Bicycle' });
export const convertedStay = staySpec('18:00', '21:00');
export const emptySeed = { recipe: () => {} };
export const dstSeed = day => ({ timezone: 'Europe/Berlin', date: day,
  recipe: ({ stay }) => stay(places.Home, `${day}T00:00:17Z`, `${day}T05:00:17Z`) });
