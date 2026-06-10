<script setup lang="ts">
import type { HeartRateSample } from '~/composables/useWorkoutStats';

const ZONE_COLORS = ['#64B5F6', '#66BB6A', '#FFCA28', '#FB8C00', '#E53935'];

const props = defineProps<{
  samples: HeartRateSample[];
  maxHr: number;
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);

let hoverIndex = -1;
let layout: {
  padding: { top: number; right: number; bottom: number; left: number };
  plotWidth: number;
  plotHeight: number;
  minTime: number;
  timeSpan: number;
  yMin: number;
  bpmSpan: number;
} | null = null;

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
    layout = null;
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

  layout = { padding, plotWidth, plotHeight, minTime, timeSpan, yMin, bpmSpan };

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

  if (hoverIndex >= 0 && hoverIndex < props.samples.length) {
    drawHover(ctx, width, xForTime, yForBpm);
  }
}

function drawHover(
  ctx: CanvasRenderingContext2D,
  width: number,
  xForTime: (time: number) => number,
  yForBpm: (bpm: number) => number,
) {
  if (!layout) return;
  const sample = props.samples[hoverIndex];
  const { padding, plotHeight, minTime } = layout;
  const x = xForTime(sample.timestamp_millis);
  const y = yForBpm(sample.bpm);
  const color = ZONE_COLORS[zoneIndex(sample.bpm, props.maxHr)];

  ctx.strokeStyle = '#999';
  ctx.lineWidth = 1;
  ctx.setLineDash([4, 4]);
  ctx.beginPath();
  ctx.moveTo(x, padding.top);
  ctx.lineTo(x, padding.top + plotHeight);
  ctx.stroke();
  ctx.setLineDash([]);

  ctx.fillStyle = color;
  ctx.strokeStyle = '#fff';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(x, y, 5, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();

  const bpmText = `${sample.bpm} bpm`;
  const timeText = formatElapsed(sample.timestamp_millis - minTime);
  ctx.font = 'bold 13px sans-serif';
  const bpmWidth = ctx.measureText(bpmText).width;
  ctx.font = '11px sans-serif';
  const timeWidth = ctx.measureText(timeText).width;
  const boxWidth = Math.max(bpmWidth, timeWidth) + 16;
  const boxHeight = 40;
  let boxX = x + 10;
  if (boxX + boxWidth > width - padding.right) {
    boxX = x - 10 - boxWidth;
  }
  let boxY = y - boxHeight - 10;
  if (boxY < padding.top) {
    boxY = y + 10;
  }

  ctx.fillStyle = 'rgba(33, 33, 33, 0.85)';
  ctx.beginPath();
  ctx.roundRect(boxX, boxY, boxWidth, boxHeight, 6);
  ctx.fill();

  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  ctx.fillStyle = '#fff';
  ctx.font = 'bold 13px sans-serif';
  ctx.fillText(bpmText, boxX + 8, boxY + 7);
  ctx.fillStyle = '#ccc';
  ctx.font = '11px sans-serif';
  ctx.fillText(timeText, boxX + 8, boxY + 24);
}

function nearestSampleIndex(x: number) {
  if (!layout || !props.samples.length) return -1;
  const { padding, plotWidth, minTime, timeSpan } = layout;
  if (x < padding.left - 8 || x > padding.left + plotWidth + 8) return -1;
  const time = minTime + ((x - padding.left) / Math.max(1, plotWidth)) * timeSpan;
  let lo = 0;
  let hi = props.samples.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (props.samples[mid].timestamp_millis < time) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  if (lo > 0) {
    const prev = props.samples[lo - 1].timestamp_millis;
    const curr = props.samples[lo].timestamp_millis;
    if (Math.abs(time - prev) < Math.abs(curr - time)) {
      return lo - 1;
    }
  }
  return lo;
}

function onMouseMove(event: MouseEvent) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const index = nearestSampleIndex(event.clientX - rect.left);
  if (index !== hoverIndex) {
    hoverIndex = index;
    draw();
  }
}

function onMouseLeave() {
  if (hoverIndex !== -1) {
    hoverIndex = -1;
    draw();
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

watch(
  () => props.samples,
  () => {
    hoverIndex = -1;
    draw();
  },
  { deep: true },
);
</script>

<template>
  <canvas
    ref="canvasRef"
    class="w-full h-64 cursor-crosshair"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
  />
</template>
