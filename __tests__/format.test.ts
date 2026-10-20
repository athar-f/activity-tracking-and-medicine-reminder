import {
  describeLight,
  formatClock,
  formatDistance,
  formatDuration,
  formatTimeOfDay,
  pressureToAltitude,
} from '../src/utils/format';

describe('format utils', () => {
  it('formats clock durations', () => {
    expect(formatClock(65_000)).toBe('01:05');
    expect(formatClock(3_725_000)).toBe('1:02:05');
    expect(formatClock(-5)).toBe('00:00');
  });

  it('formats compact durations', () => {
    expect(formatDuration(45_000)).toBe('45s');
    expect(formatDuration(12 * 60_000)).toBe('12m');
    expect(formatDuration(65 * 60_000)).toBe('1h 05m');
  });

  it('formats distances', () => {
    expect(formatDistance(850.4)).toBe('850 m');
    expect(formatDistance(1234)).toBe('1.23 km');
  });

  it('pads times of day', () => {
    expect(formatTimeOfDay(8, 5)).toBe('08:05');
  });

  it('derives altitude from pressure', () => {
    expect(pressureToAltitude(1013.25)).toBeCloseTo(0, 5);
    expect(pressureToAltitude(899)).toBeGreaterThan(950);
    expect(pressureToAltitude(899)).toBeLessThan(1050);
  });

  it('describes light levels', () => {
    expect(describeLight(5)).toBe('Dark');
    expect(describeLight(500)).toBe('Bright indoor');
    expect(describeLight(50_000)).toBe('Direct sunlight');
  });
});
