<script setup lang="ts">
import type { HeartRateSample } from '~/composables/useWorkoutStats';

const ZONE_COLORS = ['#64B5F6', '#66BB6A', '#FFCA28', '#FB8C00', '#E53935'];

const props = defineProps<{
  samples: HeartRateSample[];
  maxHr: number;
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);

function zoneIndex(bpm: number, maxHr: number) {
  const ratio = bpm / maxHr;
  if (ratio < 0.6) return 0;
  if (ratio < 0.7) return 1;
  if (ratio < 0.8) return 2;
  if (ratio < 0.9) return 3;
  return 4;
}

function draw() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, width, height);

  if (!props.samples.length) {
    ctx.fillStyle = '#666';
    ctx.font = '14px sans-serif';
    ctx.fillText('暂无心率数据', 16, height / 2);
    return;
  }

  const padding = { top: 16, right: 16, bottom: 32, left: 48 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const minTime = props.samples[0].timestamp_millis;
  const maxTime = props.samples[props.samples.length - 1].timestamp_millis;
  const timeSpan = Math.max(1, maxTime - minTime);

  let minBpm = props.samples[0].bpm;
  let maxBpm = props.samples[0].bpm;
  for (const sample of props.samples) {
    minBpm = Math.min(minBpm, sample.bpm);
    maxBpm = Math.max(maxBpm, sample.bpm);
  }

  const bpmStep = niceStep((maxBpm - minBpm + 16) / 5, [5, 10, 20, 25, 50, 100]);
  const yMin = Math.max(0, Math.floor((minBpm - 4) / bpmStep) * bpmStep);
  const yMax = Math.min(240, Math.ceil((maxBpm + 4) / bpmStep) * bpmStep);
  const bpmSpan = Math.max(1, yMax - yMin);

  const timeStepSec = niceStep(timeSpan / 1000 / 6, [
    5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600,
  ]);
  const timeStepMs = timeStepSec * 1000;

  const xForTime = (time: number) => padding.left + ((time - minTime) / timeSpan) * plotWidth;
  const yForBpm = (bpm: number) => padding.top + plotHeight - ((bpm - yMin) / bpmSpan) * plotHeight;

  ctx.font = '11px sans-serif';
  ctx.fillStyle = '#888';
  ctx.strokeStyle = '#e0e0e0';
  ctx.lineWidth = 1;

  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  for (let bpm = yMin; bpm <= yMax + 1e-6; bpm += bpmStep) {
    const y = yForBpm(bpm);
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(width - padding.right, y);
    ctx.stroke();
    ctx.fillText(String(Math.round(bpm)), padding.left - 6, y);
  }

  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  const firstTick = Math.ceil(0 / timeStepMs) * timeStepMs;
  for (let t = firstTick; t <= timeSpan + 1e-6; t += timeStepMs) {
    const x = xForTime(minTime + t);
    ctx.beginPath();
    ctx.moveTo(x, padding.top);
    ctx.lineTo(x, padding.top + plotHeight);
    ctx.stroke();
    ctx.fillText(formatElapsed(t), x, padding.top + plotHeight + 6);
  }

  for (let i = 0; i < props.samples.length - 1; i++) {
    const current = props.samples[i];
    const next = props.samples[i + 1];
    ctx.strokeStyle = ZONE_COLORS[zoneIndex(current.bpm, props.maxHr)];
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xForTime(current.timestamp_millis), yForBpm(current.bpm));
    ctx.lineTo(xForTime(next.timestamp_millis), yForBpm(next.bpm));
    ctx.stroke();
  }
}

function niceStep(target: number, steps: number[]) {
  for (const step of steps) {
    if (step >= target) return step;
  }
  return steps[steps.length - 1];
}

function formatElapsed(ms: number) {
  const totalSeconds = Math.round(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

onMounted(() => {
  draw();
  window.addEventListener('resize', draw);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', draw);
});

watch(() => props.samples, draw, { deep: true });
</script>

<template>
  <canvas ref="canvasRef" class="w-full h-64" />
</template>
