export interface PlanItemRow {
  id: string;
  exercise_id: string;
  exercise_name: string;
  planned_sets: number;
  position: number;
}

interface RawPlanRow {
  id: string;
  exercise_id: string;
  planned_sets: number;
  position: number;
  exercises: { name: string } | { name: string }[] | null;
}

function normalize(row: RawPlanRow): PlanItemRow {
  const ex = Array.isArray(row.exercises) ? row.exercises[0] : row.exercises;
  return {
    id: row.id,
    exercise_id: row.exercise_id,
    exercise_name: ex?.name ?? '',
    planned_sets: row.planned_sets,
    position: row.position,
  };
}

export function useTrainingPlan() {
  const supabase = useSupabaseClient();
  const user = useSupabaseUser();

  async function listPlanItems(): Promise<PlanItemRow[]> {
    const { data, error } = await supabase
      .from('training_plan_items')
      .select('id,exercise_id,planned_sets,position,exercises(name)')
      .order('position', { ascending: true });
    if (error) throw error;
    return ((data ?? []) as unknown as RawPlanRow[]).map(normalize);
  }

  async function addPlanItem(exerciseId: string, plannedSets: number, position: number) {
    if (!user.value) throw new Error('未登录');
    const { data, error } = await supabase
      .from('training_plan_items')
      .insert({
        user_id: user.value.id,
        exercise_id: exerciseId,
        planned_sets: plannedSets,
        position,
      })
      .select('id,exercise_id,planned_sets,position,exercises(name)')
      .single();
    if (error) {
      if (error.code === '23505') throw new Error('该动作已在计划中');
      throw error;
    }
    return normalize(data as unknown as RawPlanRow);
  }

  async function updatePlanItem(id: string, patch: { planned_sets?: number; position?: number }) {
    const { error } = await supabase.from('training_plan_items').update(patch).eq('id', id);
    if (error) throw error;
  }

  async function deletePlanItem(id: string) {
    const { error } = await supabase.from('training_plan_items').delete().eq('id', id);
    if (error) throw error;
  }

  async function repositionAll(items: PlanItemRow[]) {
    await Promise.all(
      items.map((item, index) =>
        item.position === index ? Promise.resolve() : updatePlanItem(item.id, { position: index }),
      ),
    );
  }

  return { listPlanItems, addPlanItem, updatePlanItem, deletePlanItem, repositionAll };
}
