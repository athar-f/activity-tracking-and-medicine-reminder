import React, { useMemo, useState } from 'react';
import { StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import type { GeoPoint } from '../native/types';
import { useAppTheme } from '../theme/theme';

const MAX_POINTS = 200;
const STROKE = 4;
const DOT = 12;

/**
 * Map-free route preview: projects the GPS polyline (equirectangular, fine at walking scale)
 * into the available box and draws each segment as a rotated View. Works offline and without
 * a Maps key.
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

  const pts = useMemo(() => {
    if (route.length < 2 || width === 0) return null;
    const step = Math.ceil(route.length / MAX_POINTS);
    const sampled = route.filter(
      (_, i) => i % step === 0 || i === route.length - 1,
    );
    const kx = Math.cos((sampled[0].latitude * Math.PI) / 180);
    const xs = sampled.map(p => p.longitude * kx);
    const ys = sampled.map(p => p.latitude);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const span = Math.max(maxX - minX, maxY - minY, 1e-6);
    const pad = 16;
    const scale = Math.min((width - pad * 2) / span, (height - pad * 2) / span);
    const offX = (width - (maxX - minX) * scale) / 2;
    const offY = (height - (maxY - minY) * scale) / 2;
    return xs.map((x, i) => ({
      x: offX + (x - minX) * scale,
      y: height - (offY + (ys[i] - minY) * scale),
    }));
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
      {pts ? (
        <>
          {pts.slice(1).map((b, i) => {
            const a = pts[i];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const len = Math.hypot(dx, dy);
            if (len < 0.5) return null;
            return (
              <View
                key={i}
                style={[
                  styles.segment,
                  {
                    backgroundColor: colors.primary,
                    width: len + STROKE / 2,
                    left: (a.x + b.x) / 2 - (len + STROKE / 2) / 2,
                    top: (a.y + b.y) / 2 - STROKE / 2,
                    transform: [{ rotate: `${Math.atan2(dy, dx)}rad` }],
                  },
                ]}
              />
            );
          })}
          <View
            style={[
              styles.dot,
              {
                backgroundColor: colors.success,
                left: pts[0].x - DOT / 2,
                top: pts[0].y - DOT / 2,
              },
            ]}
          />
          <View
            style={[
              styles.dot,
              {
                backgroundColor: colors.error,
                left: pts[pts.length - 1].x - DOT / 2,
                top: pts[pts.length - 1].y - DOT / 2,
              },
            ]}
          />
        </>
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
  segment: { position: 'absolute', height: STROKE, borderRadius: STROKE / 2 },
  dot: {
    position: 'absolute',
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
});
