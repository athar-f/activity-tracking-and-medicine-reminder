import React, { useCallback, useState } from 'react';
import { Alert, Pressable, StyleSheet, Switch, Text, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { AddMedicineSheet } from '../components/AddMedicineSheet';
import { Icon } from '../components/Icon';
import { Screen } from '../components/Screen';
import { Banner, Button, Card, EmptyState } from '../components/ui';
import { ReminderService } from '../native/modules';
import type { Medicine, NewMedicine, ReminderStatus } from '../native/types';
import { radius, spacing, useAppTheme } from '../theme/theme';
import { formatRelative, formatTimeOfDay } from '../utils/format';

export function MedicinesScreen() {
  const { colors } = useAppTheme();
  const [medicines, setMedicines] = useState<Medicine[]>([]);
  const [status, setStatus] = useState<ReminderStatus | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const [list, s] = await Promise.all([
        ReminderService.getMedicines(),
        ReminderService.getStatus(),
      ]);
      setMedicines(list);
      setStatus(s);
    } catch (e: any) {
      setError(e?.message ?? 'Could not load medicines');
    } finally {
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const add = async (input: NewMedicine) => {
    const created = await ReminderService.addMedicine(input);
    setMedicines(prev =>
      [...prev, created].sort(
        (a, b) => a.hour * 60 + a.minute - (b.hour * 60 + b.minute),
      ),
    );
  };

  const toggle = async (m: Medicine, enabled: boolean) => {
    setMedicines(prev =>
      prev.map(x => (x.id === m.id ? { ...x, enabled } : x)),
    );
    try {
      const updated = await ReminderService.setEnabled(m.id, enabled);
      setMedicines(prev => prev.map(x => (x.id === m.id ? updated : x)));
    } catch (e: any) {
      setError(e?.message ?? 'Update failed');
      load();
    }
  };

  const remove = (m: Medicine) =>
    Alert.alert(`Delete ${m.name}?`, 'Its reminder will be cancelled.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await ReminderService.deleteMedicine(m.id);
          setMedicines(prev => prev.filter(x => x.id !== m.id));
        },
      },
    ]);

  const testReminder = async () => {
    await ReminderService.scheduleTest(10);
    Alert.alert(
      'Test reminder scheduled',
      'A notification will arrive in 10 seconds. Close or swipe away the app to verify it works in the background.',
    );
  };

  const lead = status?.leadTimeMinutes ?? 15;

  return (
    <Screen
      title="Medicines"
      subtitle={`Reminders arrive ${lead} min before each dose`}
      refreshing={refreshing}
      onRefresh={load}
    >
      {error ? (
        <Banner
          message={error}
          actionLabel="Dismiss"
          onAction={() => setError(null)}
        />
      ) : null}
      {status && !status.notificationsEnabled ? (
        <Banner message="Notifications are disabled, so reminders cannot be shown." />
      ) : null}
      {status && !status.exactAlarmsAllowed ? (
        <Banner
          message="Exact alarms are off; reminders may arrive a few minutes late."
          actionLabel="Allow"
          onAction={ReminderService.openExactAlarmSettings}
        />
      ) : null}

      <View style={styles.actions}>
        <Button
          label="Add medicine"
          icon="add"
          onPress={() => setSheetOpen(true)}
          style={styles.flex}
        />
        <Button
          label="Test"
          icon="bell"
          variant="tonal"
          onPress={testReminder}
        />
      </View>

      {medicines.length === 0 && !refreshing ? (
        <EmptyState
          icon="pill"
          title="No medicines yet"
          body="Add a medicine and its daily time. Reminders work offline, even when the app is closed."
        />
      ) : (
        <View style={styles.list}>
          {medicines.map(m => (
            <Card key={m.id} style={styles.item}>
              <View
                style={[
                  styles.timeBadge,
                  { backgroundColor: colors.primaryContainer },
                ]}
              >
                <Text
                  style={[styles.time, { color: colors.onPrimaryContainer }]}
                >
                  {formatTimeOfDay(m.hour, m.minute)}
                </Text>
              </View>
              <View style={styles.flex}>
                <Text style={[styles.name, { color: colors.onSurface }]}>
                  {m.name}
                </Text>
                {m.dosage ? (
                  <Text
                    style={[styles.meta, { color: colors.onSurfaceVariant }]}
                  >
                    {m.dosage}
                  </Text>
                ) : null}
                <Text
                  style={[
                    styles.meta,
                    { color: m.enabled ? colors.primary : colors.outline },
                  ]}
                >
                  {m.enabled && m.nextReminderAt
                    ? `Next reminder ${formatRelative(m.nextReminderAt)}`
                    : 'Reminder paused'}
                </Text>
              </View>
              <Switch
                value={m.enabled}
                onValueChange={v => toggle(m, v)}
                trackColor={{
                  true: colors.primary,
                  false: colors.outlineVariant,
                }}
                thumbColor={colors.surface}
              />
              <Pressable
                onPress={() => remove(m)}
                hitSlop={10}
                accessibilityLabel={`Delete ${m.name}`}
              >
                <Icon name="delete" size={22} color={colors.onSurfaceVariant} />
              </Pressable>
            </Card>
          ))}
        </View>
      )}

      <AddMedicineSheet
        visible={sheetOpen}
        onClose={() => setSheetOpen(false)}
        onSubmit={add}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  actions: { flexDirection: 'row', gap: spacing.md, marginBottom: spacing.lg },
  flex: { flex: 1 },
  list: { gap: spacing.md },
  item: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  timeBadge: {
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  time: { fontSize: 18, fontWeight: '800', fontVariant: ['tabular-nums'] },
  name: { fontSize: 16, fontWeight: '600' },
  meta: { fontSize: 13, marginTop: 2 },
});
