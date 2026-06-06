import { authRedirectUrl, supabase } from './supabase.js';
import { drawHeartRateChart } from './chart.js';
import {
  calculateWorkoutStats,
  formatDuration,
  formatZoneDuration,
} from './stats.js';

const app = document.getElementById('app');
let authMode = 'login';

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function formatDateTime(millis) {
  return new Date(millis).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function workoutTypeLabel(type) {
  return type === 'strength' ? '力量训练' : '有氧运动';
}

async function getSession() {
  const { data } = await supabase.auth.getSession();
  return data.session;
}

async function fetchProfile(userId) {
  const { data, error } = await supabase
    .from('profiles')
    .select('*')
    .eq('id', userId)
    .maybeSingle();
  if (error) {
    throw error;
  }
  return data;
}

async function fetchWorkouts() {
  const { data, error } = await supabase
    .from('workout_records')
    .select('*')
    .order('start_millis', { ascending: false });
  if (error) {
    throw error;
  }
  return data ?? [];
}

async function fetchWorkoutDetail(workoutId) {
  const [workoutResult, samplesResult, setsResult, session] = await Promise.all([
    supabase.from('workout_records').select('*').eq('id', workoutId).single(),
    supabase
      .from('heart_rate_samples')
      .select('*')
      .eq('workout_id', workoutId)
      .order('timestamp_millis', { ascending: true }),
    supabase
      .from('strength_sets')
      .select('*')
      .eq('workout_id', workoutId)
      .order('timestamp_millis', { ascending: true }),
    getSession(),
  ]);

  if (workoutResult.error) {
    throw workoutResult.error;
  }
  if (samplesResult.error) {
    throw samplesResult.error;
  }
  if (setsResult.error) {
    throw setsResult.error;
  }

  const profile = session?.user ? await fetchProfile(session.user.id) : null;
  return {
    workout: workoutResult.data,
    samples: samplesResult.data ?? [],
    sets: setsResult.data ?? [],
    profile,
  };
}

function renderAuth(message = '') {
  app.innerHTML = `
    <div class="card stack" style="max-width: 420px; margin: 48px auto;">
      <div>
        <h1 style="margin: 0 0 8px;">HR40 运动记录</h1>
        <p class="muted" style="margin: 0;">登录后跨设备查看训练历史</p>
      </div>
      <div class="auth-toggle">
        <button class="${authMode === 'login' ? 'active' : 'secondary'}" data-mode="login">登录</button>
        <button class="${authMode === 'signup' ? 'active' : 'secondary'}" data-mode="signup">注册</button>
      </div>
      <form id="auth-form" class="stack">
        <label>
          邮箱
          <input name="email" type="email" required autocomplete="username" />
        </label>
        <label>
          密码
          <input name="password" type="password" required minlength="6" autocomplete="current-password" />
        </label>
        ${message ? `<div class="error">${escapeHtml(message)}</div>` : ''}
        <button type="submit">${authMode === 'login' ? '登录' : '注册'}</button>
      </form>
    </div>
  `;

  app.querySelectorAll('[data-mode]').forEach((button) => {
    button.addEventListener('click', () => {
      authMode = button.dataset.mode;
      renderAuth();
    });
  });

  app.querySelector('#auth-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');

    const action =
      authMode === 'login'
        ? supabase.auth.signInWithPassword({ email, password })
        : supabase.auth.signUp({
            email,
            password,
            options: {
              emailRedirectTo: authRedirectUrl(),
            },
          });

    const { error } = await action;
    if (error) {
      renderAuth(error.message);
      return;
    }
    if (authMode === 'signup') {
      renderAuth('注册成功。若开启了邮箱确认，请先前往邮箱完成验证后再登录。');
      return;
    }
    await renderApp();
  });
}

function renderHeader(email) {
  return `
    <header class="app-header">
      <div>
        <h1>HR40 运动记录</h1>
        <div class="muted">${escapeHtml(email)}</div>
      </div>
      <button id="logout-btn" class="secondary">退出</button>
    </header>
  `;
}

async function renderList() {
  const session = await getSession();
  if (!session) {
    renderAuth();
    return;
  }

  app.innerHTML = `${renderHeader(session.user.email)}<div class="muted">加载中...</div>`;

  try {
    const workouts = await fetchWorkouts();
    const listHtml = workouts.length
      ? workouts
          .map(
            (workout) => `
              <a class="workout-item" href="#/workout/${workout.id}">
                <h3>${escapeHtml(workoutTypeLabel(workout.workout_type))}</h3>
                <div class="muted">${escapeHtml(formatDateTime(workout.start_millis))}</div>
                <div>时长 ${escapeHtml(formatDuration(workout.start_millis, workout.end_millis))}</div>
              </a>
            `,
          )
          .join('')
      : '<div class="card muted">暂无训练记录。请先在 Android App 中同步数据。</div>';

    app.innerHTML = `
      ${renderHeader(session.user.email)}
      <section class="workout-list">${listHtml}</section>
    `;
    document.getElementById('logout-btn').addEventListener('click', async () => {
      await supabase.auth.signOut();
      location.hash = '';
      renderAuth();
    });
  } catch (error) {
    app.innerHTML = `
      ${renderHeader(session.user.email)}
      <div class="card error">加载失败：${escapeHtml(error.message)}</div>
    `;
  }
}

async function renderDetail(workoutId) {
  const session = await getSession();
  if (!session) {
    renderAuth();
    return;
  }

  app.innerHTML = `${renderHeader(session.user.email)}<div class="muted">加载详情中...</div>`;

  try {
    const { workout, samples, sets, profile } = await fetchWorkoutDetail(workoutId);
    const stats = calculateWorkoutStats(profile, samples, workout.workout_type);
    const maxHr = profile ? Math.max(120, 220 - profile.age) : 190;

    const zonesHtml = stats.zoneMillis
      .map(
        (millis, index) =>
          `<li>${escapeHtml(stats.zoneLabels[index])}：${escapeHtml(formatZoneDuration(millis))}</li>`,
      )
      .join('');

    const setsHtml = sets.length
      ? sets
          .map(
            (set) =>
              `<li>${escapeHtml(set.exercise_name)} - ${set.weight}${escapeHtml(set.weight_unit)} x ${set.reps}</li>`,
          )
          .join('')
      : '<li class="muted">无力量组记录</li>';

    app.innerHTML = `
      ${renderHeader(session.user.email)}
      <a class="back-link" href="#/">← 返回列表</a>
      <section class="stack">
        <div class="card stack">
          <div>
            <h2 style="margin: 0 0 8px;">${escapeHtml(workoutTypeLabel(workout.workout_type))}</h2>
            <div class="muted">${escapeHtml(formatDateTime(workout.start_millis))}</div>
          </div>
          <div class="metrics-grid">
            <div class="metric"><div class="label">时长</div><div class="value">${escapeHtml(formatDuration(workout.start_millis, workout.end_millis))}</div></div>
            <div class="metric"><div class="label">平均心率</div><div class="value">${stats.avgBpm || '-'} bpm</div></div>
            <div class="metric"><div class="label">最高/最低</div><div class="value">${stats.maxBpm || '-'} / ${stats.minBpm || '-'}</div></div>
            <div class="metric"><div class="label">估算消耗</div><div class="value">${stats.calories.toFixed(1)} kcal</div></div>
            <div class="metric"><div class="label">采样点</div><div class="value">${stats.sampleCount}</div></div>
          </div>
        </div>

        <div class="card stack">
          <h3 style="margin: 0;">心率曲线</h3>
          <div class="chart-wrap"><canvas id="hr-chart"></canvas></div>
        </div>

        <div class="card stack">
          <h3 style="margin: 0;">心率区间</h3>
          <ul class="zone-list">${zonesHtml}</ul>
        </div>

        <div class="card stack">
          <h3 style="margin: 0;">力量组</h3>
          <ul class="set-list">${setsHtml}</ul>
        </div>
      </section>
    `;

    document.getElementById('logout-btn').addEventListener('click', async () => {
      await supabase.auth.signOut();
      location.hash = '';
      renderAuth();
    });

    const canvas = document.getElementById('hr-chart');
    drawHeartRateChart(canvas, samples, maxHr);
    window.addEventListener(
      'resize',
      () => drawHeartRateChart(canvas, samples, maxHr),
      { once: true },
    );
  } catch (error) {
    app.innerHTML = `
      ${renderHeader(session.user.email)}
      <a class="back-link" href="#/">← 返回列表</a>
      <div class="card error">加载失败：${escapeHtml(error.message)}</div>
    `;
  }
}

async function renderApp() {
  const hash = location.hash || '#/';
  const detailMatch = hash.match(/^#\/workout\/(.+)$/);
  if (detailMatch) {
    await renderDetail(detailMatch[1]);
    return;
  }
  await renderList();
}

async function handleAuthCallback() {
  const params = new URLSearchParams(window.location.search);
  const code = params.get('code');
  if (code) {
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (error) {
      renderAuth(`邮箱验证失败：${error.message}`);
      return true;
    }
    window.history.replaceState({}, document.title, authRedirectUrl());
    return true;
  }
  return false;
}

supabase.auth.onAuthStateChange(() => {
  renderApp();
});

(async () => {
  await handleAuthCallback();
  await renderApp();
})();
