<script setup lang="ts">
definePageMeta({ layout: false });

const name = ref('');
const heightCm = ref('');
const weightKg = ref('');
const age = ref('');
const sex = ref<'male' | 'female'>('male');
const loading = ref(false);
const errorMessage = ref('');

const { saveProfile } = useProfile();

function parseNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return Number.NaN;
  return Number(trimmed);
}

async function onSubmit() {
  if (!name.value.trim()) {
    errorMessage.value = '请填写姓名或昵称';
    return;
  }

  const height = parseNumber(heightCm.value);
  const weight = parseNumber(weightKg.value);
  const ageValue = parseNumber(age.value);

  if (Number.isNaN(height) || height < 80 || height > 240) {
    errorMessage.value = '请填写合理的身高';
    return;
  }
  if (Number.isNaN(weight) || weight < 20 || weight > 250) {
    errorMessage.value = '请填写合理的体重';
    return;
  }
  if (Number.isNaN(ageValue) || ageValue < 10 || ageValue > 100) {
    errorMessage.value = '请填写合理的年龄';
    return;
  }

  loading.value = true;
  errorMessage.value = '';
  try {
    await saveProfile({
      name: name.value.trim(),
      sex: sex.value,
      age: ageValue,
      height_cm: height,
      weight_kg: weight,
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
  <AuthShell>
    <UCard class="w-full max-w-lg">
      <template #header>
        <div>
          <h1 class="text-xl font-semibold">完善运动资料</h1>
          <p class="text-sm text-muted mt-1">
            新用户需填写资料，用于估算最大心率、心率区间和能量消耗。
          </p>
        </div>
      </template>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <UFormField label="姓名或昵称" required>
          <UInput v-model="name" class="w-full" placeholder="请输入姓名或昵称" />
        </UFormField>
        <div class="grid grid-cols-2 gap-4">
          <UFormField label="身高 (cm)" required>
            <UInput
              v-model="heightCm"
              type="text"
              inputmode="numeric"
              class="w-full"
              placeholder="请输入身高"
            />
          </UFormField>
          <UFormField label="体重 (kg)" required>
            <UInput
              v-model="weightKg"
              type="text"
              inputmode="decimal"
              class="w-full"
              placeholder="请输入体重"
            />
          </UFormField>
        </div>
        <UFormField label="年龄" required>
          <UInput
            v-model="age"
            type="text"
            inputmode="numeric"
            class="w-full"
            placeholder="请输入年龄"
          />
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
        <p v-if="errorMessage" class="text-sm text-error">{{ errorMessage }}</p>
        <UButton type="submit" block :loading="loading" label="保存并继续" />
      </form>
    </UCard>
  </AuthShell>
</template>
