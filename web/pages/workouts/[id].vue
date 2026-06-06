<script setup lang="ts">
import { ZONE_LABELS } from '~/composables/useWorkoutStats';

const route = useRoute();
const supabase = useSupabaseClient();
const { fetchProfile } = useProfile();

const workout = ref<any>(null);
const samples = ref<any[]>([]);
const sets = ref<any[]>([]);
const profile = ref<any>(null);
const loading = ref(true);
const errorMessage = ref('');

const stats = computed(() =>
  calculateWorkoutStats(profile.value, samples.value, workout.value?.workout_type || 'aerobic'),
);

const maxHr = computed(() =>
  profile.value ? Math.max(120, 220 - profile.value.age) : 190,
);

onMounted(async () => {
  const workoutId = route.params.id as string;
  try {
    const [workoutResult, samplesResult, setsResult, profileData] = await Promise.all([
      supabase.from('workout_records').select('*').eq('id', workoutId).single(),
      supabase
        .from('heart_rate_samples')
        .select('*')
        .eq('workout_id', workoutId)
        .order('timestamp_millis', { ascending: true }),
      supabase
        .from('strength_sets')
        .select('*')
        .eq('workout_id', workoutId)
        .order('timestamp_millis', { ascending: true }),
      fetchProfile(),
    ]);

    if (workoutResult.error) throw workoutResult.error;
    if (samplesResult.error) throw samplesResult.error;
    if (setsResult.error) throw setsResult.error;

    workout.value = workoutResult.data;
    samples.value = samplesResult.data ?? [];
    sets.value = setsResult.data ?? [];
    profile.value = profileData;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="max-w-3xl mx-auto p-4 sm:p-6 space-y-4">
    <UButton to="/" color="neutral" variant="ghost" icon="i-lucide-arrow-left" label="返回列表" />

    <div v-if="loading" class="text-gray-500">加载详情中……</div>
    <UAlert v-else-if="errorMessage" color="error" :title="errorMessage" />

    <template v-else-if="workout">
      <UCard>
        <h1 class="text-xl font-semibold">{{ workoutTypeLabel(workout.workout_type) }}</h1>
        <p class="text-sm text-gray-500 mt-1">
          {{ new Date(workout.start_millis).toLocaleString('zh-CN') }}
        </p>
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-3 mt-4">
          <div class="rounded-lg bg-gray-50 p-3">
            <div class="text-xs text-gray-500">时长</div>
            <div class="font-semibold mt-1">
              {{ formatDuration(workout.start_millis, workout.end_millis) }}
            </div>
          </div>
          <div class="rounded-lg bg-gray-50 p-3">
            <div class="text-xs text-gray-500">平均心率</div>
            <div class="font-semibold mt-1">{{ stats.avgBpm || '-' }} bpm</div>
          </div>
          <div class="rounded-lg bg-gray-50 p-3">
            <div class="text-xs text-gray-500">估算消耗</div>
            <div class="font-semibold mt-1">{{ stats.calories.toFixed(1) }} kcal</div>
          </div>
        </div>
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">心率曲线</h2>
        <WorkoutChart :samples="samples" :max-hr="maxHr" />
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">心率区间</h2>
        <ul class="space-y-2 text-sm text-gray-700">
          <li v-for="(millis, index) in stats.zoneMillis" :key="index">
            {{ ZONE_LABELS[index] }}：{{ formatZoneDuration(millis) }}
          </li>
        </ul>
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">力量组</h2>
        <ul v-if="sets.length" class="space-y-2 text-sm">
          <li v-for="set in sets" :key="set.id">
            {{ set.exercise_name }} - {{ set.weight }}{{ set.weight_unit }} x {{ set.reps }}
          </li>
        </ul>
        <p v-else class="text-sm text-gray-500">无力量组记录</p>
      </UCard>
    </template>
  </div>
</template>
