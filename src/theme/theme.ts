import { useColorScheme } from 'react-native';
import type { ActivityType } from '../native/types';

// Material 3 inspired palette (indigo seed), mirrored in android res/values{,-night}/colors.xml.
const light = {
  primary: '#3F51B5',
  onPrimary: '#FFFFFF',
  primaryContainer: '#DEE0FF',
  onPrimaryContainer: '#00105C',
  secondaryContainer: '#E1E0F9',
  surface: '#FBF8FF',
  surfaceContainer: '#EFEDF4',
  surfaceContainerHigh: '#E9E7EF',
  onSurface: '#1B1B21',
  onSurfaceVariant: '#46464F',
  outline: '#777680',
  outlineVariant: '#C7C5D0',
  error: '#BA1A1A',
  errorContainer: '#FFDAD6',
  onErrorContainer: '#410002',
  success: '#2E7D32',
  activity: {
    stationary: '#78909C',
    walking: '#2E7D32',
    running: '#E65100',
  } as Record<ActivityType, string>,
  chart: {
    intensity: '#3F51B5',
    light: '#F9A825',
    pressure: '#00838F',
  },
};

export type ThemeColors = typeof light;

const dark: ThemeColors = {
  primary: '#BAC3FF',
  onPrimary: '#08218A',
  primaryContainer: '#293CA0',
  onPrimaryContainer: '#DEE0FF',
  secondaryContainer: '#444559',
  surface: '#121318',
  surfaceContainer: '#1F1F25',
  surfaceContainerHigh: '#292A2F',
  onSurface: '#E4E1E9',
  onSurfaceVariant: '#C7C5D0',
  outline: '#91909A',
  outlineVariant: '#46464F',
  error: '#FFB4AB',
  errorContainer: '#93000A',
  onErrorContainer: '#FFDAD6',
  success: '#81C784',
  activity: {
    stationary: '#B0BEC5',
    walking: '#81C784',
    running: '#FFB74D',
  },
  chart: {
    intensity: '#BAC3FF',
    light: '#FFD54F',
    pressure: '#4DD0E1',
  },
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 } as const;
export const radius = { sm: 8, md: 12, lg: 16, xl: 28 } as const;

export function useAppTheme() {
  const scheme = useColorScheme();
  const isDark = scheme === 'dark';
  return { colors: isDark ? dark : light, isDark };
}

export const ACTIVITY_LABELS: Record<ActivityType, string> = {
  stationary: 'Stationary',
  walking: 'Walking',
  running: 'Running',
};
