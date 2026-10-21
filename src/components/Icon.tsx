import React from 'react';
import { StyleSheet, Text } from 'react-native';

// Unicode / emoji glyphs. react-native-svg is intentionally not used: its Fabric components
// crash on React Native 0.88 Android at startup (see README, "Known limitations").
const GLYPHS = {
  dashboard: '▦',
  map: '🗺️',
  history: '🕘',
  pill: '💊',
  walk: '🚶',
  run: '🏃',
  stand: '🧍',
  route: '📍',
  sun: '☀️',
  gauge: '🌡️',
  play: '▶',
  stop: '■',
  add: '+',
  delete: '🗑️',
  share: '⤴',
  logout: '🚪',
  bell: '🔔',
  chevronRight: '›',
  warning: '⚠️',
  timer: '⏱️',
} as const;

export type IconName = keyof typeof GLYPHS;

export function Icon({
  name,
  size = 24,
  color,
}: {
  name: IconName;
  size?: number;
  color: string;
}) {
  return (
    <Text
      allowFontScaling={false}
      style={[
        styles.icon,
        { fontSize: size * 0.85, lineHeight: size, width: size, color },
      ]}
    >
      {GLYPHS[name]}
    </Text>
  );
}

const styles = StyleSheet.create({
  icon: { textAlign: 'center', includeFontPadding: false },
});
