<script setup lang="ts">
const supabase = useSupabaseClient();
const { fetchProfile } = useProfile();

const profile = ref<Awaited<ReturnType<typeof fetchProfile>>>(null);
const workouts = ref<any[]>([]);
const loading = ref(true);
const errorMessage = ref('');
const startDate = ref('');
const endDate = ref('');

const headerTitle = computed(() => {
  const name = profile.value?.name?.trim();
  return name ? `${name} · 运动记录` : '运动记录';
});

const filteredWorkouts = computed(() => {
  if (!startDate.value && !endDate.value) {
    return workouts.value;
  }

  const startMillis = startDate.value
    ? new Date(`${startDate.value}T00:00:00`).getTime()
    : Number.NEGATIVE_INFINITY;
  const endMillis = endDate.value
    ? new Date(`${endDate.value}T23:59:59.999`).getTime()
    : Number.POSITIVE_INFINITY;

  return workouts.value.filter((workout) => {
    const workoutStart = workout.start_millis as number;
    return workoutStart >= startMillis && workoutStart <= endMillis;
  });
});

onMounted(async () => {
  try {
    profile.value = await fetchProfile();
    const { data, error } = await supabase
      .from('workout_records')
      .select('*')
      .order('start_millis', { ascending: false });
    if (error) throw error;
    workouts.value = data ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
});

function formatDateTime(millis: number) {
  return new Date(millis).toLocaleString('zh-CN');
}

function clearDateFilter() {
  startDate.value = '';
  endDate.value = '';
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-4 sm:p-6">
    <AppHeader :title="headerTitle">
      <template #actions>
        <UButton to="/exercises" color="neutral" variant="ghost" label="动作管理" />
        <UButton to="/account" color="neutral" variant="ghost" label="用户管理" />
      </template>
    </AppHeader>

    <UCard class="mb-4">
      <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
        <DatePickerField
          v-model="startDate"
          label="开始日期"
          placeholder="选择开始日期"
        />
        <DatePickerField
          v-model="endDate"
          label="结束日期"
          placeholder="选择结束日期"
        />
        <UButton
          color="neutral"
          variant="soft"
          label="清除筛选"
          :disabled="!startDate && !endDate"
          @click="clearDateFilter"
        />
        <p class="text-sm text-muted ml-auto">
          已筛选 {{ filteredWorkouts.length }} / {{ workouts.length }} 条记录
        </p>
      </div>
    </UCard>

    <div v-if="loading" class="text-muted">加载中……</div>
    <UAlert v-else-if="errorMessage" color="error" :title="errorMessage" />
    <UCard v-else-if="!workouts.length">
      <p class="text-muted">暂无训练记录。请先在 App 中同步数据。</p>
    </UCard>
    <UCard v-else-if="!filteredWorkouts.length">
      <p class="text-muted">当前日期范围内没有训练记录。</p>
    </UCard>
    <div v-else class="space-y-3">
      <NuxtLink
        v-for="workout in filteredWorkouts"
        :key="workout.id"
        :to="`/workouts/${workout.id}`"
        class="block"
      >
        <UCard class="hover:ring-2 hover:ring-primary/20 transition">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h2 class="font-medium">{{ workoutTypeLabel(workout.workout_type) }}</h2>
              <p class="text-sm text-muted mt-1">{{ formatDateTime(workout.start_millis) }}</p>
            </div>
            <div class="text-sm text-default">
              {{ formatDuration(workout.start_millis, workout.end_millis) }}
            </div>
          </div>
        </UCard>
      </NuxtLink>
    </div>
  </div>
</template>
