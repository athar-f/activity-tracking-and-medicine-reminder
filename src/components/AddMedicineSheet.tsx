import React, { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import type { NewMedicine } from '../native/types';
import { radius, spacing, useAppTheme } from '../theme/theme';
import { formatTimeOfDay } from '../utils/format';
import { Button } from './ui';

interface Props {
  visible: boolean;
  onClose(): void;
  onSubmit(input: NewMedicine): Promise<void>;
}

const MINUTE_STEP = 5;
const LEAD_MINUTES = 15;

function reminderTime(hour: number, minute: number): string {
  const total = (hour * 60 + minute - LEAD_MINUTES + 24 * 60) % (24 * 60);
  return formatTimeOfDay(Math.floor(total / 60), total % 60);
}

/** Bottom sheet form for a new daily medicine. Time is picked with hour/minute steppers. */
export function AddMedicineSheet({ visible, onClose, onSubmit }: Props) {
  const { colors } = useAppTheme();
  const [name, setName] = useState('');
  const [dosage, setDosage] = useState('');
  const [notes, setNotes] = useState('');
  const [hour, setHour] = useState(8);
  const [minute, setMinute] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      // Default to the next round half-hour, at least 20 min ahead so the reminder is visible soon.
      const d = new Date(Date.now() + 20 * 60000);
      const m = Math.ceil(d.getMinutes() / MINUTE_STEP) * MINUTE_STEP;
      setHour((d.getHours() + Math.floor(m / 60)) % 24);
      setMinute(m % 60);
      setName('');
      setDosage('');
      setNotes('');
      setError(null);
    }
  }, [visible]);

  const save = async () => {
    if (!name.trim()) {
      setError('Please enter the medicine name');
      return;
    }
    setSaving(true);
    try {
      await onSubmit({
        name: name.trim(),
        dosage: dosage.trim(),
        notes: notes.trim(),
        hour,
        minute,
      });
      onClose();
    } catch (e: any) {
      setError(e?.message ?? 'Could not save');
    } finally {
      setSaving(false);
    }
  };

  const inputStyle = [
    styles.input,
    { borderColor: colors.outline, color: colors.onSurface },
  ];

  return (
    <Modal
      visible={visible}
      animationType="slide"
      transparent
      onRequestClose={onClose}
    >
      <Pressable style={styles.backdrop} onPress={onClose} />
      <KeyboardAvoidingView behavior="padding" style={styles.sheetWrap}>
        <View
          style={[styles.sheet, { backgroundColor: colors.surfaceContainer }]}
        >
          <View style={[styles.handle, { backgroundColor: colors.outline }]} />
          <Text style={[styles.title, { color: colors.onSurface }]}>
            Add medicine
          </Text>

          <TextInput
            placeholder="Medicine name"
            placeholderTextColor={colors.onSurfaceVariant}
            value={name}
            onChangeText={setName}
            style={inputStyle}
            autoFocus
          />
          <TextInput
            placeholder="Dosage (e.g. 1 tablet, 500 mg)"
            placeholderTextColor={colors.onSurfaceVariant}
            value={dosage}
            onChangeText={setDosage}
            style={inputStyle}
          />
          <TextInput
            placeholder="Notes (optional, e.g. after food)"
            placeholderTextColor={colors.onSurfaceVariant}
            value={notes}
            onChangeText={setNotes}
            style={inputStyle}
          />

          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>
            Daily dose time
          </Text>
          <View style={styles.timeRow}>
            <Stepper
              value={hour}
              onChange={v => setHour((v + 24) % 24)}
              step={1}
            />
            <Text style={[styles.timeText, { color: colors.onSurface }]}>
              {formatTimeOfDay(hour, minute)}
            </Text>
            <Stepper
              value={minute}
              onChange={v => setMinute((v + 60) % 60)}
              step={MINUTE_STEP}
            />
          </View>
          <Text style={[styles.hint, { color: colors.onSurfaceVariant }]}>
            You'll be notified at {reminderTime(hour, minute)}, 15 minutes
            before.
          </Text>

          {error ? (
            <Text style={[styles.error, { color: colors.error }]}>{error}</Text>
          ) : null}

          <View style={styles.buttons}>
            <Button
              label="Cancel"
              variant="text"
              onPress={onClose}
              style={styles.flex}
            />
            <Button
              label="Save"
              onPress={save}
              loading={saving}
              style={styles.flex}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

function Stepper({
  value,
  onChange,
  step,
}: {
  value: number;
  onChange(v: number): void;
  step: number;
}) {
  const { colors } = useAppTheme();
  const btn = (label: string, delta: number) => (
    <Pressable
      onPress={() => onChange(value + delta)}
      style={[styles.stepBtn, { backgroundColor: colors.secondaryContainer }]}
      accessibilityLabel={label === '+' ? 'Increase' : 'Decrease'}
    >
      <Text style={[styles.stepText, { color: colors.onSurface }]}>
        {label}
      </Text>
    </Pressable>
  );
  return (
    <View style={styles.stepper}>
      {btn('+', step)}
      {btn('−', -step)}
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  sheetWrap: { flex: 1, justifyContent: 'flex-end' },
  sheet: {
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    padding: spacing.xl,
    gap: spacing.md,
  },
  handle: {
    alignSelf: 'center',
    width: 32,
    height: 4,
    borderRadius: 2,
    opacity: 0.5,
  },
  title: { fontSize: 22, fontWeight: '700' },
  input: {
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    height: 52,
    fontSize: 16,
  },
  label: { fontSize: 13, fontWeight: '600', marginTop: spacing.sm },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  timeText: { fontSize: 44, fontWeight: '800', fontVariant: ['tabular-nums'] },
  stepper: { gap: spacing.sm },
  stepBtn: {
    width: 48,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { fontSize: 22, fontWeight: '700' },
  hint: { fontSize: 13 },
  error: { fontSize: 13 },
  buttons: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.sm },
  flex: { flex: 1 },
});
