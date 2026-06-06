export interface UserProfileRow {
  id: string;
  name: string;
  sex: 'male' | 'female';
  age: number;
  height_cm: number;
  weight_kg: number;
  profile_completed: boolean;
}

export function useProfile() {
  const supabase = useSupabaseClient();
  const user = useSupabaseUser();

  async function fetchProfile(): Promise<UserProfileRow | null> {
    if (!user.value) return null;
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', user.value.id)
      .maybeSingle();
    if (error) throw error;
    return data;
  }

  async function saveProfile(payload: {
    name: string;
    sex: 'male' | 'female';
    age: number;
    height_cm: number;
    weight_kg: number;
  }) {
    if (!user.value) throw new Error('未登录');
    const { error } = await supabase
      .from('profiles')
      .upsert({
        id: user.value.id,
        ...payload,
        profile_completed: true,
        updated_at: new Date().toISOString(),
      });
    if (error) throw error;
  }

  return { fetchProfile, saveProfile };
}
