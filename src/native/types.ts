/** Shapes returned by the Kotlin bridge (see android/.../bridge/Mappers.kt). */

export type ActivityType = 'stationary' | 'walking' | 'running';

export type Durations = Record<ActivityType, number>;

export interface GeoPoint {
  ts: number;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number;
}

export interface LocationEvent extends GeoPoint {
  sessionId: number;
}

export interface TrackingSnapshot {
  isTracking: boolean;
  sessionId: number | null;
  startTime: number | null;
  elapsedMs: number | null;
  activity: ActivityType;
  activityDurationMs: number;
  durations: Durations;
  distanceM: number;
  lightLux: number | null;
  pressureHpa: number | null;
  /** Std-dev of acceleration magnitude over the last window, m/s². */
  intensity: number;
  cadenceHz: number;
  lastLocation: GeoPoint | null;
}

export interface SensorAvailability {
  accelerometer: boolean;
  gyroscope: boolean;
  light: boolean;
  pressure: boolean;
}

export interface TrackingStatus {
  snapshot: TrackingSnapshot;
  sensors: SensorAvailability;
}

export interface SessionSummary {
  id: number;
  startTime: number;
  endTime: number | null;
  distanceM: number;
  durations: Durations;
  avgLight: number | null;
  avgPressure: number | null;
  status: 'active' | 'completed';
}

export interface SensorSample {
  ts: number;
  activity: ActivityType;
  intensity: number;
  light: number | null;
  pressure: number | null;
}

export interface SessionDetail {
  session: SessionSummary;
  samples: SensorSample[];
  route: GeoPoint[];
}

export interface Medicine {
  id: number;
  name: string;
  dosage: string;
  hour: number;
  minute: number;
  notes: string;
  enabled: boolean;
  nextReminderAt: number | null;
}

export interface NewMedicine {
  name: string;
  dosage: string;
  hour: number;
  minute: number;
  notes: string;
}

export interface ReminderStatus {
  exactAlarmsAllowed: boolean;
  notificationsEnabled: boolean;
  leadTimeMinutes: number;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
}

export const EMPTY_DURATIONS: Durations = {
  stationary: 0,
  walking: 0,
  running: 0,
};

export const EMPTY_SNAPSHOT: TrackingSnapshot = {
  isTracking: false,
  sessionId: null,
  startTime: null,
  elapsedMs: null,
  activity: 'stationary',
  activityDurationMs: 0,
  durations: EMPTY_DURATIONS,
  distanceM: 0,
  lightLux: null,
  pressureHpa: null,
  intensity: 0,
  cadenceHz: 0,
  lastLocation: null,
};
