<script setup lang="ts">
import {
  CalendarDate,
  DateFormatter,
  getLocalTimeZone,
  type DateValue,
} from '@internationalized/date';

const props = defineProps<{
  label: string;
  placeholder: string;
}>();

const model = defineModel<string>({ default: '' });

const calendarValue = shallowRef<DateValue | undefined>();
const df = new DateFormatter('zh-CN', { dateStyle: 'medium' });

function toIsoDate(value: DateValue) {
  const date = value.toDate(getLocalTimeZone());
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function isoToCalendarDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) return undefined;
  return new CalendarDate(year, month, day);
}

watch(calendarValue, (value) => {
  model.value = value ? toIsoDate(value) : '';
});

watch(model, (value) => {
  calendarValue.value = value ? isoToCalendarDate(value) : undefined;
}, { immediate: true });

const displayText = computed(() => {
  if (!model.value) return null;
  const calendarDate = isoToCalendarDate(model.value);
  if (!calendarDate) return null;
  return df.format(calendarDate.toDate(getLocalTimeZone()));
});
</script>

<template>
  <div class="flex items-center gap-3 min-w-0">
    <span class="text-sm text-default shrink-0">{{ props.label }}</span>
    <UPopover>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-calendar"
        class="min-w-44 justify-start font-normal"
      >
        <span :class="displayText ? 'text-default' : 'text-muted'">
          {{ displayText ?? props.placeholder }}
        </span>
      </UButton>
      <template #content>
        <UCalendar v-model="calendarValue" class="p-2" />
      </template>
    </UPopover>
  </div>
</template>
