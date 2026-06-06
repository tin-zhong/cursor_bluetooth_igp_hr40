const ZONE_COLORS = ['#64B5F6', '#66BB6A', '#FFCA28', '#FB8C00', '#E53935'];

function zoneIndex(bpm, maxHr) {
  const ratio = bpm / maxHr;
  if (ratio < 0.6) return 0;
  if (ratio < 0.7) return 1;
  if (ratio < 0.8) return 2;
  if (ratio < 0.9) return 3;
  return 4;
}

export function drawHeartRateChart(canvas, samples, maxHr) {
  const ctx = canvas.getContext('2d');
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, width, height);

  if (!samples.length) {
    ctx.fillStyle = '#666';
    ctx.font = '14px sans-serif';
    ctx.fillText('暂无心率数据', 16, height / 2);
    return;
  }

  const padding = { top: 16, right: 16, bottom: 28, left: 40 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const minTime = samples[0].timestamp_millis;
  const maxTime = samples[samples.length - 1].timestamp_millis;
  const timeSpan = Math.max(1, maxTime - minTime);

  let minBpm = samples[0].bpm;
  let maxBpm = samples[0].bpm;
  for (const sample of samples) {
    minBpm = Math.min(minBpm, sample.bpm);
    maxBpm = Math.max(maxBpm, sample.bpm);
  }
  const bpmPadding = 8;
  const yMin = Math.max(40, minBpm - bpmPadding);
  const yMax = Math.min(220, maxBpm + bpmPadding);
  const bpmSpan = Math.max(1, yMax - yMin);

  const xForTime = (time) => padding.left + ((time - minTime) / timeSpan) * plotWidth;
  const yForBpm = (bpm) => padding.top + plotHeight - ((bpm - yMin) / bpmSpan) * plotHeight;

  ctx.strokeStyle = '#e0e0e0';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (plotHeight / 4) * i;
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(width - padding.right, y);
    ctx.stroke();
  }

  ctx.fillStyle = '#666';
  ctx.font = '12px sans-serif';
  ctx.textAlign = 'right';
  for (let i = 0; i <= 4; i++) {
    const bpm = Math.round(yMax - (bpmSpan / 4) * i);
    const y = padding.top + (plotHeight / 4) * i;
    ctx.fillText(String(bpm), padding.left - 8, y + 4);
  }

  for (let i = 0; i < samples.length - 1; i++) {
    const current = samples[i];
    const next = samples[i + 1];
    ctx.strokeStyle = ZONE_COLORS[zoneIndex(current.bpm, maxHr)];
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xForTime(current.timestamp_millis), yForBpm(current.bpm));
    ctx.lineTo(xForTime(next.timestamp_millis), yForBpm(next.bpm));
    ctx.stroke();
  }

  ctx.fillStyle = '#666';
  ctx.textAlign = 'center';
  ctx.fillText('时间', width / 2, height - 6);
}
