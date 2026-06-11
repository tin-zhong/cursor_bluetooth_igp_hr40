<script setup lang="ts">
import { use } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import VChart from 'vue-echarts';
import type { HeartRateSample } from '~/composables/useWorkoutStats';

use([LineChart, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer]);

const ZONE_COLORS = ['#64B5F6', '#66BB6A', '#FFCA28', '#FB8C00', '#E53935'];

const props = defineProps<{
  samples: HeartRateSample[];
  maxHr: number;
}>();

function zoneIndex(bpm: number, maxHr: number) {
  const ratio = bpm / maxHr;
  if (ratio < 0.6) return 0;
  if (ratio < 0.7) return 1;
  if (ratio < 0.8) return 2;
  if (ratio < 0.9) return 3;
  return 4;
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

const hasData = computed(() => props.samples.length > 0);

const option = computed(() => {
  if (!hasData.value) {
    return {
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无心率数据', fill: '#666', fontSize: 14 },
      },
    };
  }

  const startTime = props.samples[0].timestamp_millis;
  const data = props.samples.map((s) => [s.timestamp_millis - startTime, s.bpm]);
  const maxHr = props.maxHr;

  // Zone boundaries as absolute bpm, so the line is colored by heart-rate zone.
  const pieces = [
    { lt: 0.6 * maxHr, color: ZONE_COLORS[0] },
    { gte: 0.6 * maxHr, lt: 0.7 * maxHr, color: ZONE_COLORS[1] },
    { gte: 0.7 * maxHr, lt: 0.8 * maxHr, color: ZONE_COLORS[2] },
    { gte: 0.8 * maxHr, lt: 0.9 * maxHr, color: ZONE_COLORS[3] },
    { gte: 0.9 * maxHr, color: ZONE_COLORS[4] },
  ];

  return {
    animation: false,
    grid: { top: 16, right: 16, bottom: 32, left: 48 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#999', type: 'dashed' } },
      backgroundColor: 'rgba(33, 33, 33, 0.85)',
      borderWidth: 0,
      padding: [6, 10],
      textStyle: { color: '#ccc', fontSize: 11 },
      formatter: (params: any) => {
        const point = Array.isArray(params) ? params[0] : params;
        const elapsedMs = point.value[0] as number;
        const bpm = point.value[1] as number;
        const color = ZONE_COLORS[zoneIndex(bpm, maxHr)];
        return (
          `<div style="color:#ccc;font-size:11px">${formatElapsed(elapsedMs)}</div>` +
          `<div style="color:${color};font-weight:bold;font-size:13px">${bpm} bpm</div>`
        );
      },
    },
    visualMap: {
      show: false,
      type: 'piecewise',
      dimension: 1,
      seriesIndex: 0,
      pieces,
      outOfRange: { color: ZONE_COLORS[0] },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: data[data.length - 1][0],
      axisLabel: {
        color: '#888',
        fontSize: 11,
        formatter: (value: number) => formatElapsed(value),
      },
      axisLine: { lineStyle: { color: '#e0e0e0' } },
      splitLine: { lineStyle: { color: '#e0e0e0' } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#888', fontSize: 11 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#e0e0e0' } },
    },
    series: [
      {
        type: 'line',
        data,
        showSymbol: false,
        symbolSize: 8,
        lineStyle: { width: 2 },
        emphasis: { focus: 'series', scale: true },
        sampling: 'lttb',
      },
    ],
  };
});
</script>

<template>
  <!--
    vue-echarts renders a <x-vue-echarts> custom element with an injected,
    unlayered `height: 100%` rule. Tailwind's h-* utilities live in @layer
    utilities and lose to unlayered rules, so the height must be set inline.
  -->
  <VChart class="w-full" :style="{ height: '16rem' }" :option="option" autoresize />
</template>
