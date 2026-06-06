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

  const padding = { top: 16, right: 16, bottom: 28, left: 40 };
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

  const yMin = Math.max(40, minBpm - 8);
  const yMax = Math.min(220, maxBpm + 8);
  const bpmSpan = Math.max(1, yMax - yMin);

  const xForTime = (time: number) => padding.left + ((time - minTime) / timeSpan) * plotWidth;
  const yForBpm = (bpm: number) => padding.top + plotHeight - ((bpm - yMin) / bpmSpan) * plotHeight;

  ctx.strokeStyle = '#e0e0e0';
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (plotHeight / 4) * i;
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(width - padding.right, y);
    ctx.stroke();
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
