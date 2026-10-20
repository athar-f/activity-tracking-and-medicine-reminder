import React, { useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { ActivityBreakdown } from '../components/ActivityBreakdown';
import { Icon, type IconName } from '../components/Icon';
import { LineChart } from '../components/LineChart';
import { Screen } from '../components/Screen';
import { Banner, Button, Card, SectionTitle, StatCard } from '../components/ui';
import { AuthService } from '../native/modules';
import type { ActivityType, User } from '../native/types';
import { useTracking } from '../state/TrackingContext';
import { ACTIVITY_LABELS, radius, spacing, useAppTheme } from '../theme/theme';
import {
  describeLight,
  formatClock,
  formatDistance,
  pressureToAltitude,
} from '../utils/format';

const ACTIVITY_ICONS: Record<ActivityType, IconName> = {
  stationary: 'stand',
  walking: 'walk',
  running: 'run',
};

export function DashboardScreen() {
  const { colors } = useAppTheme();
  const { snapshot, sensors, series, busy, error, start, stop, clearError } =
    useTracking();
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    AuthService.getCurrentUser()
      .then(setUser)
      .catch(() => undefined);
  }, []);

  const confirmLogout = () =>
    Alert.alert(
      'Sign out',
      snapshot.isTracking ? 'This will also stop the running session.' : '',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Sign out',
          style: 'destructive',
          onPress: () => AuthService.logout(),
        },
      ],
    );

  const activityColor = colors.activity[snapshot.activity];
  const tracking = snapshot.isTracking;

  return (
    <Screen
      title={user ? `Hi, ${user.displayName.split(' ')[0]}` : 'Dashboard'}
      subtitle={tracking ? 'Session in progress' : 'Ready when you are'}
      right={
        <Pressable
          onPress={confirmLogout}
          hitSlop={12}
          accessibilityLabel="Sign out"
        >
          <Icon name="logout" color={colors.onSurfaceVariant} />
        </Pressable>
      }
    >
      {error ? (
        <Banner message={error} actionLabel="Dismiss" onAction={clearError} />
      ) : null}

      {/* Current activity hero */}
      <Card style={[styles.hero, { backgroundColor: colors.primaryContainer }]}>
        <View style={styles.heroRow}>
          <View
            style={[
              styles.activityIcon,
              { backgroundColor: tracking ? activityColor : colors.outline },
            ]}
          >
            <Icon
              name={ACTIVITY_ICONS[snapshot.activity]}
              size={36}
              color="#FFFFFF"
            />
          </View>
          <View style={styles.heroText}>
            <Text
              style={[styles.heroLabel, { color: colors.onPrimaryContainer }]}
            >
              Current activity
            </Text>
            <Text
              style={[
                styles.heroActivity,
                { color: colors.onPrimaryContainer },
              ]}
            >
              {tracking ? ACTIVITY_LABELS[snapshot.activity] : 'Not tracking'}
            </Text>
            <Text
              style={[
                styles.heroDuration,
                { color: colors.onPrimaryContainer },
              ]}
            >
              {tracking
                ? `for ${formatClock(snapshot.activityDurationMs)}`
                : 'Start a session to detect activity'}
            </Text>
          </View>
        </View>

        {tracking && (
          <View style={styles.sessionRow}>
            <Icon name="timer" size={18} color={colors.onPrimaryContainer} />
            <Text
              style={[
                styles.sessionClock,
                { color: colors.onPrimaryContainer },
              ]}
            >
              Session {formatClock(snapshot.elapsedMs ?? 0)}
            </Text>
          </View>
        )}

        <Button
          label={tracking ? 'Stop session' : 'Start tracking'}
          icon={tracking ? 'stop' : 'play'}
          variant={tracking ? 'danger' : 'filled'}
          loading={busy}
          onPress={tracking ? stop : start}
          style={styles.heroButton}
        />
      </Card>

      {/* Key stats */}
      <View style={styles.grid}>
        <StatCard
          icon="route"
          label="Distance"
          value={formatDistance(snapshot.distanceM)}
          caption={tracking ? 'this session' : 'no active session'}
        />
        <StatCard
          icon="walk"
          label="Cadence"
          value={tracking ? `${Math.round(snapshot.cadenceHz * 60)} spm` : '—'}
          caption={
            tracking
              ? `motion ${snapshot.intensity.toFixed(2)} m/s²`
              : 'steps per minute'
          }
          accent={colors.activity.walking}
        />
      </View>
      <View style={styles.grid}>
        <StatCard
          icon="sun"
          label="Ambient light"
          value={
            sensors?.light === false
              ? 'N/A'
              : snapshot.lightLux == null
              ? '…'
              : `${Math.round(snapshot.lightLux)} lx`
          }
          caption={
            sensors?.light === false
              ? 'No light sensor'
              : snapshot.lightLux != null
              ? describeLight(snapshot.lightLux)
              : undefined
          }
          accent={colors.chart.light}
        />
        <StatCard
          icon="gauge"
          label="Pressure"
          value={
            sensors?.pressure === false
              ? 'N/A'
              : snapshot.pressureHpa == null
              ? '…'
              : `${snapshot.pressureHpa.toFixed(1)} hPa`
          }
          caption={
            sensors?.pressure === false
              ? 'No barometer on this device'
              : snapshot.pressureHpa != null
              ? `≈ ${Math.round(
                  pressureToAltitude(snapshot.pressureHpa),
                )} m altitude`
              : undefined
          }
          accent={colors.chart.pressure}
        />
      </View>

      {tracking && (
        <>
          <SectionTitle>Time by activity</SectionTitle>
          <Card>
            <ActivityBreakdown durations={snapshot.durations} />
          </Card>
        </>
      )}

      <SectionTitle>Live sensors</SectionTitle>
      <Card style={styles.charts}>
        <LineChart
          title="Motion intensity"
          unit="m/s²"
          decimals={2}
          data={series.intensity}
          color={colors.chart.intensity}
          emptyText={
            tracking ? 'Collecting data…' : 'Starts with a tracking session'
          }
        />
        {sensors?.light !== false && (
          <LineChart
            title="Ambient light"
            unit="lx"
            decimals={0}
            data={series.light}
            color={colors.chart.light}
          />
        )}
        {sensors?.pressure !== false && (
          <LineChart
            title="Atmospheric pressure"
            unit="hPa"
            decimals={2}
            data={series.pressure}
            color={colors.chart.pressure}
          />
        )}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { gap: spacing.lg, borderRadius: radius.xl, padding: spacing.xl },
  heroRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.lg },
  activityIcon: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroText: { flex: 1 },
  heroLabel: { fontSize: 13, opacity: 0.8 },
  heroActivity: { fontSize: 30, fontWeight: '800' },
  heroDuration: { fontSize: 14, fontVariant: ['tabular-nums'] },
  sessionRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  sessionClock: {
    fontSize: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  heroButton: { alignSelf: 'stretch' },
  grid: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.md },
  charts: { gap: spacing.xl },
});
