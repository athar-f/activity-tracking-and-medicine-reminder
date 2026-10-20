import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { radius, spacing, useAppTheme } from '../theme/theme';
import { Icon, type IconName } from './Icon';

export function Card({
  children,
  style,
  onPress,
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  onPress?: () => void;
}) {
  const { colors } = useAppTheme();
  const base = [
    styles.card,
    { backgroundColor: colors.surfaceContainer },
    style,
  ];
  if (!onPress) return <View style={base}>{children}</View>;
  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: colors.outlineVariant }}
      style={base}
    >
      {children}
    </Pressable>
  );
}

export function SectionTitle({ children }: { children: React.ReactNode }) {
  const { colors } = useAppTheme();
  return (
    <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
      {children}
    </Text>
  );
}

export function StatCard({
  icon,
  label,
  value,
  caption,
  accent,
  style,
}: {
  icon: IconName;
  label: string;
  value: string;
  caption?: string;
  accent?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useAppTheme();
  const tint = accent ?? colors.primary;
  return (
    <Card style={[styles.stat, style]}>
      <View style={styles.statHeader}>
        <Icon name={icon} size={18} color={tint} />
        <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>
          {label}
        </Text>
      </View>
      <Text
        style={[styles.statValue, { color: colors.onSurface }]}
        numberOfLines={1}
        adjustsFontSizeToFit
      >
        {value}
      </Text>
      {caption ? (
        <Text
          style={[styles.statCaption, { color: colors.onSurfaceVariant }]}
          numberOfLines={1}
        >
          {caption}
        </Text>
      ) : null}
    </Card>
  );
}

type ButtonVariant = 'filled' | 'tonal' | 'text' | 'danger';

export function Button({
  label,
  icon,
  onPress,
  variant = 'filled',
  loading,
  disabled,
  style,
}: {
  label: string;
  icon?: IconName;
  onPress: () => void;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useAppTheme();
  const palette: Record<ButtonVariant, { bg: string; fg: string }> = {
    filled: { bg: colors.primary, fg: colors.onPrimary },
    tonal: { bg: colors.secondaryContainer, fg: colors.onSurface },
    text: { bg: 'transparent', fg: colors.primary },
    danger: { bg: colors.errorContainer, fg: colors.onErrorContainer },
  };
  const { bg, fg } = palette[variant];
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      disabled={inactive}
      android_ripple={{ color: colors.outlineVariant, borderless: false }}
      style={[
        styles.button,
        { backgroundColor: bg },
        inactive && styles.inactive,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <>
          {icon ? <Icon name={icon} size={20} color={fg} /> : null}
          <Text style={[styles.buttonLabel, { color: fg }]}>{label}</Text>
        </>
      )}
    </Pressable>
  );
}

export function Banner({
  message,
  actionLabel,
  onAction,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  const { colors } = useAppTheme();
  return (
    <View style={[styles.banner, { backgroundColor: colors.errorContainer }]}>
      <Icon name="warning" size={20} color={colors.onErrorContainer} />
      <Text style={[styles.bannerText, { color: colors.onErrorContainer }]}>
        {message}
      </Text>
      {actionLabel && onAction ? (
        <Pressable onPress={onAction} hitSlop={8}>
          <Text
            style={[styles.bannerAction, { color: colors.onErrorContainer }]}
          >
            {actionLabel}
          </Text>
        </Pressable>
      ) : null}
    </View>
  );
}

export function EmptyState({
  icon,
  title,
  body,
}: {
  icon: IconName;
  title: string;
  body: string;
}) {
  const { colors } = useAppTheme();
  return (
    <View style={styles.empty}>
      <Icon name={icon} size={48} color={colors.outline} />
      <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>
        {title}
      </Text>
      <Text style={[styles.emptyBody, { color: colors.onSurfaceVariant }]}>
        {body}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg, padding: spacing.lg, overflow: 'hidden' },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
  },
  stat: { flex: 1, minWidth: 0, gap: spacing.xs },
  statHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  statLabel: { fontSize: 13, fontWeight: '500' },
  statValue: { fontSize: 24, fontWeight: '700', fontVariant: ['tabular-nums'] },
  statCaption: { fontSize: 12 },
  button: {
    minHeight: 48,
    borderRadius: radius.xl,
    paddingHorizontal: spacing.xl,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
    overflow: 'hidden',
  },
  buttonLabel: { fontSize: 15, fontWeight: '600' },
  inactive: { opacity: 0.6 },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: radius.md,
    marginBottom: spacing.md,
  },
  bannerText: { flex: 1, fontSize: 13 },
  bannerAction: { fontWeight: '700', fontSize: 13 },
  empty: {
    alignItems: 'center',
    paddingVertical: 48,
    paddingHorizontal: spacing.xl,
    gap: spacing.sm,
  },
  emptyTitle: { fontSize: 18, fontWeight: '600', marginTop: spacing.sm },
  emptyBody: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
});
