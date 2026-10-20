const pad = (n: number) => n.toString().padStart(2, '0');

/** 3725000 → "1:02:05", 65000 → "01:05". */
export function formatClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

/** Compact human duration: "45s", "12m", "1h 05m". */
export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  if (total < 60) return `${total}s`;
  const minutes = Math.floor(total / 60);
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h ${pad(minutes % 60)}m`;
}

export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(2)} km`;
}

export function formatTimeOfDay(hour: number, minute: number): string {
  return `${pad(hour)}:${pad(minute)}`;
}

export function formatDateTime(ts: number): string {
  const d = new Date(ts);
  return `${d.toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })} · ${formatTimeOfDay(d.getHours(), d.getMinutes())}`;
}

export function formatRelative(ts: number, now = Date.now()): string {
  const diffMin = Math.round((ts - now) / 60000);
  if (diffMin < 1) return 'now';
  if (diffMin < 60) return `in ${diffMin} min`;
  const h = Math.floor(diffMin / 60);
  const m = diffMin % 60;
  return m === 0 ? `in ${h} h` : `in ${h} h ${m} min`;
}

/** Converts sea-level-referenced pressure to an approximate altitude (international formula). */
export function pressureToAltitude(hPa: number, seaLevelHpa = 1013.25): number {
  return 44330 * (1 - Math.pow(hPa / seaLevelHpa, 1 / 5.255));
}

/** Rough lighting description for a lux reading. */
export function describeLight(lux: number): string {
  if (lux < 10) return 'Dark';
  if (lux < 200) return 'Dim indoor';
  if (lux < 1000) return 'Bright indoor';
  if (lux < 10000) return 'Overcast daylight';
  return 'Direct sunlight';
}
