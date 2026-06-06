export interface ExerciseRow {
  id: string;
  user_id: string;
  name: string;
  created_at: string;
  updated_at: string;
}

export function useExercises() {
  const supabase = useSupabaseClient();
  const user = useSupabaseUser();

  async function listExercises(): Promise<ExerciseRow[]> {
    const { data, error } = await supabase
      .from('exercises')
      .select('*')
      .order('name', { ascending: true });
    if (error) throw error;
    return data ?? [];
  }

  async function addExercise(name: string): Promise<ExerciseRow> {
    if (!user.value) throw new Error('未登录');
    const trimmed = name.trim();
    if (!trimmed) throw new Error('动作名称不能为空');

    const { data, error } = await supabase
      .from('exercises')
      .insert({
        user_id: user.value.id,
        name: trimmed,
      })
      .select()
      .single();

    if (error) {
      if (error.code === '23505') {
        throw new Error('该动作名称已存在');
      }
      throw error;
    }
    return data;
  }

  async function deleteExercise(id: string) {
    const { error } = await supabase.from('exercises').delete().eq('id', id);
    if (error) throw error;
  }

  return { listExercises, addExercise, deleteExercise };
}
