import React, { useMemo, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Defs, Line, LinearGradient, Path, Stop } from 'react-native-svg';
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

/** Lightweight area/line chart on react-native-svg; auto-scales to the data range. */
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
  const [width, setWidth] = useState(0);
  const gradientId = useMemo(() => `grad-${title.replace(/\W/g, '')}`, [title]);

  const { line, area, min, max } = useMemo(() => {
    if (data.length < 2 || width === 0)
      return { line: '', area: '', min: 0, max: 0 };
    let lo = Math.min(...data);
    let hi = Math.max(...data);
    if (hi - lo < 1e-6) {
      lo -= 1;
      hi += 1;
    }
    const pad = (hi - lo) * 0.1;
    lo -= pad;
    hi += pad;
    const stepX = width / (data.length - 1);
    const y = (v: number) => height - ((v - lo) / (hi - lo)) * height;
    const points = data.map(
      (v, i) => `${(i * stepX).toFixed(1)},${y(v).toFixed(1)}`,
    );
    const linePath = `M${points.join(' L')}`;
    return {
      line: linePath,
      area: `${linePath} L${width},${height} L0,${height} Z`,
      min: Math.min(...data),
      max: Math.max(...data),
    };
  }, [data, width, height]);

  const onLayout = (e: LayoutChangeEvent) =>
    setWidth(e.nativeEvent.layout.width);
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
      <View style={{ height }} onLayout={onLayout}>
        {line ? (
          <Svg width={width} height={height}>
            <Defs>
              <LinearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <Stop offset="0" stopColor={color} stopOpacity={0.35} />
                <Stop offset="1" stopColor={color} stopOpacity={0} />
              </LinearGradient>
            </Defs>
            {[0.25, 0.5, 0.75].map(f => (
              <Line
                key={f}
                x1={0}
                x2={width}
                y1={height * f}
                y2={height * f}
                stroke={colors.outlineVariant}
                strokeWidth={0.5}
                strokeDasharray="4 4"
              />
            ))}
            <Path d={area} fill={`url(#${gradientId})`} />
            <Path
              d={line}
              stroke={color}
              strokeWidth={2}
              fill="none"
              strokeLinejoin="round"
            />
          </Svg>
        ) : (
          <View
            style={[styles.placeholder, { borderColor: colors.outlineVariant }]}
          >
            <Text style={{ color: colors.onSurfaceVariant }}>
              {emptyText ?? 'Collecting data…'}
            </Text>
          </View>
        )}
      </View>
      {line ? (
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
  placeholder: {
    flex: 1,
    borderRadius: 8,
    borderWidth: 1,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
  range: { fontSize: 11, marginTop: spacing.xs, textAlign: 'right' },
});
