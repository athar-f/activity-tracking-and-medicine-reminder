import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { RouteMap } from '../components/RouteMap';
import { Screen } from '../components/Screen';
import { Button, Card } from '../components/ui';
import { useTracking } from '../state/TrackingContext';
import { ACTIVITY_LABELS, spacing, useAppTheme } from '../theme/theme';
import { formatClock, formatDistance } from '../utils/format';

export function MapScreen() {
  const { colors } = useAppTheme();
  const { snapshot, route, busy, start, stop } = useTracking();
  const tracking = snapshot.isTracking;
  const accuracy = snapshot.lastLocation?.accuracy;

  return (
    <Screen
      title="Live route"
      subtitle={
        tracking
          ? accuracy != null
            ? `GPS ±${Math.round(accuracy)} m · ${route.length} points`
            : 'Waiting for GPS fix…'
          : 'Start a session to record your route'
      }
      scroll={false}
    >
      <RouteMap route={route} live current={snapshot.lastLocation} />

      <Card style={styles.panel}>
        <View style={styles.metrics}>
          <Metric
            label="Distance"
            value={formatDistance(snapshot.distanceM)}
            color={colors.onSurface}
          />
          <Metric
            label="Duration"
            value={tracking ? formatClock(snapshot.elapsedMs ?? 0) : '—'}
            color={colors.onSurface}
          />
          <Metric
            label="Activity"
            value={tracking ? ACTIVITY_LABELS[snapshot.activity] : '—'}
            color={colors.activity[snapshot.activity]}
          />
        </View>
        <Button
          label={tracking ? 'Stop session' : 'Start tracking'}
          icon={tracking ? 'stop' : 'play'}
          variant={tracking ? 'danger' : 'filled'}
          loading={busy}
          onPress={tracking ? stop : start}
        />
      </Card>
    </Screen>
  );
}

function Metric({
  label,
  value,
  color,
}: {
  label: string;
  value: string;
  color: string;
}) {
  const { colors } = useAppTheme();
  return (
    <View style={styles.metric}>
      <Text style={[styles.metricLabel, { color: colors.onSurfaceVariant }]}>
        {label}
      </Text>
      <Text style={[styles.metricValue, { color }]} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { marginTop: spacing.md, gap: spacing.md },
  metrics: { flexDirection: 'row' },
  metric: { flex: 1, alignItems: 'center' },
  metricLabel: { fontSize: 12 },
  metricValue: {
    fontSize: 18,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
});
