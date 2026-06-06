<script setup lang="ts">
definePageMeta({ layout: false });

const email = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

const supabase = useSupabaseClient();

async function onSubmit() {
  loading.value = true;
  errorMessage.value = '';
  const { error } = await supabase.auth.signInWithPassword({
    email: email.value.trim(),
    password: password.value,
  });
  loading.value = false;
  if (error) {
    errorMessage.value = error.message;
    return;
  }
  await navigateTo('/');
}
</script>

<template>
  <AuthShell>
    <UCard class="w-full max-w-md">
      <template #header>
        <div>
          <h1 class="text-xl font-semibold">登录</h1>
          <p class="text-sm text-muted mt-1">登录后跨设备查看训练历史</p>
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
            autocomplete="current-password"
            class="w-full"
          />
        </UFormField>
        <p v-if="errorMessage" class="text-sm text-error">{{ errorMessage }}</p>
        <UButton type="submit" block :loading="loading" label="登录" />
      </form>

      <template #footer>
        <p class="text-sm text-center text-muted">
          还没有账号？
          <NuxtLink to="/register" class="text-primary font-medium">去注册</NuxtLink>
        </p>
      </template>
    </UCard>
  </AuthShell>
</template>
