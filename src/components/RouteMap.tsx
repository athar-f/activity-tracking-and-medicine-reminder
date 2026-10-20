import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker, Polyline, PROVIDER_GOOGLE } from 'react-native-maps';
import type { GeoPoint } from '../native/types';
import { radius, spacing, useAppTheme } from '../theme/theme';
import { RouteSketch } from './RouteSketch';

type Mode = 'map' | 'sketch';

/**
 * Google map with the traveled polyline. A "Sketch" toggle renders the same route with SVG,
 * which works offline and without a Maps API key.
 */
export function RouteMap({
  route,
  live = false,
  height,
  current,
}: {
  route: GeoPoint[];
  live?: boolean;
  height?: number;
  current?: GeoPoint | null;
}) {
  const { colors, isDark } = useAppTheme();
  const [mode, setMode] = useState<Mode>('map');
  const mapRef = useRef<MapView>(null);
  const coords = useMemo(
    () => route.map(p => ({ latitude: p.latitude, longitude: p.longitude })),
    [route],
  );
  const focus = current ?? route[route.length - 1] ?? null;

  useEffect(() => {
    if (mode !== 'map' || !mapRef.current) return;
    if (live && focus) {
      mapRef.current.animateCamera(
        {
          center: { latitude: focus.latitude, longitude: focus.longitude },
          zoom: 17,
        },
        { duration: 500 },
      );
    } else if (!live && coords.length > 1) {
      mapRef.current.fitToCoordinates(coords, {
        edgePadding: { top: 40, right: 40, bottom: 40, left: 40 },
        animated: false,
      });
    }
  }, [mode, live, focus, coords]);

  const box = height ? { height } : styles.fill;

  return (
    <View style={[styles.container, box]}>
      {mode === 'map' ? (
        <MapView
          ref={mapRef}
          provider={PROVIDER_GOOGLE}
          style={StyleSheet.absoluteFill}
          showsUserLocation={live}
          showsMyLocationButton={live}
          userInterfaceStyle={isDark ? 'dark' : 'light'}
          initialRegion={
            focus
              ? {
                  latitude: focus.latitude,
                  longitude: focus.longitude,
                  latitudeDelta: 0.01,
                  longitudeDelta: 0.01,
                }
              : undefined
          }
          onMapReady={() => {
            if (!live && coords.length > 1) {
              mapRef.current?.fitToCoordinates(coords, {
                edgePadding: { top: 40, right: 40, bottom: 40, left: 40 },
                animated: false,
              });
            }
          }}
        >
          {coords.length > 1 && (
            <Polyline
              coordinates={coords}
              strokeColor={colors.primary}
              strokeWidth={5}
            />
          )}
          {coords.length > 0 && (
            <Marker coordinate={coords[0]} pinColor="green" title="Start" />
          )}
          {!live && coords.length > 1 && (
            <Marker
              coordinate={coords[coords.length - 1]}
              pinColor="red"
              title="End"
            />
          )}
        </MapView>
      ) : (
        <View style={[styles.fill, styles.sketch]}>
          <RouteSketch route={route} height={height ? height - 16 : 360} />
        </View>
      )}

      <View style={[styles.toggle, { backgroundColor: colors.surface }]}>
        {(['map', 'sketch'] as Mode[]).map(m => (
          <Pressable
            key={m}
            onPress={() => setMode(m)}
            style={[
              styles.toggleItem,
              mode === m && { backgroundColor: colors.primaryContainer },
            ]}
          >
            <Text
              style={[
                styles.toggleText,
                {
                  color:
                    mode === m
                      ? colors.onPrimaryContainer
                      : colors.onSurfaceVariant,
                },
              ]}
            >
              {m === 'map' ? 'Map' : 'Sketch'}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderRadius: radius.lg, overflow: 'hidden' },
  fill: { flex: 1 },
  sketch: { padding: spacing.sm, justifyContent: 'center' },
  toggle: {
    position: 'absolute',
    top: spacing.sm,
    left: spacing.sm,
    flexDirection: 'row',
    borderRadius: radius.xl,
    padding: 3,
    elevation: 3,
  },
  toggleItem: {
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.xl,
  },
  toggleText: { fontWeight: '600', fontSize: 12 },
});
