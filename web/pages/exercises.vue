<script setup lang="ts">
import type { ExerciseRow } from '~/composables/useExercises';

const { fetchProfile } = useProfile();
const { listExercises, addExercise, deleteExercise } = useExercises();

const profile = ref<Awaited<ReturnType<typeof fetchProfile>>>(null);
const exercises = ref<ExerciseRow[]>([]);
const newExerciseName = ref('');
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const deleteModalOpen = ref(false);
const deleteTarget = ref<ExerciseRow | null>(null);
const deleting = ref(false);

const headerTitle = computed(() => {
  const name = profile.value?.name?.trim();
  return name ? `${name} · 动作管理` : '动作管理';
});

async function loadData() {
  loading.value = true;
  errorMessage.value = '';
  try {
    profile.value = await fetchProfile();
    exercises.value = await listExercises();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

async function onAddExercise() {
  const name = newExerciseName.value.trim();
  if (!name) {
    errorMessage.value = '请输入动作名称';
    return;
  }

  saving.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const created = await addExercise(name);
    exercises.value = [...exercises.value, created].sort((a, b) =>
      a.name.localeCompare(b.name, 'zh-CN'),
    );
    newExerciseName.value = '';
    successMessage.value = `已添加：${created.name}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '添加失败';
  } finally {
    saving.value = false;
  }
}

function openDeleteModal(exercise: ExerciseRow) {
  deleteTarget.value = exercise;
  deleteModalOpen.value = true;
}

async function confirmDelete() {
  if (!deleteTarget.value) return;
  deleting.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await deleteExercise(deleteTarget.value.id);
    exercises.value = exercises.value.filter((item) => item.id !== deleteTarget.value?.id);
    successMessage.value = `已删除：${deleteTarget.value.name}`;
    deleteModalOpen.value = false;
    deleteTarget.value = null;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败';
  } finally {
    deleting.value = false;
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
            <h2 class="font-medium">添加动作</h2>
            <p class="text-sm text-muted mt-1">动作名称会保存到云端，后续可与 Online 版 App 同步。</p>
          </div>
        </template>

        <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="onAddExercise">
          <UInput
            v-model="newExerciseName"
            class="flex-1"
            placeholder="例如：卧推、深蹲、引体向上"
          />
          <UButton type="submit" :loading="saving" label="添加" class="sm:w-28" />
        </form>
      </UCard>

      <UCard>
        <template #header>
          <h2 class="font-medium">我的动作（{{ exercises.length }}）</h2>
        </template>

        <p v-if="!exercises.length" class="text-muted">还没有动作，先在上方添加一个吧。</p>

        <ul v-else class="divide-y divide-default">
          <li
            v-for="exercise in exercises"
            :key="exercise.id"
            class="flex items-center justify-between gap-3 py-3"
          >
            <span class="font-medium">{{ exercise.name }}</span>
            <UButton
              color="error"
              variant="soft"
              size="sm"
              label="删除"
              @click="openDeleteModal(exercise)"
            />
          </li>
        </ul>
      </UCard>
    </template>

    <UModal v-model:open="deleteModalOpen" :dismissible="!deleting">
      <template #header>
        <span class="font-semibold text-error">删除动作</span>
      </template>
      <template #body>
        <p class="text-muted">
          确定要删除「{{ deleteTarget?.name }}」吗？删除后 Online 版同步时也会移除该动作。
        </p>
      </template>
      <template #footer>
        <div class="flex gap-2">
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
