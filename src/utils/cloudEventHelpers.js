const dateCache = new WeakMap();

const pad = (num) => String(num).padStart(2, "0");

const parseSingleDate = (input) => {
  if (!input) return null;
  const [year, month, day] = input.split("-").map((value) => Number(value));
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) return null;
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  return { year, month, day, iso: `${year}-${pad(month)}-${pad(day)}` };
};

const getEventDateList = (event) => {
  if (!event) return [];
  if (dateCache.has(event)) return dateCache.get(event);
  const list = String(event.date || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
    .map((item) => parseSingleDate(item))
    .filter(Boolean)
    .sort((a, b) => a.year - b.year || a.month - b.month || a.day - b.day);
  dateCache.set(event, list);
  return list;
};

const formatIso = (year, month, day) => `${year}-${pad(month)}-${pad(day)}`;

const hasExplicitDateForYear = (event, year) => getEventDateList(event).some((entry) => entry.year === year);

const getBaseTemplate = (event) => {
  const list = getEventDateList(event);
  return list[0] || null;
};

const resolveEventDateForYear = (event, year) => {
  if (!event || typeof year !== "number") return null;
  const entries = getEventDateList(event);
  if (!entries.length) return null;
  const match = entries.find((entry) => entry.year === year);
  if (match) return match.iso;
  const template = getBaseTemplate(event);
  if (!template) return null;
  return formatIso(year, template.month, template.day);
};

const getNextOccurrenceForEvent = (event, referenceDate = new Date()) => {
  if (!event) return null;
  const entries = getEventDateList(event);
  if (!entries.length) return null;

  const now = referenceDate;
  const msPerDay = 1000 * 60 * 60 * 24;

  const candidates = entries.map((entry) => {
    const dateObj = new Date(entry.year, entry.month - 1, entry.day);
    return { dateObj, entry, explicit: true };
  });

  const template = getBaseTemplate(event);
  if (template) {
    const currentYear = now.getFullYear();
    const lookAheadYears = 3;
    for (let offset = 0; offset <= lookAheadYears; offset += 1) {
      const year = currentYear + offset;
      if (hasExplicitDateForYear(event, year)) continue;
      const dateObj = new Date(year, template.month - 1, template.day);
      candidates.push({
        dateObj,
        entry: { year, month: template.month, day: template.day },
        explicit: false,
      });
    }
  }

  const next = candidates
    .filter(({ dateObj }) => dateObj >= now)
    .sort((a, b) => a.dateObj - b.dateObj)[0];

  if (!next) return null;

  const daysLeft = Math.ceil((next.dateObj - now) / msPerDay);
  return {
    iso: formatIso(next.entry.year, next.entry.month, next.entry.day),
    dateObj: next.dateObj,
    daysLeft,
    explicit: next.explicit,
  };
};

export { getEventDateList, resolveEventDateForYear, getNextOccurrenceForEvent };
