<template>
  <!-- Mini calendar trigger -->
  <div class="mini-calendar" @click.stop="toggleExpand">
    <section class="calendar-card cards">
      <header class="calendar-header">
        <div class="calendar-label">
          日历
          <span class="mini-label-date">{{ displayYear }}年 {{ displayMonth }}月</span>
        </div>
      </header>
      <div class="weekday-row">
        <span v-for="(label, index) in weekdayLabels" :key="index">{{ label }}</span>
      </div>
      <div class="days-grid">
        <div
          v-for="day in monthDays"
          :key="day.key"
          :class="[
            'day-cell',
            {
              'is-today': day.isToday,
              'has-mini-event': (day.events || []).length,
            },
            (day.events || []).length ? `event-${getMarkColor(day.events)}` : '',
          ]"
        >
          <div class="day-number" v-if="!day.empty">{{ day.date }}</div>
          <span
            v-if="(day.events || []).length"
            class="mini-underline"
            :class="`mini-underline--${getMarkColor(day.events)}`"
          ></span>
        </div>
      </div>
    </section>
  </div>

  <!-- Expanded modal -->
  <Teleport to="body">
    <div v-if="isExpanded" :class="['expanded-calendar-container', { 'is-collapsing': isCollapsing }]">
      <div class="calendar-overlay" @click.stop="close"></div>
      <section class="calendar-card cards">
        <header class="calendar-header">
          <div>
            <div class="calendar-label">日历</div>
            <div class="calendar-sub">切换云事件或添加你的个人标注</div>
          </div>
          <div class="header-nav-mobile">
            <button class="control-btn" type="button" @click.stop="prevMonth">上一月</button>
            <button class="control-btn" type="button" @click.stop="nextMonth">下一月</button>
          </div>
          <div class="mode-switch">
            <button :class="{ active: mode === 'cloud' }" @click.stop="mode = 'cloud'">
              <CloudStorage class="mode-icon" />
              云模式
            </button>
            <button :class="{ active: mode === 'personal' }" @click.stop="mode = 'personal'">
              <Lock class="mode-icon" />
              私人模式
            </button>
          </div>
          <button class="close-btn" type="button" @click.stop="close">×</button>
        </header>

        <div class="calendar-controls">
          <button class="control-btn" @click.stop="prevMonth">上一月</button>
          <div class="calendar-title">{{ displayYear }}年 {{ displayMonth }}月</div>
          <button class="control-btn" @click.stop="nextMonth">下一月</button>
        </div>

        <div class="calendar-body">
          <div class="calendar-left">
            <div class="weekday-row">
              <span v-for="(label, index) in weekdayLabels" :key="index">{{ label }}</span>
            </div>
            <div class="days-grid">
              <div
                v-for="day in monthDays"
                :key="day.key"
                :class="[
                  'day-cell',
                  {
                    'is-today': day.isToday,
                    'has-event': (day.events || []).length,
                  },
                  (day.events || []).length ? `event-${getMarkColor(day.events)}` : '',
                ]"
              >
                <div class="day-number">{{ day.date }}</div>
                <div class="event-icons">
                  <component
                    v-for="(event, index) in (day.events || []).slice(0, 3)"
                    :key="event.date + event.title + index"
                    :is="iconMap[event.icon] || iconMap.Calendar"
                    class="event-icon"
                  />
                </div>
                <div v-if="(day.events || []).length" class="event-tooltip">
                  <div class="tooltip-title">{{ (day.events || []).length }} 个事件</div>
                  <ul>
                    <li v-for="(event, idx) in (day.events || [])" :key="event.date + event.title + idx">
                      <span class="dot" :class="{ 'dot-purple': event.markColor === 'purple', 'dot-blue': event.markColor === 'blue' }"></span>
                      <span class="text">{{ event.title }}<span v-if="event.description"> · {{ event.description }}</span></span>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </div>

          <div class="calendar-right">
                <div v-if="mode === 'cloud'" class="cloud-panel">
              <h4 class="panel-title">
                最近的特殊日
                <span class="legend-tip" tabindex="0" aria-label="颜色说明">
                  i
                  <div class="legend-tooltip">
                    <p><span class="legend-dot legend-dot--red"></span> 红色：公共节日</p>
                    <p><span class="legend-dot legend-dot--purple"></span> 紫色：站长定的特殊日期</p>
                    <p><span class="legend-dot legend-dot--blue"></span> 蓝色：私人标记</p>
                  </div>
                </span>
              </h4>
              <ul class="upcoming-list">
                    <li v-for="event in upcomingEvents" :key="event.isoNextDate + event.title" class="event-row">
                      <span class="bullet" :class="{ 'bullet-purple': event.markColor === 'purple', 'bullet-blue': event.markColor === 'blue' }"></span>
                  <div class="upcoming-meta">
                    <span class="event-title">{{ event.title }}</span>
                        <span class="event-date">{{ formatDate(event.isoNextDate || event.date) }}</span>
                        <span class="event-countdown" :class="{ 'event-countdown-purple': event.markColor === 'purple', 'event-countdown-blue': event.markColor === 'blue' }">剩余 {{ event.daysLeft }} 天</span>
                  </div>
                </li>
                <li v-if="!upcomingEvents.length" class="empty">暂无即将到来的特殊日</li>
              </ul>
            </div>

            <div v-else class="personal-panel">
              <form class="personal-form" @submit.prevent="addPersonalEvent">
                <label>
                  日期
                  <input type="date" v-model="newEvent.date" :max="maxDate" required />
                </label>
                <label>
                  标题
                  <input type="text" v-model="newEvent.title" maxlength="20" placeholder="标题" required />
                </label>
                <label>
                  图标
                  <select v-model="newEvent.icon">
                    <option v-for="(label, key) in iconOptions" :key="key" :value="key">{{ label }}</option>
                  </select>
                </label>
                <label>
                  描述
                  <input type="text" v-model="newEvent.description" maxlength="40" placeholder="描述（可选）" />
                </label>
                <button type="submit" class="add-btn">添加至本地</button>
              </form>

              <div class="personal-list">
                <article v-for="event in personalEvents" :key="event.date + event.title" class="personal-item">
                  <div class="item-meta">
                    <span class="date">{{ formatDate(event.date) }}</span>
                    <span class="title">{{ event.title }}</span>
                  </div>
                  <button class="remove-btn" @click.stop="removePersonalEvent(event)">移除</button>
                </article>
                <div v-if="!personalEvents.length" class="empty">暂无本地事件</div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import {
  Calendar,
  Flag,
  Gift,
  Heart,
  AlarmClock,
  Leaf,
  Star,
  CloseOne,
  CloudStorage,
  Lock,
} from "@icon-park/vue-next";
import cloudData from "@/assets/calendarCloudEvents.json";
import { resolveEventDateForYear, getNextOccurrenceForEvent } from "@/utils/cloudEventHelpers";

const isExpanded = ref(false);
const isCollapsing = ref(false);
const mode = ref("cloud");
const today = new Date();
const displayYear = ref(today.getFullYear());
const displayMonth = ref(today.getMonth() + 1);
const cloudEvents = ref([]);
const personalEvents = ref([]);

const cloudEventsForYear = computed(() => {
  const year = displayYear.value;
  return (cloudEvents.value || [])
    .map((event) => {
      const iso = resolveEventDateForYear(event, year);
      if (!iso) return null;
      return { ...event, date: iso };
    })
    .filter(Boolean);
});

const formatIsoDate = (dateObj) => {
  const y = dateObj.getFullYear();
  const m = String(dateObj.getMonth() + 1).padStart(2, "0");
  const d = String(dateObj.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

const todayIso = formatIsoDate(today);
const newEvent = ref({ date: todayIso, title: "", icon: "Calendar", description: "", markColor: "blue" });

const weekdayLabels = ["日", "一", "二", "三", "四", "五", "六"];

const iconMap = { Calendar, Flag, Gift, Heart, AlarmClock, Leaf, Star, CloseOne };

const iconOptions = {
  Calendar: "📅 普通",
  Flag: "🚩 纪念",
  Gift: "🎁 节日",
  Heart: "❤️ 温暖",
  AlarmClock: "⏰ 提醒",
  Leaf: "🍃 纪念",
  Star: "⭐ 特别",
};

const storageKey = "home-calendar-personal-events";
const cloudUrl = import.meta.env.VITE_CALENDAR_EVENTS_URL;

const getMarkColor = (events = []) => {
  if (!events.length) return "red";
  const colors = events.map((ev) => ev.markColor).filter(Boolean);
  if (colors.includes("purple")) return "purple";
  if (colors.includes("blue")) return "blue";
  if (colors.includes("red")) return "red";
  return "red";
};

const toggleExpand = () => {
  if (!isExpanded.value) {
    isExpanded.value = true;
    isCollapsing.value = false;
  }
};

const close = () => {
  if (!isExpanded.value) return;
  isCollapsing.value = true;
  setTimeout(() => {
    isExpanded.value = false;
    isCollapsing.value = false;
  }, 280);
};

const monthDays = computed(() => {
  const year = displayYear.value;
  const month = displayMonth.value - 1;
  const firstDay = new Date(year, month, 1);
  const startWeekday = firstDay.getDay();
  const totalDays = new Date(year, month + 1, 0).getDate();
  const cells = [];

  for (let blank = 0; blank < startWeekday; blank++) {
    cells.push({ key: `empty-${blank}`, empty: true, date: "", events: [] });
  }

  const allEvents = [...cloudEventsForYear.value, ...personalEvents.value];

  for (let day = 1; day <= totalDays; day++) {
    const isoDate = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    const events = allEvents.filter((event) => event.date === isoDate);
    cells.push({
      key: isoDate,
      date: day,
      events,
      isToday:
        year === today.getFullYear() && month === today.getMonth() && day === today.getDate(),
    });
  }

  return cells;
});

const formatDate = (value) => {
  if (!value) return "";
  const [year, month, day] = value.split("-");
  return `${Number(month)}月${Number(day)}日`;
};

const maxDate = computed(() => {
  const future = new Date(today);
  future.setFullYear(future.getFullYear() + 3);
  return formatIsoDate(future);
});

const loadPersonalEvents = () => {
  try {
    const stored = window.localStorage.getItem(storageKey);
    const parsed = stored ? JSON.parse(stored) : [];
    personalEvents.value = parsed.map((ev) => ({ ...ev, markColor: ev.markColor || "blue" }));
  } catch (error) {
    console.error("加载本地事件失败", error);
    personalEvents.value = [];
  }
};

const savePersonalEvents = () => {
  window.localStorage.setItem(storageKey, JSON.stringify(personalEvents.value));
};

const addPersonalEvent = () => {
  if (!newEvent.value.date || !newEvent.value.title.trim()) return;
  personalEvents.value.unshift({
    date: newEvent.value.date,
    title: newEvent.value.title.trim(),
    icon: newEvent.value.icon,
    description: newEvent.value.description.trim(),
    markColor: newEvent.value.markColor || "blue",
  });
  savePersonalEvents();
  ElMessage({ message: "已保存到本地事件", type: "success", duration: 1200 });
  newEvent.value = { date: todayIso, title: "", icon: "Calendar", description: "", markColor: "blue" };
};

const removePersonalEvent = (event) => {
  personalEvents.value = personalEvents.value.filter((item) => item !== event);
  savePersonalEvents();
  ElMessage({ message: "已从本地移除", type: "info", duration: 1200 });
};

const prevMonth = () => {
  if (displayMonth.value === 1) {
    displayMonth.value = 12;
    displayYear.value -= 1;
  } else {
    displayMonth.value -= 1;
  }
};

const nextMonth = () => {
  if (displayMonth.value === 12) {
    displayMonth.value = 1;
    displayYear.value += 1;
  } else {
    displayMonth.value += 1;
  }
};

const upcomingEvents = computed(() => {
  const now = new Date();

  const cloudNext = (cloudEvents.value || []).map((ev) => {
    const occurrence = getNextOccurrenceForEvent(ev, now);
    if (!occurrence) return null;
    return {
      ...ev,
      nextDate: occurrence.dateObj,
      isoNextDate: occurrence.iso,
      daysLeft: occurrence.daysLeft,
    };
  });

  const personalNext = (personalEvents.value || []).map((ev) => {
    if (!ev.date) return null;
    const dateObj = new Date(ev.date);
    if (Number.isNaN(dateObj.getTime())) return null;
    const diff = Math.ceil((dateObj - now) / (1000 * 60 * 60 * 24));
    return diff >= 0
      ? {
          ...ev,
          nextDate: dateObj,
          isoNextDate: ev.date,
          daysLeft: diff,
          markColor: ev.markColor || "blue",
        }
      : null;
  });

  return [...cloudNext, ...personalNext]
    .filter((event) => event && Number.isFinite(event.daysLeft))
    .sort((a, b) => a.daysLeft - b.daysLeft)
    .slice(0, 3);
});

const fetchCloudEvents = async () => {
  try {
    if (cloudUrl) {
      const response = await fetch(cloudUrl);
      if (response.ok) {
        const data = await response.json();
        cloudEvents.value = Array.isArray(data) ? data : cloudData;
        return;
      }
    }
    cloudEvents.value = cloudData;
  } catch (error) {
    console.warn("云端加载失败，使用本地数据", error);
    cloudEvents.value = cloudData;
  }
};

onMounted(() => {
  loadPersonalEvents();
  fetchCloudEvents();
});
</script>

<style lang="scss" scoped>
@keyframes zoom-in {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes zoom-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.9);
  }
}

.calendar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  z-index: 1;
}

.expanded-calendar-container {
  position: fixed;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.calendar-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 100%;
  border-radius: 12px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 1.25rem 1.5rem;
  overflow: hidden;
  animation: zoom-in 0.3s cubic-bezier(0.25, 1, 0.5, 1) forwards;
}

.expanded-calendar-container .calendar-card {
  width: 92vw;
  max-width: 900px;
  max-height: 620px;
  overflow: visible;
}

.expanded-calendar-container.is-collapsing .calendar-card {
  animation: zoom-out 0.28s cubic-bezier(0.5, 0, 0.75, 0) forwards;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  flex-shrink: 0;

  .calendar-label {
    font-size: 1.2rem;
    font-weight: bold;
    display: inline-flex;
    align-items: baseline;
    gap: 6px;
  }

  .mini-label-date {
    font-size: 0.75rem;
    opacity: 0.7;
  }

  .calendar-sub {
    font-size: 0.8rem;
    opacity: 0.7;
  }

  .close-btn {
    background: rgba(255, 255, 255, 0.08);
    border: none;
    color: #fff;
    width: 32px;
    height: 32px;
    border-radius: 8px;
    cursor: pointer;
    font-size: 1.4rem;
    line-height: 1;
    display: grid;
    place-items: center;
    transition: all 0.2s;
    &:hover {
      background: rgba(255, 255, 255, 0.18);
      color: #ef859d;
    }
  }
}

.mode-switch {
  display: flex;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.08);
  padding: 4px;
  border-radius: 10px;

  button {
    background: transparent;
    border: none;
    color: #fff;
    padding: 4px 12px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 0.8rem;
    transition: all 0.2s;
    display: inline-flex;
    align-items: center;
    gap: 6px;

    &.active {
      background: #fff;
      color: #000;
    }
  }

  .mode-icon {
    font-size: 16px;
  }
}

.calendar-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
  padding: 0 0.5rem;
  flex-shrink: 0;

  .calendar-title {
    font-weight: bold;
    text-align: center;
    flex: 1;
  }

  .control-btn {
    background: rgba(255, 255, 255, 0.1);
    border: none;
    color: #fff;
    padding: 6px 12px;
    border-radius: 4px;
    cursor: pointer;
    &:hover {
      background: rgba(255, 255, 255, 0.2);
    }
  }
}

.header-nav-mobile {
  display: none;
  gap: 0.35rem;
  align-items: center;
  margin-left: auto;

  .control-btn {
    padding: 6px 11px;
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.22), rgba(255, 255, 255, 0.08));
    border: 1px solid rgba(255, 255, 255, 0.25);
    border-radius: 10px;
    color: #fff;
    font-weight: 600;
    box-shadow: 0 4px 10px rgba(0, 0, 0, 0.25);
    transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.2s ease;

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 14px rgba(0, 0, 0, 0.3);
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.12));
    }

    &:active {
      transform: translateY(0);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
    }
  }
}

.calendar-body {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 1.2rem;
  flex: 1;
  min-height: 0;
}

.calendar-left {
  flex: 1.05;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.calendar-right {
  flex: 0.95;
  min-width: 300px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 0.9rem;
  max-height: 100%;
  overflow-y: auto;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
}

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-weight: bold;
  opacity: 0.6;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  position: relative;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-2px) scale(1.03);
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.25);
    z-index: 1;
  }

  .event-tooltip {
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translate(-50%, 10px);
    min-width: 170px;
    max-width: 220px;
    padding: 10px;
    border-radius: 8px;
    background: rgba(0, 0, 0, 0.75);
    border: 1px solid rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(8px);
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.35);
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transition: opacity 0.16s ease, transform 0.16s ease;
    z-index: 10;

    .tooltip-title {
      font-weight: 700;
      margin-bottom: 6px;
      color: #fff;
      font-size: 0.9rem;
    }

    ul {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        align-items: center;
        gap: 6px;
        color: #f7f7f7;
        font-size: 0.82rem;
        line-height: 1.2;

        .dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: #ef3340;
          box-shadow: 0 0 6px rgba(239, 51, 64, 0.45);
          flex-shrink: 0;

          &.dot-purple {
            background: #c17eff;
            box-shadow: 0 0 6px rgba(193, 126, 255, 0.45);
          }

          &.dot-blue {
            background: #4da3ff;
            box-shadow: 0 0 6px rgba(77, 163, 255, 0.45);
          }
        }

        .text {
          opacity: 0.95;
        }
      }
    }
  }

  &.is-today {
    border: 2px solid #ef859d;
  }

  &.has-event::before {
    content: "";
    position: absolute;
    top: 12%;
    left: 12%;
    width: 76%;
    height: 76%;
    border: 1.5px solid rgba(239, 51, 64, 0.55);
    border-radius: 50%;
    transform: rotate(4deg) scaleX(0.99);
    opacity: 0.7;
    filter: blur(0.55px);
    pointer-events: none;
  }

  &.has-event::after {
    content: "";
    position: absolute;
    top: 7%;
    left: 7%;
    width: 86%;
    height: 86%;
    border: 2px solid #ef3340;
    border-radius: 50%;
    box-shadow: 0 0 10px rgba(239, 51, 64, 0.32), 0 0 0 1px rgba(239, 51, 64, 0.22),
      0 0 0 7px rgba(239, 51, 64, 0.08);
    background:
      radial-gradient(120% 95% at 18% 32%, rgba(239, 51, 64, 0.3) 0%, transparent 55%),
      radial-gradient(95% 115% at 74% 58%, rgba(239, 51, 64, 0.26) 0%, transparent 60%),
      radial-gradient(90% 90% at 40% 78%, rgba(239, 51, 64, 0.2) 0%, transparent 62%),
      radial-gradient(75% 85% at 62% 22%, rgba(239, 51, 64, 0.18) 0%, transparent 58%),
      radial-gradient(18% 22% at 30% 15%, rgba(239, 51, 64, 0.35) 0%, transparent 75%),
      radial-gradient(12% 18% at 78% 72%, rgba(239, 51, 64, 0.28) 0%, transparent 78%),
      radial-gradient(10% 14% at 52% 48%, rgba(239, 51, 64, 0.22) 0%, transparent 80%);
    opacity: 0.95;
    transform: rotate(-7deg) scaleX(0.985);
    filter: blur(0.45px) drop-shadow(-1px 1px 1px rgba(0, 0, 0, 0.18));
    pointer-events: none;
  }

  &.event-purple.has-event::before {
    border: 1.5px solid rgba(193, 126, 255, 0.55);
    filter: blur(0.55px);
  }

  &.event-purple.has-event::after {
    border: 2px solid #c17eff;
    box-shadow: 0 0 10px rgba(193, 126, 255, 0.32), 0 0 0 1px rgba(193, 126, 255, 0.22),
      0 0 0 7px rgba(193, 126, 255, 0.08);
    background:
      radial-gradient(120% 95% at 18% 32%, rgba(193, 126, 255, 0.3) 0%, transparent 55%),
      radial-gradient(95% 115% at 74% 58%, rgba(193, 126, 255, 0.26) 0%, transparent 60%),
      radial-gradient(90% 90% at 40% 78%, rgba(193, 126, 255, 0.2) 0%, transparent 62%),
      radial-gradient(75% 85% at 62% 22%, rgba(193, 126, 255, 0.18) 0%, transparent 58%),
      radial-gradient(18% 22% at 30% 15%, rgba(193, 126, 255, 0.35) 0%, transparent 75%),
      radial-gradient(12% 18% at 78% 72%, rgba(193, 126, 255, 0.28) 0%, transparent 78%),
      radial-gradient(10% 14% at 52% 48%, rgba(193, 126, 255, 0.22) 0%, transparent 80%);
    filter: blur(0.45px) drop-shadow(-1px 1px 1px rgba(0, 0, 0, 0.18));
  }

  &.event-blue.has-event::before {
    border: 1.5px solid rgba(77, 163, 255, 0.55);
    filter: blur(0.55px);
  }

  &.event-blue.has-event::after {
    border: 2px solid #4da3ff;
    box-shadow: 0 0 10px rgba(77, 163, 255, 0.32), 0 0 0 1px rgba(77, 163, 255, 0.22),
      0 0 0 7px rgba(77, 163, 255, 0.08);
    background:
      radial-gradient(120% 95% at 18% 32%, rgba(77, 163, 255, 0.3) 0%, transparent 55%),
      radial-gradient(95% 115% at 74% 58%, rgba(77, 163, 255, 0.26) 0%, transparent 60%),
      radial-gradient(90% 90% at 40% 78%, rgba(77, 163, 255, 0.2) 0%, transparent 62%),
      radial-gradient(75% 85% at 62% 22%, rgba(77, 163, 255, 0.18) 0%, transparent 58%),
      radial-gradient(18% 22% at 30% 15%, rgba(77, 163, 255, 0.35) 0%, transparent 75%),
      radial-gradient(12% 18% at 78% 72%, rgba(77, 163, 255, 0.28) 0%, transparent 78%),
      radial-gradient(10% 14% at 52% 48%, rgba(77, 163, 255, 0.22) 0%, transparent 80%);
    filter: blur(0.45px) drop-shadow(-1px 1px 1px rgba(0, 0, 0, 0.18));
  }

  &.event-purple {
    .event-icon {
      color: #c17eff;
    }

    .event-tooltip .dot {
      background: #c17eff;
      box-shadow: 0 0 6px rgba(193, 126, 255, 0.45);
    }
  }

  &.event-blue {
    .event-icon {
      color: #4da3ff;
    }

    .event-tooltip .dot {
      background: #4da3ff;
      box-shadow: 0 0 6px rgba(77, 163, 255, 0.45);
    }
  }

  &.has-event:hover {
    transform: translateY(-3px) scale(1.08);
    z-index: 2;

    .event-tooltip {
      opacity: 1;
      visibility: visible;
      transform: translate(-50%, 4px);
    }
  }

  .day-number {
    font-weight: 500;
  }
}

.event-icons {
  display: flex;
  gap: 2px;
  margin-top: 2px;

  .event-icon {
    font-size: 12px;
    color: #ef859d;
  }
}

.cloud-panel .panel-title {
  margin-bottom: 0.5rem;
  font-size: 1rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-tip {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2.5px solid rgba(255, 255, 255, 0.6);
  font-size: 12px;
  line-height: 1;
  font-weight: 700;
  padding-left: 0px;
  cursor: default;
  color: #fff;
  opacity: 0.8;

  &:hover,
  &:focus-visible {
    opacity: 1;
  }

  .legend-tooltip {
    position: absolute;
    top: 120%;
    left: 50%;
    transform: translateX(-50%);
    background: rgba(0, 0, 0, 0.9);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 8px;
    padding: 8px 10px;
    white-space: nowrap;
    box-shadow: 0 8px 18px rgba(0, 0, 0, 0.25);
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.16s ease, transform 0.16s ease;
    transform: translate(-50%, -2px);
    z-index: 20;

    p {
      margin: 4px 0;
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #f7f7f7;
    }

    .legend-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;

      &.legend-dot--red {
        background: #ef3340;
        box-shadow: 0 0 6px rgba(239, 51, 64, 0.35);
      }

      &.legend-dot--purple {
        background: #c17eff;
        box-shadow: 0 0 6px rgba(193, 126, 255, 0.35);
      }

      &.legend-dot--blue {
        background: #4da3ff;
        box-shadow: 0 0 6px rgba(77, 163, 255, 0.35);
      }
    }
  }

  &:hover .legend-tooltip,
  &:focus-visible .legend-tooltip {
    opacity: 1;
    visibility: visible;
    transform: translate(-50%, 2px);
  }
}

.upcoming-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.event-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);

  .bullet {
    width: 6px;
    height: 6px;
    background: #ef859d;
    border-radius: 50%;

    &.bullet-purple {
      background: #c17eff;
      box-shadow: 0 0 6px rgba(193, 126, 255, 0.35);
    }

    &.bullet-blue {
      background: #4da3ff;
      box-shadow: 0 0 6px rgba(77, 163, 255, 0.35);
    }
  }

  .upcoming-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .event-title {
    font-weight: 600;
  }

  .event-date {
    font-size: 0.9rem;
    opacity: 0.75;
  }

  .event-countdown {
    font-size: 0.9rem;
    color: #ef859d;
    font-weight: 600;

    &.event-countdown-purple {
      color: #c17eff;
    }

    &.event-countdown-blue {
      color: #4da3ff;
    }
  }
}

.cloud-panel .empty {
  text-align: center;
  opacity: 0.6;
  padding: 1rem 0;
}

.personal-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
  background: rgba(255, 255, 255, 0.05);
  padding: 1rem;
  border-radius: 8px;

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 0.8rem;
    opacity: 0.8;
  }

  input,
  select {
    background: rgba(0, 0, 0, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.2);
    color: #fff;
    padding: 6px;
    border-radius: 4px;
    outline: none;
    &:focus {
      border-color: #ef859d;
    }
  }

  .add-btn {
    grid-column: 1 / -1;
    background: #ef859d;
    color: #fff;
    border: none;
    padding: 10px;
    border-radius: 6px;
    cursor: pointer;
    font-weight: bold;
    &:hover {
      opacity: 0.9;
    }
  }
}

.personal-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  .personal-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: rgba(255, 255, 255, 0.05);
    padding: 8px 12px;
    border-radius: 6px;

    .item-meta {
      display: flex;
      gap: 1rem;
      .date {
        opacity: 0.6;
        font-size: 0.9rem;
      }
    }

    .remove-btn {
      background: rgba(255, 0, 0, 0.2);
      border: none;
      color: #ff4d4f;
      padding: 2px 8px;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.8rem;
      &:hover {
        background: rgba(255, 0, 0, 0.4);
      }
    }
  }

  .empty {
    text-align: center;
    opacity: 0.5;
    padding: 1.5rem;
  }
}

/* Mini calendar styles */
.mini-calendar {
  width: 81%;
  max-width: 760px;
  margin-top: 1rem;
  cursor: pointer;

  .calendar-card {
    padding: 12px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    transition: all 0.3s;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.3);
    }
  }

  .weekday-row {
    font-size: 10px;
    margin-bottom: 4px;
  }

  .day-cell {
    height: 24px;
    font-size: 12px;

    &.has-mini-event {
      position: relative;
    }

    &.has-event::before,
    &.has-event::after {
      display: none;
    }

    &:hover {
      transform: none;
      box-shadow: none;
    }

    &.is-today {
      background: var(--main-color, #ef859d);
      color: #fff;
    }
  }

  .mini-underline {
    position: absolute;
    bottom: 4px;
    left: 50%;
    transform: translateX(-50%);
    width: 70%;
    height: 2px;
    border-radius: 999px;
    background: #ef3340;
    box-shadow: 0 0 6px rgba(239, 51, 64, 0.25);

    &.mini-underline--purple {
      background: #c17eff;
      box-shadow: 0 0 6px rgba(193, 126, 255, 0.25);
    }

    &.mini-underline--blue {
      background: #4da3ff;
      box-shadow: 0 0 6px rgba(77, 163, 255, 0.25);
    }
  }
}

@media (max-width: 768px) {
  .expanded-calendar-container .calendar-card {
    width: 95vw;
    max-width: 95vw;
    max-height: 86vh; /* 略微加高以利用剩余空间（仅移动端生效） */
    padding: 1rem;
  }

  .calendar-header {
    align-items: flex-start;
  }

  .mode-switch {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .calendar-controls {
    flex-direction: column;
    align-items: stretch;
    padding: 0;

    .control-btn {
      width: 100%;
      justify-content: center;
    }

    .calendar-title {
      width: 100%;
      text-align: center;
    }
  }

  .calendar-body {
    flex-direction: column;
    gap: 0.8rem;
  }

  .calendar-left,
  .calendar-right {
    width: 100%;
    min-width: 0;
  }

  .calendar-right {
    padding: 0.8rem;
    margin-top: 0.6rem; /* 再上移，为下方留更多空间 */
  }

  .days-grid {
    gap: 2px;
  }

  .calendar-body {
    gap: 0.5rem;
  }

  .calendar-left {
    gap: 0.35rem;
  }

  .calendar-body .day-cell {
    aspect-ratio: auto; /* 变为长方形 */
    height: 34px;
    min-height: 0;
    padding: 4px 0;
  }

  /* 关闭按钮右置，便于移动端操作 */
  .calendar-header {
    align-items: center;

    .close-btn {
      margin-left: auto;
    }

    .header-nav-mobile {
      display: inline-flex;
    }
  }

  .calendar-controls {
    display: none;
  }

  /* 移动端提升“最近的特殊日”可读性 */
  .upcoming-list {
    gap: 0.65rem;
  }

  .event-row {
    align-items: flex-start;
    padding: 12px;

    .bullet {
      width: 7px;
      height: 7px;
    }

    .event-title {
      font-size: 1rem;
    }

    .event-date,
    .event-countdown {
      font-size: 0.95rem;
    }
  }

  .mini-calendar {
    width: 92%;
    max-width: 380px;
    margin: 0.4rem auto 0; /* 略微上移迷你日历 */

    .calendar-card {
      padding: 8px;
    }

    .day-cell {
      height: 30px;
      font-size: 13px;
      min-height: 0;
      aspect-ratio: auto;
      line-height: 1;
      padding: 0;
    }
  }
}

/* Scrollbar */
.calendar-card::-webkit-scrollbar {
  width: 6px;
}
.calendar-card::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}
</style>
