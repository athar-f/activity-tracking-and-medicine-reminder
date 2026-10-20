import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ActivityBreakdown } from '../components/ActivityBreakdown';
import { Icon } from '../components/Icon';
import { Card, EmptyState } from '../components/ui';
import { TrackingService } from '../native/modules';
import type { SessionSummary } from '../native/types';
import type { RootStackParamList } from '../navigation/types';
import { useTracking } from '../state/TrackingContext';
import { spacing, useAppTheme } from '../theme/theme';
import {
  formatDateTime,
  formatDistance,
  formatDuration,
} from '../utils/format';

export function HistoryScreen() {
  const { colors } = useAppTheme();
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { snapshot } = useTracking();
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSessions(await TrackingService.getSessions());
    } finally {
      setLoading(false);
    }
  }, []);

  // Reload whenever the tab gains focus or a session starts/stops.
  const isTracking = snapshot.isTracking;
  useFocusEffect(
    useCallback(() => {
      load().catch(() => undefined);
    }, [load]),
  );
  useEffect(() => {
    load().catch(() => undefined);
  }, [load, isTracking]);

  const totalDistance = sessions.reduce((sum, s) => sum + s.distanceM, 0);

  return (
    <SafeAreaView
      edges={['top']}
      style={[styles.root, { backgroundColor: colors.surface }]}
    >
      <FlatList
        data={sessions}
        keyExtractor={s => String(s.id)}
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl
            refreshing={loading}
            onRefresh={load}
            colors={[colors.primary]}
          />
        }
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>
              History
            </Text>
            <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
              {sessions.length} sessions · {formatDistance(totalDistance)} total
            </Text>
          </View>
        }
        ListEmptyComponent={
          loading ? undefined : (
            <EmptyState
              icon="history"
              title="No sessions yet"
              body="Start tracking from the dashboard. Every session is stored on this device."
            />
          )
        }
        ItemSeparatorComponent={Separator}
        renderItem={({ item }) => {
          const active = item.status === 'active';
          const duration = (item.endTime ?? Date.now()) - item.startTime;
          return (
            <Card
              onPress={() =>
                navigation.navigate('SessionDetail', { sessionId: item.id })
              }
            >
              <View style={styles.row}>
                <View style={styles.flex}>
                  <Text style={[styles.date, { color: colors.onSurface }]}>
                    {formatDateTime(item.startTime)}
                  </Text>
                  <Text
                    style={[styles.meta, { color: colors.onSurfaceVariant }]}
                  >
                    {formatDuration(duration)} ·{' '}
                    {formatDistance(item.distanceM)}
                    {item.avgLight != null
                      ? ` · ${Math.round(item.avgLight)} lx avg`
                      : ''}
                  </Text>
                </View>
                {active ? (
                  <View
                    style={[
                      styles.live,
                      { backgroundColor: colors.errorContainer },
                    ]}
                  >
                    <Text
                      style={[
                        styles.liveText,
                        { color: colors.onErrorContainer },
                      ]}
                    >
                      LIVE
                    </Text>
                  </View>
                ) : (
                  <Icon name="chevronRight" color={colors.onSurfaceVariant} />
                )}
              </View>
              <View style={styles.breakdown}>
                <ActivityBreakdown durations={item.durations} />
              </View>
            </Card>
          );
        }}
      />
    </SafeAreaView>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

const styles = StyleSheet.create({
  separator: { height: spacing.md },
  root: { flex: 1 },
  content: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xl },
  header: { paddingTop: spacing.lg, paddingBottom: spacing.md },
  title: { fontSize: 28, fontWeight: '700' },
  subtitle: { fontSize: 14, marginTop: 2 },
  row: { flexDirection: 'row', alignItems: 'center' },
  flex: { flex: 1 },
  date: { fontSize: 16, fontWeight: '600' },
  meta: { fontSize: 13, marginTop: 2 },
  breakdown: { marginTop: spacing.md },
  live: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  liveText: { fontSize: 11, fontWeight: '800' },
});
