<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
}>();

const supabase = useSupabaseClient();

async function logout() {
  await supabase.auth.signOut();
  await navigateTo('/login');
}
</script>

<template>
  <div class="flex items-center justify-between gap-4 mb-6">
    <div>
      <h1 class="text-2xl font-semibold text-highlighted">{{ title }}</h1>
      <p v-if="subtitle" class="text-sm text-muted mt-1">{{ subtitle }}</p>
    </div>
    <div class="flex items-center gap-2">
      <slot name="actions" />
      <UButton color="neutral" variant="soft" label="退出" @click="logout" />
    </div>
  </div>
</template>
