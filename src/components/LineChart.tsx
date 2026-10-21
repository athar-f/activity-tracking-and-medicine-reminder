import React, { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { spacing, useAppTheme } from '../theme/theme';

interface Props {
  data: number[];
  color: string;
  height?: number;
  title: string;
  unit: string;
  decimals?: number;
  emptyText?: string;
}

const MAX_BARS = 90;

/** Sparkline drawn with plain Views (one bar per sample), auto-scaled to the data range. */
export function LineChart({
  data,
  color,
  height = 120,
  title,
  unit,
  decimals = 1,
  emptyText,
}: Props) {
  const { colors } = useAppTheme();

  const { bars, min, max } = useMemo(() => {
    if (data.length < 2) return { bars: [] as number[], min: 0, max: 0 };
    // Downsample long histories by averaging buckets so bars stay readable.
    const bucket = Math.ceil(data.length / MAX_BARS);
    const values: number[] = [];
    for (let i = 0; i < data.length; i += bucket) {
      const slice = data.slice(i, i + bucket);
      values.push(slice.reduce((s, v) => s + v, 0) / slice.length);
    }
    const lo = Math.min(...values);
    const hi = Math.max(...values);
    const span = hi - lo < 1e-6 ? 1 : hi - lo;
    return {
      // 15% floor so the smallest value is still visible.
      bars: values.map(v => 0.15 + ((v - lo) / span) * 0.85),
      min: Math.min(...data),
      max: Math.max(...data),
    };
  }, [data]);

  const latest = data.length ? data[data.length - 1] : null;

  return (
    <View>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurfaceVariant }]}>
          {title}
        </Text>
        <Text style={[styles.value, { color: colors.onSurface }]}>
          {latest == null ? '—' : `${latest.toFixed(decimals)} ${unit}`}
        </Text>
      </View>
      {bars.length ? (
        <View
          style={[styles.plot, { height, borderColor: colors.outlineVariant }]}
        >
          {bars.map((f, i) => (
            <View
              key={i}
              style={[
                styles.bar,
                i === bars.length - 1 ? styles.latestBar : styles.pastBar,
                {
                  height: `${f * 100}%`,
                  backgroundColor: color,
                },
              ]}
            />
          ))}
        </View>
      ) : (
        <View
          style={[
            styles.placeholder,
            { height, borderColor: colors.outlineVariant },
          ]}
        >
          <Text style={{ color: colors.onSurfaceVariant }}>
            {emptyText ?? 'Collecting data…'}
          </Text>
        </View>
      )}
      {bars.length ? (
        <Text style={[styles.range, { color: colors.onSurfaceVariant }]}>
          min {min.toFixed(decimals)} · max {max.toFixed(decimals)} {unit}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  title: { fontSize: 13, fontWeight: '600' },
  value: { fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] },
  plot: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 1,
    borderBottomWidth: 1,
  },
  bar: { flex: 1, borderTopLeftRadius: 2, borderTopRightRadius: 2 },
  latestBar: { opacity: 1 },
  pastBar: { opacity: 0.55 },
  placeholder: {
    borderRadius: 8,
    borderWidth: 1,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  range: { fontSize: 11, marginTop: spacing.xs, textAlign: 'right' },
});
