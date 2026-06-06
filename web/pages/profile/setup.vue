<script setup lang="ts">
definePageMeta({ layout: false });

const name = ref('');
const heightCm = ref(170);
const weightKg = ref(70);
const age = ref(30);
const sex = ref<'male' | 'female'>('male');
const loading = ref(false);
const errorMessage = ref('');

const { saveProfile } = useProfile();

async function onSubmit() {
  if (!name.value.trim()) {
    errorMessage.value = '请填写姓名或昵称';
    return;
  }
  if (heightCm.value < 80 || heightCm.value > 240) {
    errorMessage.value = '请填写合理的身高';
    return;
  }
  if (weightKg.value < 20 || weightKg.value > 250) {
    errorMessage.value = '请填写合理的体重';
    return;
  }
  if (age.value < 10 || age.value > 100) {
    errorMessage.value = '请填写合理的年龄';
    return;
  }

  loading.value = true;
  errorMessage.value = '';
  try {
    await saveProfile({
      name: name.value.trim(),
      sex: sex.value,
      age: age.value,
      height_cm: heightCm.value,
      weight_kg: weightKg.value,
    });
    await navigateTo('/');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center p-4 bg-gray-50">
    <UCard class="w-full max-w-lg">
      <template #header>
        <div>
          <h1 class="text-xl font-semibold">完善运动资料</h1>
          <p class="text-sm text-gray-500 mt-1">
            新用户需填写资料，用于估算最大心率、心率区间和能量消耗。
          </p>
        </div>
      </template>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <UFormField label="姓名或昵称" required>
          <UInput v-model="name" class="w-full" />
        </UFormField>
        <div class="grid grid-cols-2 gap-4">
          <UFormField label="身高 (cm)" required>
            <UInput v-model.number="heightCm" type="number" class="w-full" />
          </UFormField>
          <UFormField label="体重 (kg)" required>
            <UInput v-model.number="weightKg" type="number" step="0.1" class="w-full" />
          </UFormField>
        </div>
        <UFormField label="年龄" required>
          <UInput v-model.number="age" type="number" class="w-full" />
        </UFormField>
        <UFormField label="性别" required>
          <URadioGroup
            v-model="sex"
            :items="[
              { label: '男', value: 'male' },
              { label: '女', value: 'female' },
            ]"
          />
        </UFormField>
        <p v-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>
        <UButton type="submit" block :loading="loading" label="保存并继续" />
      </form>
    </UCard>
  </div>
</template>
