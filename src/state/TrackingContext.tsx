import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  AppState,
  PermissionsAndroid,
  Platform,
  type Permission,
} from 'react-native';
import { TrackingService } from '../native/modules';
import {
  EMPTY_SNAPSHOT,
  type GeoPoint,
  type SensorAvailability,
  type TrackingSnapshot,
} from '../native/types';

const SERIES_LENGTH = 90; // ~90 s of live history for charts

export interface LiveSeries {
  intensity: number[];
  light: number[];
  pressure: number[];
}

interface TrackingContextValue {
  snapshot: TrackingSnapshot;
  sensors: SensorAvailability | null;
  route: GeoPoint[];
  series: LiveSeries;
  busy: boolean;
  error: string | null;
  start(): Promise<void>;
  stop(): Promise<void>;
  clearError(): void;
}

const TrackingContext = createContext<TrackingContextValue | null>(null);

const emptySeries = (): LiveSeries => ({
  intensity: [],
  light: [],
  pressure: [],
});

const pushBounded = (arr: number[], value: number | null) =>
  value == null ? arr : [...arr.slice(-(SERIES_LENGTH - 1)), value];

async function ensurePermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  const wanted: Permission[] = [
    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
  ];
  if (Number(Platform.Version) >= 33) {
    wanted.push(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
  }
  const result = await PermissionsAndroid.requestMultiple(wanted);
  // Notifications are optional (tracking still works); location is required.
  return (
    result[PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION] ===
    PermissionsAndroid.RESULTS.GRANTED
  );
}

/**
 * Single subscription to the native tracking stream, shared by all screens. Holds the live
 * route and short rolling series for the real-time charts.
 */
export function TrackingProvider({ children }: { children: React.ReactNode }) {
  const [snapshot, setSnapshot] = useState<TrackingSnapshot>(EMPTY_SNAPSHOT);
  const [sensors, setSensors] = useState<SensorAvailability | null>(null);
  const [route, setRoute] = useState<GeoPoint[]>([]);
  const [series, setSeries] = useState<LiveSeries>(emptySeries);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sessionIdRef = useRef<number | null>(null);

  /** Re-reads native state, e.g. on mount or when returning from background. */
  const sync = useCallback(async () => {
    const status = await TrackingService.getStatus();
    setSensors(status.sensors);
    setSnapshot(status.snapshot);
    const { sessionId } = status.snapshot;
    sessionIdRef.current = sessionId;
    if (status.snapshot.isTracking && sessionId != null) {
      const detail = await TrackingService.getSessionDetail(sessionId);
      setRoute(detail.route);
    }
  }, []);

  useEffect(() => {
    sync().catch(e => setError(String(e?.message ?? e)));
    TrackingService.startEnvironmentPreview();

    const offUpdate = TrackingService.onUpdate(next => {
      // New session started: reset per-session buffers.
      if (next.isTracking && next.sessionId !== sessionIdRef.current) {
        sessionIdRef.current = next.sessionId;
        setRoute([]);
        setSeries(emptySeries());
      }
      if (!next.isTracking) sessionIdRef.current = null;
      setSnapshot(next);
      setSeries(prev => ({
        intensity: next.isTracking
          ? pushBounded(prev.intensity, next.intensity)
          : prev.intensity,
        light: pushBounded(prev.light, next.lightLux),
        pressure: pushBounded(prev.pressure, next.pressureHpa),
      }));
    });

    const offLocation = TrackingService.onLocation(point => {
      if (point.sessionId === sessionIdRef.current) {
        setRoute(prev => [...prev, point]);
      }
    });

    const appStateSub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        TrackingService.startEnvironmentPreview();
        sync().catch(() => undefined);
      } else if (state === 'background') {
        TrackingService.stopEnvironmentPreview();
      }
    });

    return () => {
      offUpdate();
      offLocation();
      appStateSub.remove();
      TrackingService.stopEnvironmentPreview();
    };
  }, [sync]);

  const start = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      if (!(await ensurePermissions())) {
        setError('Location permission is needed to record your route.');
        return;
      }
      await TrackingService.start();
    } catch (e: any) {
      setError(e?.message ?? 'Could not start tracking');
    } finally {
      setBusy(false);
    }
  }, []);

  const stop = useCallback(async () => {
    setBusy(true);
    try {
      await TrackingService.stop();
    } catch (e: any) {
      setError(e?.message ?? 'Could not stop tracking');
    } finally {
      setBusy(false);
    }
  }, []);

  const value = useMemo(
    () => ({
      snapshot,
      sensors,
      route,
      series,
      busy,
      error,
      start,
      stop,
      clearError: () => setError(null),
    }),
    [snapshot, sensors, route, series, busy, error, start, stop],
  );

  return (
    <TrackingContext.Provider value={value}>
      {children}
    </TrackingContext.Provider>
  );
}

export function useTracking(): TrackingContextValue {
  const ctx = useContext(TrackingContext);
  if (!ctx)
    throw new Error('useTracking must be used inside <TrackingProvider>');
  return ctx;
}
