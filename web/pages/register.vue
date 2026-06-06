<script setup lang="ts">
definePageMeta({ layout: false });

const email = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');
const successOpen = ref(false);

const supabase = useSupabaseClient();

function verifyRedirectUrl() {
  if (!import.meta.client) return undefined;
  const base = useRouter().options.history.base || '/';
  const normalizedBase = base.endsWith('/') ? base : `${base}/`;
  return `${window.location.origin}${normalizedBase}verify`;
}

async function onSubmit() {
  loading.value = true;
  errorMessage.value = '';
  const { error } = await supabase.auth.signUp({
    email: email.value.trim(),
    password: password.value,
    options: {
      emailRedirectTo: verifyRedirectUrl(),
    },
  });
  loading.value = false;
  if (error) {
    errorMessage.value = error.message;
    return;
  }
  successOpen.value = true;
}
</script>

<template>
  <AuthShell>
    <UCard class="w-full max-w-md">
      <template #header>
        <div>
          <h1 class="text-xl font-semibold">注册</h1>
          <p class="text-sm text-muted mt-1">创建账号以同步和查看运动记录</p>
        </div>
      </template>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <UFormField label="邮箱">
          <UInput v-model="email" type="email" required autocomplete="username" class="w-full" />
        </UFormField>
        <UFormField label="密码">
          <UInput
            v-model="password"
            type="password"
            required
            minlength="6"
            autocomplete="new-password"
            class="w-full"
          />
        </UFormField>
        <p v-if="errorMessage" class="text-sm text-error">{{ errorMessage }}</p>
        <UButton type="submit" block :loading="loading" label="注册" />
      </form>

      <template #footer>
        <p class="text-sm text-center text-muted">
          已有账号？
          <NuxtLink to="/login" class="text-primary font-medium">去登录</NuxtLink>
        </p>
      </template>
    </UCard>

    <UModal v-model:open="successOpen" :dismissible="false">
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-circle-check" class="size-5 text-green-600" />
          <span class="font-semibold">注册成功</span>
        </div>
      </template>
      <template #body>
        <p class="text-muted leading-relaxed">
          账号已创建。若开启了邮箱验证，请前往邮箱点击确认链接；验证完成后即可登录并填写运动资料。
        </p>
      </template>
      <template #footer>
        <UButton label="我知道了" block @click="navigateTo('/login')" />
      </template>
    </UModal>
  </AuthShell>
</template>
