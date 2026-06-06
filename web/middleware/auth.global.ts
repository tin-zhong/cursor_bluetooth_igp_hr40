export default defineNuxtRouteMiddleware(async (to) => {
  const publicPaths = ['/login', '/register', '/verify'];
  if (publicPaths.includes(to.path)) return;

  const supabase = useSupabaseClient();
  const { data: sessionData } = await supabase.auth.getSession();
  if (!sessionData.session) {
    return navigateTo('/login');
  }

  if (to.path === '/profile/setup') return;

  const { fetchProfile } = useProfile();
  try {
    const profile = await fetchProfile();
    if (!profile?.profile_completed) {
      return navigateTo('/profile/setup');
    }
  } catch {
    return navigateTo('/login');
  }
});
