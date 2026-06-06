export const ZONE_LABELS = [
  '恢复区 <60%',
  '燃脂区 60-70%',
  '有氧区 70-80%',
  '阈值区 80-90%',
  '极限区 >=90%',
] as const;

const MIN_HR_FOR_CALORIES = 60;
const STRENGTH_ACTIVITY_COEFFICIENT = 0.88;
const KCAL_PER_KJ = 4.184;

export interface Profile {
  name: string;
  sex: 'male' | 'female';
  age: number;
  height_cm: number;
  weight_kg: number;
}

export interface HeartRateSample {
  timestamp_millis: number;
  bpm: number;
}

function caloriesPerMinute(profile: Profile, bpm: number) {
  const kilojoulesPerMinute =
    profile.sex === 'female'
      ? -20.4022 + 0.4472 * bpm - 0.1263 * profile.weight_kg + 0.074 * profile.age
      : -55.0969 + 0.6309 * bpm + 0.1988 * profile.weight_kg + 0.2017 * profile.age;
  return Math.max(0, kilojoulesPerMinute / KCAL_PER_KJ);
}

function caloriesForSegment(profile: Profile, current: HeartRateSample, next: HeartRateSample) {
  const deltaMillis = Math.max(0, next.timestamp_millis - current.timestamp_millis);
  if (deltaMillis === 0) return 0;
  const averageBpm = (current.bpm + next.bpm) / 2;
  if (averageBpm < MIN_HR_FOR_CALORIES) return 0;
  return caloriesPerMinute(profile, averageBpm) * (deltaMillis / 60000);
}

export function estimateCalories(
  profile: Profile | null,
  samples: HeartRateSample[],
  workoutType: string,
) {
  if (!profile || samples.length < 2) return 0;
  let calories = 0;
  for (let i = 0; i < samples.length - 1; i++) {
    calories += caloriesForSegment(profile, samples[i], samples[i + 1]);
  }
  if (workoutType === 'strength') calories *= STRENGTH_ACTIVITY_COEFFICIENT;
  return calories;
}

function zoneIndex(bpm: number, maxHr: number) {
  const ratio = bpm / maxHr;
  if (ratio < 0.6) return 0;
  if (ratio < 0.7) return 1;
  if (ratio < 0.8) return 2;
  if (ratio < 0.9) return 3;
  return 4;
}

export function calculateWorkoutStats(
  profile: Profile | null,
  samples: HeartRateSample[],
  workoutType: string,
) {
  if (!samples.length) {
    return {
      minBpm: 0,
      maxBpm: 0,
      avgBpm: 0,
      calories: 0,
      zoneMillis: Array(ZONE_LABELS.length).fill(0),
      sampleCount: 0,
    };
  }

  let min = Number.POSITIVE_INFINITY;
  let max = 0;
  let sum = 0;
  for (const sample of samples) {
    min = Math.min(min, sample.bpm);
    max = Math.max(max, sample.bpm);
    sum += sample.bpm;
  }

  const maxHr = profile ? Math.max(120, 220 - profile.age) : 190;
  const zoneMillis = Array(ZONE_LABELS.length).fill(0);
  for (let i = 0; i < samples.length - 1; i++) {
    const delta = Math.max(0, samples[i + 1].timestamp_millis - samples[i].timestamp_millis);
    zoneMillis[zoneIndex(samples[i].bpm, maxHr)] += delta;
  }

  return {
    minBpm: min,
    maxBpm: max,
    avgBpm: Math.round(sum / samples.length),
    calories: estimateCalories(profile, samples, workoutType),
    zoneMillis,
    sampleCount: samples.length,
  };
}

export function formatDuration(startMillis: number, endMillis: number) {
  const totalSeconds = Math.max(0, Math.floor((endMillis - startMillis) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function formatZoneDuration(millis: number) {
  const totalSeconds = Math.floor(millis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}分${seconds}秒`;
}

export function workoutTypeLabel(type: string) {
  return type === 'strength' ? '力量训练' : '有氧运动';
}
