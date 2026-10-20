import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { ActivityBreakdown } from '../components/ActivityBreakdown';
import { LineChart } from '../components/LineChart';
import { RouteMap } from '../components/RouteMap';
import { Banner, Button, Card, SectionTitle, StatCard } from '../components/ui';
import { TrackingService } from '../native/modules';
import type { SessionDetail } from '../native/types';
import type { RootStackParamList } from '../navigation/types';
import { spacing, useAppTheme } from '../theme/theme';
import {
  formatDateTime,
  formatDistance,
  formatDuration,
} from '../utils/format';

type Props = NativeStackScreenProps<RootStackParamList, 'SessionDetail'>;

export function SessionDetailScreen({ route, navigation }: Props) {
  const { colors } = useAppTheme();
  const { sessionId } = route.params;
  const [detail, setDetail] = useState<SessionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    TrackingService.getSessionDetail(sessionId)
      .then(d => {
        setDetail(d);
        navigation.setOptions({ title: formatDateTime(d.session.startTime) });
      })
      .catch(e => setError(e?.message ?? 'Could not load session'));
  }, [sessionId, navigation]);

  const series = useMemo(() => {
    const samples = detail?.samples ?? [];
    return {
      intensity: samples.map(s => s.intensity),
      light: samples.flatMap(s => (s.light == null ? [] : [s.light])),
      pressure: samples.flatMap(s => (s.pressure == null ? [] : [s.pressure])),
    };
  }, [detail]);

  const exportCsv = async () => {
    setExporting(true);
    try {
      await TrackingService.exportSessionCsv(sessionId);
    } catch (e: any) {
      setError(e?.message ?? 'Export failed');
    } finally {
      setExporting(false);
    }
  };

  const confirmDelete = () =>
    Alert.alert(
      'Delete session?',
      'Its route and sensor data will be removed from this device.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await TrackingService.deleteSession(sessionId);
              navigation.goBack();
            } catch (e: any) {
              setError(e?.message ?? 'Delete failed');
            }
          },
        },
      ],
    );

  if (!detail) {
    return (
      <View style={[styles.center, { backgroundColor: colors.surface }]}>
        {error ? (
          <Banner message={error} />
        ) : (
          <ActivityIndicator color={colors.primary} />
        )}
      </View>
    );
  }

  const { session } = detail;
  const duration = (session.endTime ?? Date.now()) - session.startTime;
  const movingMs = session.durations.walking + session.durations.running;
  const pace =
    session.distanceM > 0 && movingMs > 0
      ? movingMs / 60000 / (session.distanceM / 1000)
      : null;

  return (
    <ScrollView
      style={{ backgroundColor: colors.surface }}
      contentContainerStyle={styles.content}
    >
      {error ? (
        <Banner
          message={error}
          actionLabel="Dismiss"
          onAction={() => setError(null)}
        />
      ) : null}

      <RouteMap route={detail.route} height={260} />

      <View style={styles.grid}>
        <StatCard
          icon="route"
          label="Distance"
          value={formatDistance(session.distanceM)}
        />
        <StatCard
          icon="timer"
          label="Duration"
          value={formatDuration(duration)}
        />
      </View>
      <View style={styles.grid}>
        <StatCard
          icon="walk"
          label="Avg pace"
          value={
            pace
              ? `${Math.floor(pace)}:${String(
                  Math.round((pace % 1) * 60),
                ).padStart(2, '0')}`
              : '—'
          }
          caption="min/km while moving"
          accent={colors.activity.walking}
        />
        <StatCard
          icon="sun"
          label="Avg light"
          value={
            session.avgLight != null
              ? `${Math.round(session.avgLight)} lx`
              : '—'
          }
          caption={
            session.avgPressure != null
              ? `${session.avgPressure.toFixed(1)} hPa avg`
              : 'no barometer data'
          }
          accent={colors.chart.light}
        />
      </View>

      <SectionTitle>Time by activity</SectionTitle>
      <Card>
        <ActivityBreakdown durations={session.durations} />
      </Card>

      <SectionTitle>Sensor timeline</SectionTitle>
      <Card style={styles.charts}>
        <LineChart
          title="Motion intensity"
          unit="m/s²"
          decimals={2}
          data={series.intensity}
          color={colors.chart.intensity}
          emptyText="Not enough samples"
        />
        <LineChart
          title="Ambient light"
          unit="lx"
          decimals={0}
          data={series.light}
          color={colors.chart.light}
          emptyText="No light data"
        />
        <LineChart
          title="Pressure"
          unit="hPa"
          decimals={2}
          data={series.pressure}
          color={colors.chart.pressure}
          emptyText="No barometer on this device"
        />
      </Card>

      <View style={styles.actions}>
        <Button
          label="Export CSV"
          icon="share"
          variant="tonal"
          loading={exporting}
          onPress={exportCsv}
          style={styles.flex}
        />
        <Button
          label="Delete"
          icon="delete"
          variant="danger"
          onPress={confirmDelete}
          disabled={session.status === 'active'}
          style={styles.flex}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.lg,
  },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 2 },
  grid: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.md },
  charts: { gap: spacing.xl },
  actions: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.xl },
  flex: { flex: 1 },
});
