<script setup lang="ts">
import type { ExerciseRow } from '~/composables/useExercises';
import type { PlanItemRow } from '~/composables/useTrainingPlan';

const { fetchProfile } = useProfile();
const { listExercises } = useExercises();
const { listPlanItems, addPlanItem, updatePlanItem, deletePlanItem, repositionAll } = useTrainingPlan();

const profile = ref<Awaited<ReturnType<typeof fetchProfile>>>(null);
const exercises = ref<ExerciseRow[]>([]);
const items = ref<PlanItemRow[]>([]);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const newExerciseId = ref('');
const newPlannedSets = ref(3);
const newRestSeconds = ref(60);

const headerTitle = computed(() => {
  const name = profile.value?.name?.trim();
  return name ? `${name} · 训练计划` : '训练计划';
});

const availableExercises = computed(() => {
  const used = new Set(items.value.map((it) => it.exercise_id));
  return exercises.value.filter((ex) => !used.has(ex.id));
});

const exerciseOptions = computed(() =>
  availableExercises.value.map((ex) => ({ label: ex.name, value: ex.id })),
);

async function loadData() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [profileData, exerciseList, planList] = await Promise.all([
      fetchProfile(),
      listExercises(),
      listPlanItems(),
    ]);
    profile.value = profileData;
    exercises.value = exerciseList;
    items.value = planList;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

function clearMessages() {
  errorMessage.value = '';
  successMessage.value = '';
}

async function onAddPlanItem() {
  clearMessages();
  if (!newExerciseId.value) {
    errorMessage.value = '请选择一个动作';
    return;
  }
  const sets = Math.max(1, Math.floor(Number(newPlannedSets.value) || 0));
  const rest = Math.max(0, Math.min(3600, Math.floor(Number(newRestSeconds.value) || 0)));
  saving.value = true;
  try {
    const created = await addPlanItem(newExerciseId.value, sets, rest, items.value.length);
    items.value = [...items.value, created];
    newExerciseId.value = '';
    newPlannedSets.value = 3;
    newRestSeconds.value = 60;
    successMessage.value = `已加入计划：${created.exercise_name}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '添加失败';
  } finally {
    saving.value = false;
  }
}

async function onChangeSets(item: PlanItemRow, delta: number) {
  clearMessages();
  const next = Math.max(0, Math.min(100, item.planned_sets + delta));
  if (next === item.planned_sets) return;
  const prev = item.planned_sets;
  item.planned_sets = next;
  try {
    await updatePlanItem(item.id, { planned_sets: next });
  } catch (error) {
    item.planned_sets = prev;
    errorMessage.value = error instanceof Error ? error.message : '保存失败';
  }
}

async function onChangeRest(item: PlanItemRow, delta: number) {
  clearMessages();
  const next = Math.max(0, Math.min(3600, item.rest_seconds + delta));
  if (next === item.rest_seconds) return;
  const prev = item.rest_seconds;
  item.rest_seconds = next;
  try {
    await updatePlanItem(item.id, { rest_seconds: next });
  } catch (error) {
    item.rest_seconds = prev;
    errorMessage.value = error instanceof Error ? error.message : '保存失败';
  }
}

async function onMove(item: PlanItemRow, delta: -1 | 1) {
  clearMessages();
  const index = items.value.findIndex((it) => it.id === item.id);
  const target = index + delta;
  if (index < 0 || target < 0 || target >= items.value.length) return;
  const next = items.value.slice();
  const [moved] = next.splice(index, 1);
  next.splice(target, 0, moved);
  const previous = items.value;
  items.value = next;
  try {
    await repositionAll(next);
    items.value.forEach((it, idx) => (it.position = idx));
  } catch (error) {
    items.value = previous;
    errorMessage.value = error instanceof Error ? error.message : '排序失败';
  }
}

async function onDelete(item: PlanItemRow) {
  clearMessages();
  const previous = items.value;
  items.value = items.value.filter((it) => it.id !== item.id);
  try {
    await deletePlanItem(item.id);
    await repositionAll(items.value);
    items.value.forEach((it, idx) => (it.position = idx));
    successMessage.value = `已从计划移除：${item.exercise_name}`;
  } catch (error) {
    items.value = previous;
    errorMessage.value = error instanceof Error ? error.message : '删除失败';
  }
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-4 sm:p-6">
    <UButton to="/" color="neutral" variant="ghost" icon="i-lucide-arrow-left" label="返回列表" class="mb-4" />

    <AppHeader :title="headerTitle" :show-logout="false" />

    <div v-if="loading" class="text-muted">加载中……</div>

    <template v-else>
      <UAlert v-if="errorMessage" color="error" :title="errorMessage" class="mb-4" />
      <UAlert v-if="successMessage" color="success" :title="successMessage" class="mb-4" />

      <UCard class="mb-4">
        <template #header>
          <div>
            <h2 class="font-medium">添加计划项</h2>
            <p class="text-sm text-muted mt-1">
              将动作加入力量训练计划，并指定计划组数与组间休息时间（秒）。Online 版 App 训练时会显示进度、允许增减组数，并在记录本组后自动按休息时间倒计时。
            </p>
          </div>
        </template>

        <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="onAddPlanItem">
          <USelectMenu
            v-model="newExerciseId"
            value-key="value"
            :items="exerciseOptions"
            placeholder="选择动作"
            class="flex-1"
          />
          <UInput
            v-model.number="newPlannedSets"
            type="number"
            :min="1"
            :max="100"
            class="sm:w-28"
            placeholder="计划组数"
          />
          <UInput
            v-model.number="newRestSeconds"
            type="number"
            :min="0"
            :max="3600"
            class="sm:w-32"
            placeholder="组间休息(秒)"
          />
          <UButton
            type="submit"
            :loading="saving"
            :disabled="!availableExercises.length"
            label="加入计划"
            class="sm:w-32"
          />
        </form>
        <p v-if="!availableExercises.length && exercises.length" class="text-sm text-muted mt-3">
          所有已有动作都已在计划中。可去
          <NuxtLink to="/exercises" class="text-primary underline">动作管理</NuxtLink>
          先添加新的动作。
        </p>
        <p v-else-if="!exercises.length" class="text-sm text-muted mt-3">
          还没有动作，请先去
          <NuxtLink to="/exercises" class="text-primary underline">动作管理</NuxtLink>
          添加。
        </p>
      </UCard>

      <UCard>
        <template #header>
          <h2 class="font-medium">当前计划（{{ items.length }}）</h2>
        </template>

        <p v-if="!items.length" class="text-muted">还没有计划项。</p>

        <div v-else class="overflow-x-auto">
          <div class="min-w-[34rem]">
            <div
              class="grid grid-cols-[minmax(0,1fr)_7rem_9rem_8.5rem] items-center gap-3 pb-2 border-b border-default text-xs font-medium text-muted"
            >
              <span>动作名称</span>
              <span class="text-center">组数</span>
              <span class="text-center">休息时间</span>
              <span class="text-center">操作</span>
            </div>

            <ul class="divide-y divide-default">
              <li
                v-for="(item, index) in items"
                :key="item.id"
                class="grid grid-cols-[minmax(0,1fr)_7rem_9rem_8.5rem] items-center gap-3 py-3"
              >
              <span class="font-medium min-w-0 truncate">
                <span class="text-muted">{{ index + 1 }}.</span> {{ item.exercise_name }}
              </span>
              <div class="flex items-center gap-1 justify-center">
                <UButton size="xs" variant="soft" color="neutral" label="-1" @click="onChangeSets(item, -1)" />
                <span class="font-mono w-8 text-center">{{ item.planned_sets }}</span>
                <UButton size="xs" variant="soft" color="neutral" label="+1" @click="onChangeSets(item, 1)" />
              </div>
              <div class="flex items-center gap-1 justify-center">
                <UButton size="xs" variant="soft" color="neutral" label="-10" @click="onChangeRest(item, -10)" />
                <span class="font-mono w-12 text-center">{{ item.rest_seconds }}s</span>
                <UButton size="xs" variant="soft" color="neutral" label="+10" @click="onChangeRest(item, 10)" />
              </div>
              <div class="flex items-center gap-1 justify-center">
                <UButton
                  size="xs"
                  variant="ghost"
                  color="neutral"
                  icon="i-lucide-arrow-up"
                  :disabled="index === 0"
                  @click="onMove(item, -1)"
                />
                <UButton
                  size="xs"
                  variant="ghost"
                  color="neutral"
                  icon="i-lucide-arrow-down"
                  :disabled="index === items.length - 1"
                  @click="onMove(item, 1)"
                />
                <UButton size="xs" variant="soft" color="error" label="移除" @click="onDelete(item)" />
              </div>
            </li>
            </ul>
          </div>
        </div>
      </UCard>
    </template>
  </div>
</template>
