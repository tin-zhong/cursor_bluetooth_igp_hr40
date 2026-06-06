<script setup lang="ts">
definePageMeta({ layout: false });

type Step = 'verifying' | 'success' | 'error';

const step = ref<Step>('verifying');
const errorMessage = ref('');

const supabase = useSupabaseClient();

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const code = params.get('code');
  const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const hasHashToken = hashParams.has('access_token');

  try {
    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) throw error;
    } else if (hasHashToken) {
      const { error } = await supabase.auth.getSession();
      if (error) throw error;
    } else {
      throw new Error('验证链接无效或已过期');
    }

    await supabase.auth.signOut();
    step.value = 'success';
    window.history.replaceState({}, document.title, `${useRouter().options.history.base || '/'}verify`);
  } catch (error) {
    step.value = 'error';
    errorMessage.value = error instanceof Error ? error.message : '邮箱验证失败';
  }
});
</script>

<template>
  <AuthShell>
    <UCard class="w-full max-w-md text-center">
      <div v-if="step === 'verifying'" class="space-y-4 py-8">
        <UIcon name="i-lucide-loader-circle" class="size-10 mx-auto animate-spin text-primary" />
        <h1 class="text-xl font-semibold">正在验证邮箱</h1>
        <p class="text-muted">请稍候，我们正在确认你的邮箱地址……</p>
      </div>

      <div v-else-if="step === 'success'" class="space-y-4 py-6">
        <UIcon name="i-lucide-circle-check" class="size-12 mx-auto text-green-600" />
        <h1 class="text-xl font-semibold">邮箱验证成功</h1>
        <p class="text-muted">你的邮箱已确认，现在可以登录并完善运动资料。</p>
        <UButton label="前往登录" block class="mt-4" to="/login" />
      </div>

      <div v-else class="space-y-4 py-6">
        <UIcon name="i-lucide-circle-x" class="size-12 mx-auto text-red-600" />
        <h1 class="text-xl font-semibold">验证失败</h1>
        <p class="text-muted">{{ errorMessage }}</p>
        <UButton label="返回登录" block variant="soft" to="/login" />
      </div>
    </UCard>
  </AuthShell>
</template>
