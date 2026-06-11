<script setup lang="ts">
import { ZONE_LABELS } from '~/composables/useWorkoutStats';

const route = useRoute();
const router = useRouter();
const supabase = useSupabaseClient();
const { fetchProfile } = useProfile();

const workout = ref<any>(null);
const samples = ref<any[]>([]);
const sets = ref<any[]>([]);
const profile = ref<any>(null);
const loading = ref(true);
const errorMessage = ref('');

const deleteModalOpen = ref(false);
const deleting = ref(false);

const stats = computed(() =>
  calculateWorkoutStats(profile.value, samples.value, workout.value?.workout_type || 'aerobic'),
);

const maxHr = computed(() =>
  profile.value ? Math.max(120, 220 - profile.value.age) : 190,
);

async function fetchAllSamples(workoutId: string) {
  const pageSize = 1000;
  const all: any[] = [];
  for (let from = 0; ; from += pageSize) {
    const { data, error } = await supabase
      .from('heart_rate_samples')
      .select('*')
      .eq('workout_id', workoutId)
      .order('timestamp_millis', { ascending: true })
      .range(from, from + pageSize - 1);
    if (error) throw error;
    const rows = data ?? [];
    all.push(...rows);
    if (rows.length < pageSize) break;
  }
  return all;
}

onMounted(async () => {
  const workoutId = route.params.id as string;
  try {
    const [workoutResult, samplesData, setsResult, profileData] = await Promise.all([
      supabase.from('workout_records').select('*').eq('id', workoutId).single(),
      fetchAllSamples(workoutId),
      supabase
        .from('strength_sets')
        .select('*')
        .eq('workout_id', workoutId)
        .order('timestamp_millis', { ascending: true }),
      fetchProfile(),
    ]);

    if (workoutResult.error) throw workoutResult.error;
    if (setsResult.error) throw setsResult.error;

    workout.value = workoutResult.data;
    samples.value = samplesData;
    sets.value = setsResult.data ?? [];
    profile.value = profileData;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
});

async function confirmDelete() {
  if (!workout.value) return;
  deleting.value = true;
  errorMessage.value = '';
  try {
    const { error } = await supabase
      .from('workout_records')
      .delete()
      .eq('id', workout.value.id);
    if (error) throw error;
    deleteModalOpen.value = false;
    await router.push('/');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败';
  } finally {
    deleting.value = false;
  }
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-4 sm:p-6 space-y-4">
    <UButton to="/" color="neutral" variant="ghost" icon="i-lucide-arrow-left" label="返回列表" />

    <div v-if="loading" class="text-muted">加载详情中……</div>
    <UAlert v-else-if="errorMessage" color="error" :title="errorMessage" />

    <template v-else-if="workout">
      <UCard>
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <h1 class="text-xl font-semibold">{{ workoutTypeLabel(workout.workout_type) }}</h1>
            <p class="text-sm text-muted mt-1">
              {{ new Date(workout.start_millis).toLocaleString('zh-CN') }}
            </p>
          </div>
          <UButton
            color="error"
            variant="soft"
            size="sm"
            icon="i-lucide-trash-2"
            label="删除"
            @click="deleteModalOpen = true"
          />
        </div>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
          <div class="rounded-lg bg-elevated/50 border border-default p-3">
            <div class="text-xs text-muted">时长</div>
            <div class="font-semibold mt-1 text-default">
              {{ formatDuration(workout.start_millis, workout.end_millis) }}
            </div>
          </div>
          <div class="rounded-lg bg-elevated/50 border border-default p-3">
            <div class="text-xs text-muted">平均心率</div>
            <div class="font-semibold mt-1 text-default">{{ stats.avgBpm || '-' }} bpm</div>
          </div>
          <div class="rounded-lg bg-elevated/50 border border-default p-3">
            <div class="text-xs text-muted">最高心率</div>
            <div class="font-semibold mt-1 text-default">{{ stats.maxBpm || '-' }} bpm</div>
          </div>
          <div class="rounded-lg bg-elevated/50 border border-default p-3">
            <div class="text-xs text-muted">估算消耗</div>
            <div class="font-semibold mt-1 text-default">{{ stats.calories.toFixed(1) }} kcal</div>
          </div>
        </div>
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">心率曲线</h2>
        <WorkoutChart :samples="samples" :max-hr="maxHr" />
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">心率区间</h2>
        <ul class="space-y-2 text-sm text-default">
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
        <p v-else class="text-sm text-muted">无力量组记录</p>
      </UCard>
    </template>

    <UModal v-model:open="deleteModalOpen" :dismissible="!deleting">
      <template #header>
        <span class="font-semibold text-error">删除运动记录</span>
      </template>
      <template #body>
        <p class="text-muted">
          确定要删除这条运动记录吗？该操作不可恢复，关联的心率采样和力量组也会一并清除。
        </p>
      </template>
      <template #footer>
        <div class="flex gap-2 w-full">
          <UButton
            label="取消"
            color="neutral"
            variant="soft"
            class="flex-1"
            :disabled="deleting"
            @click="deleteModalOpen = false"
          />
          <UButton
            label="确认删除"
            color="error"
            class="flex-1"
            :loading="deleting"
            @click="confirmDelete"
          />
        </div>
      </template>
    </UModal>
  </div>
</template>
