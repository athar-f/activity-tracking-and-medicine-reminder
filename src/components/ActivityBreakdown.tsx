import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { ActivityType, Durations } from '../native/types';
import { ACTIVITY_LABELS, spacing, useAppTheme } from '../theme/theme';
import { formatDuration } from '../utils/format';

const ORDER: ActivityType[] = ['walking', 'running', 'stationary'];

/** Stacked bar + legend showing time spent per activity. */
export function ActivityBreakdown({ durations }: { durations: Durations }) {
  const { colors } = useAppTheme();
  const total = ORDER.reduce((sum, k) => sum + durations[k], 0);

  return (
    <View>
      <View
        style={[styles.bar, { backgroundColor: colors.surfaceContainerHigh }]}
      >
        {total > 0 &&
          ORDER.map(k =>
            durations[k] > 0 ? (
              <View
                key={k}
                style={{
                  flex: durations[k] / total,
                  backgroundColor: colors.activity[k],
                }}
              />
            ) : null,
          )}
      </View>
      <View style={styles.legend}>
        {ORDER.map(k => (
          <View key={k} style={styles.legendItem}>
            <View
              style={[styles.dot, { backgroundColor: colors.activity[k] }]}
            />
            <Text
              style={[styles.legendText, { color: colors.onSurfaceVariant }]}
            >
              {ACTIVITY_LABELS[k]}{' '}
              <Text style={[styles.legendValue, { color: colors.onSurface }]}>
                {formatDuration(durations[k])}
              </Text>
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    height: 12,
    borderRadius: 6,
    overflow: 'hidden',
    flexDirection: 'row',
  },
  legend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.md,
    marginTop: spacing.sm,
  },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  legendText: { fontSize: 12 },
  legendValue: { fontWeight: '600' },
});
