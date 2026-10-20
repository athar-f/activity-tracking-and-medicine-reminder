import React, { useMemo, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import type { GeoPoint } from '../native/types';
import { useAppTheme } from '../theme/theme';

/**
 * Map-free route preview: projects the GPS polyline (equirectangular, fine at walking scale)
 * into the available box. Used in history cards and as a fallback when no Maps key is set.
 */
export function RouteSketch({
  route,
  height = 160,
}: {
  route: GeoPoint[];
  height?: number;
}) {
  const { colors } = useAppTheme();
  const [width, setWidth] = useState(0);

  const path = useMemo(() => {
    if (route.length < 2 || width === 0) return null;
    const lat0 = route[0].latitude;
    const kx = Math.cos((lat0 * Math.PI) / 180);
    const xs = route.map(p => p.longitude * kx);
    const ys = route.map(p => p.latitude);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const span = Math.max(maxX - minX, maxY - minY, 1e-6);
    const pad = 16;
    const scale = Math.min((width - pad * 2) / span, (height - pad * 2) / span);
    const offX = (width - (maxX - minX) * scale) / 2;
    const offY = (height - (maxY - minY) * scale) / 2;
    const pts = xs.map((x, i) => ({
      x: offX + (x - minX) * scale,
      y: height - (offY + (ys[i] - minY) * scale),
    }));
    return {
      d: `M${pts.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L')}`,
      start: pts[0],
      end: pts[pts.length - 1],
    };
  }, [route, width, height]);

  const onLayout = (e: LayoutChangeEvent) =>
    setWidth(e.nativeEvent.layout.width);

  return (
    <View
      style={[
        styles.box,
        { height, backgroundColor: colors.surfaceContainerHigh },
      ]}
      onLayout={onLayout}
    >
      {path ? (
        <Svg width={width} height={height}>
          <Path
            d={path.d}
            stroke={colors.primary}
            strokeWidth={4}
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <Circle
            cx={path.start.x}
            cy={path.start.y}
            r={6}
            fill={colors.success}
          />
          <Circle cx={path.end.x} cy={path.end.y} r={6} fill={colors.error} />
        </Svg>
      ) : (
        <Text style={{ color: colors.onSurfaceVariant }}>
          {route.length === 1
            ? 'Waiting for movement…'
            : 'No GPS route recorded'}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
});
