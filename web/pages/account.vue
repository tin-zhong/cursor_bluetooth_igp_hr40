<script setup lang="ts">
const supabase = useSupabaseClient();
const user = useSupabaseUser();
const { fetchProfile, saveProfile } = useProfile();

const profile = ref<Awaited<ReturnType<typeof fetchProfile>>>(null);
const name = ref('');
const heightCm = ref('');
const weightKg = ref('');
const age = ref('');
const sex = ref<'male' | 'female'>('male');

const newPassword = ref('');
const confirmPassword = ref('');

const passwordModalOpen = ref(false);
const passwordConfirmOpen = ref(false);
const deleteModalOpen = ref(false);
const deleteConfirmOpen = ref(false);

const loading = ref(false);
const profileLoading = ref(false);
const message = ref('');
const errorMessage = ref('');

function applyProfileToForm(data: Awaited<ReturnType<typeof fetchProfile>>) {
  if (!data) return;
  name.value = data.name ?? '';
  heightCm.value = data.height_cm != null ? String(data.height_cm) : '';
  weightKg.value = data.weight_kg != null ? String(data.weight_kg) : '';
  age.value = data.age != null ? String(data.age) : '';
  sex.value = data.sex === 'female' ? 'female' : 'male';
}

onMounted(async () => {
  profile.value = await fetchProfile();
  applyProfileToForm(profile.value);
});

const headerTitle = computed(() => {
  const displayName = profile.value?.name?.trim() || name.value.trim();
  return displayName ? `${displayName} · 用户管理` : '用户管理';
});

function parseNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return Number.NaN;
  return Number(trimmed);
}

async function saveUserProfile() {
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

  profileLoading.value = true;
  errorMessage.value = '';
  message.value = '';
  try {
    await saveProfile({
      name: name.value.trim(),
      sex: sex.value,
      age: ageValue,
      height_cm: height,
      weight_kg: weight,
    });
    profile.value = await fetchProfile();
    applyProfileToForm(profile.value);
    message.value = '资料已保存';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败';
  } finally {
    profileLoading.value = false;
  }
}

function openPasswordFlow() {
  newPassword.value = '';
  confirmPassword.value = '';
  errorMessage.value = '';
  passwordModalOpen.value = true;
}

function requestPasswordConfirm() {
  if (newPassword.value.length < 6) {
    errorMessage.value = '新密码至少 6 位';
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致';
    return;
  }
  errorMessage.value = '';
  passwordModalOpen.value = false;
  passwordConfirmOpen.value = true;
}

async function changePassword() {
  loading.value = true;
  errorMessage.value = '';
  const { error } = await supabase.auth.updateUser({ password: newPassword.value });
  loading.value = false;
  passwordConfirmOpen.value = false;
  if (error) {
    errorMessage.value = error.message;
    return;
  }
  message.value = '密码已更新';
}

function openDeleteFlow() {
  errorMessage.value = '';
  deleteModalOpen.value = true;
}

function requestDeleteConfirm() {
  deleteModalOpen.value = false;
  deleteConfirmOpen.value = true;
}

async function deleteAccount() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const { error } = await supabase.rpc('delete_account');
    if (error) throw error;
    await supabase.auth.signOut();
    if (process.client) {
      window.location.href = `${useRouter().options.history.base || '/'}login`;
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注销失败';
    deleteConfirmOpen.value = false;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="max-w-6xl mx-auto p-4 sm:p-6">
    <UButton to="/" color="neutral" variant="ghost" icon="i-lucide-arrow-left" label="返回列表" class="mb-4" />

    <AppHeader
      :title="headerTitle"
      :subtitle="user?.email || undefined"
    />

    <UAlert v-if="message" color="success" :title="message" class="mb-4" />
    <UAlert v-if="errorMessage" color="error" :title="errorMessage" class="mb-4" />

    <div class="space-y-4">
      <UCard>
        <h2 class="font-medium mb-3">运动人员资料</h2>
        <form class="space-y-4" @submit.prevent="saveUserProfile">
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
          <UButton type="submit" :loading="profileLoading" label="保存资料" />
        </form>
      </UCard>

      <UCard>
        <h2 class="font-medium mb-2">账户信息</h2>
        <p class="text-sm text-gray-600">邮箱：{{ user?.email }}</p>
      </UCard>

      <UCard>
        <h2 class="font-medium mb-3">安全设置</h2>
        <UButton label="修改密码" variant="soft" @click="openPasswordFlow" />
      </UCard>

      <UCard>
        <h2 class="font-medium mb-2 text-red-700">危险操作</h2>
        <p class="text-sm text-gray-500 mb-3">
          注销后将删除你的全部训练记录，且无法恢复。
        </p>
        <UButton color="error" variant="soft" label="注销账户" @click="openDeleteFlow" />
      </UCard>
    </div>

    <UModal v-model:open="passwordModalOpen">
      <template #header><span class="font-semibold">修改密码</span></template>
      <template #body>
        <div class="space-y-3">
          <UFormField label="新密码">
            <UInput v-model="newPassword" type="password" minlength="6" class="w-full" />
          </UFormField>
          <UFormField label="确认新密码">
            <UInput v-model="confirmPassword" type="password" minlength="6" class="w-full" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <UButton label="下一步" block @click="requestPasswordConfirm" />
      </template>
    </UModal>

    <UModal v-model:open="passwordConfirmOpen" :dismissible="!loading">
      <template #header><span class="font-semibold">确认修改密码</span></template>
      <template #body>
        <p>确定要将密码更新为新密码吗？</p>
      </template>
      <template #footer>
        <div class="flex gap-2">
          <UButton
            label="取消"
            color="neutral"
            variant="soft"
            class="flex-1"
            @click="passwordConfirmOpen = false"
          />
          <UButton
            label="确认修改"
            class="flex-1"
            :loading="loading"
            @click="changePassword"
          />
        </div>
      </template>
    </UModal>

    <UModal v-model:open="deleteModalOpen">
      <template #header><span class="font-semibold text-red-700">注销账户</span></template>
      <template #body>
        <p class="text-gray-700">
          注销会永久删除你的账户及全部运动记录。是否继续？
        </p>
      </template>
      <template #footer>
        <UButton color="error" label="继续注销" block @click="requestDeleteConfirm" />
      </template>
    </UModal>

    <UModal v-model:open="deleteConfirmOpen" :dismissible="!loading">
      <template #header><span class="font-semibold text-red-700">最终确认</span></template>
      <template #body>
        <p class="text-gray-700">
          这是最后一步。确认后你的账户和所有训练数据将被立即删除，且无法恢复。
        </p>
      </template>
      <template #footer>
        <div class="flex gap-2">
          <UButton
            label="取消"
            color="neutral"
            variant="soft"
            class="flex-1"
            @click="deleteConfirmOpen = false"
          />
          <UButton
            label="确认注销"
            color="error"
            class="flex-1"
            :loading="loading"
            @click="deleteAccount"
          />
        </div>
      </template>
    </UModal>
  </div>
</template>
