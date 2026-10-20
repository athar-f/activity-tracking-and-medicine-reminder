import { NativeEventEmitter, NativeModules } from 'react-native';
import type {
  LocationEvent,
  Medicine,
  NewMedicine,
  ReminderStatus,
  SessionDetail,
  SessionSummary,
  TrackingSnapshot,
  TrackingStatus,
  User,
} from './types';

/**
 * Typed wrappers around the Kotlin native modules. All business logic (sensors, storage,
 * scheduling, auth) lives natively; JS only renders and issues commands.
 */

interface TrackingNative {
  startTracking(): Promise<void>;
  stopTracking(): Promise<void>;
  getStatus(): Promise<TrackingStatus>;
  startEnvironmentPreview(): void;
  stopEnvironmentPreview(): void;
  getSessions(): Promise<SessionSummary[]>;
  getSessionDetail(id: number): Promise<SessionDetail>;
  deleteSession(id: number): Promise<void>;
  exportSessionCsv(id: number): Promise<string>;
}

interface ReminderNative {
  getMedicines(): Promise<Medicine[]>;
  addMedicine(input: NewMedicine): Promise<Medicine>;
  setMedicineEnabled(id: number, enabled: boolean): Promise<Medicine>;
  deleteMedicine(id: number): Promise<void>;
  getReminderStatus(): Promise<ReminderStatus>;
  openExactAlarmSettings(): void;
  scheduleTestReminder(delaySeconds: number): Promise<number>;
}

interface AuthNative {
  getCurrentUser(): Promise<User | null>;
  logout(): Promise<void>;
}

function requireModule<T>(name: string): T {
  const module = NativeModules[name];
  if (!module) {
    throw new Error(
      `Native module "${name}" is not linked. This app targets Android only.`,
    );
  }
  return module as T;
}

const trackingNative = requireModule<TrackingNative>('TrackingModule');
const trackingEvents = new NativeEventEmitter(NativeModules.TrackingModule);

export const TrackingService = {
  start: () => trackingNative.startTracking(),
  stop: () => trackingNative.stopTracking(),
  getStatus: () => trackingNative.getStatus(),
  startEnvironmentPreview: () => trackingNative.startEnvironmentPreview(),
  stopEnvironmentPreview: () => trackingNative.stopEnvironmentPreview(),
  getSessions: () => trackingNative.getSessions(),
  getSessionDetail: (id: number) => trackingNative.getSessionDetail(id),
  deleteSession: (id: number) => trackingNative.deleteSession(id),
  exportSessionCsv: (id: number) => trackingNative.exportSessionCsv(id),

  onUpdate(listener: (snapshot: TrackingSnapshot) => void): () => void {
    const sub = trackingEvents.addListener('TrackingUpdate', e =>
      listener(e as TrackingSnapshot),
    );
    return () => sub.remove();
  },

  onLocation(listener: (point: LocationEvent) => void): () => void {
    const sub = trackingEvents.addListener('LocationUpdate', e =>
      listener(e as LocationEvent),
    );
    return () => sub.remove();
  },
};

const reminderNative = requireModule<ReminderNative>('ReminderModule');

export const ReminderService = {
  getMedicines: () => reminderNative.getMedicines(),
  addMedicine: (input: NewMedicine) => reminderNative.addMedicine(input),
  setEnabled: (id: number, enabled: boolean) =>
    reminderNative.setMedicineEnabled(id, enabled),
  deleteMedicine: (id: number) => reminderNative.deleteMedicine(id),
  getStatus: () => reminderNative.getReminderStatus(),
  openExactAlarmSettings: () => reminderNative.openExactAlarmSettings(),
  scheduleTest: (delaySeconds: number) =>
    reminderNative.scheduleTestReminder(delaySeconds),
};

const authNative = requireModule<AuthNative>('AuthModule');

export const AuthService = {
  getCurrentUser: () => authNative.getCurrentUser(),
  logout: () => authNative.logout(),
};
