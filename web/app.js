const state = {
  data: null,
  selectedWasteId: "0",
  selectedPlace: "",
  hideYard: true,
  currentYear: new Date().getFullYear(),
  currentMonth: new Date().getMonth() + 1,
  selectedDate: new Date().toISOString().slice(0, 10),
};

const monthNames = [
  "", "Leden", "Únor", "Březen", "Duben", "Květen", "Červen",
  "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec"
];
const dayNames = ["Neděle", "Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota"];
const monthGenitive = [
  "", "ledna", "února", "března", "dubna", "května", "června",
  "července", "srpna", "září", "října", "listopadu", "prosince"
];

function formatCzechDate(isoDate) {
  const [y, m, d] = isoDate.split("-").map(Number);
  const dt = new Date(y, m - 1, d);
  return `${dayNames[dt.getDay()]} ${d}. ${monthGenitive[m]} ${y}`;
}

function relativeLabel(isoDate) {
  const todayIso = new Date().toISOString().slice(0, 10);
  const t1 = new Date(todayIso + "T00:00:00");
  const t2 = new Date(isoDate + "T00:00:00");
  const diff = Math.round((t2 - t1) / 86400000);
  if (diff === 0) return "DNES";
  if (diff === 1) return "ZÍTRA";
  if (diff === 2) return "POZÍTŘÍ";
  if (diff >= 3 && diff <= 4) return `Za ${diff} dny`;
  if (diff > 4) return `Za ${diff} dní`;
  return isoDate;
}

function getFilteredEvents() {
  if (!state.data) return [];
  return state.data.events.filter((ev) => {
    const matchWaste = state.selectedWasteId === "0" || ev.wasteTypeId === state.selectedWasteId;
    const matchPlace = !state.selectedPlace || ev.places.includes(state.selectedPlace);
    const matchYard = state.hideYard && state.selectedWasteId === "0" ? ev.isCollection : true;
    return matchWaste && matchPlace && matchYard;
  });
}

function renderFilters() {
  const wasteSelect = document.getElementById("wasteSelect");
  const placeSelect = document.getElementById("placeSelect");
  const chipsRow = document.getElementById("quickChips");

  wasteSelect.innerHTML = `<option value="0">Všechny typy událostí</option>`;
  state.data.wasteTypes.forEach((wt) => {
    const opt = document.createElement("option");
    opt.value = wt.id;
    opt.textContent = wt.name;
    if (wt.id === state.selectedWasteId) opt.selected = true;
    wasteSelect.appendChild(opt);
  });

  placeSelect.innerHTML = `<option value="">Všechna místa (celé město)</option>`;
  state.data.places.forEach((p) => {
    const opt = document.createElement("option");
    opt.value = p.name;
    opt.textContent = p.name;
    if (p.name === state.selectedPlace) opt.selected = true;
    placeSelect.appendChild(opt);
  });

  chipsRow.innerHTML = "";
  const allChip = document.createElement("button");
  allChip.className = `chip ${state.selectedWasteId === "0" ? "active" : ""}`;
  allChip.textContent = "Všechny svozy";
  allChip.onclick = () => {
    state.selectedWasteId = "0";
    wasteSelect.value = "0";
    renderAll();
  };
  chipsRow.appendChild(allChip);

  state.data.wasteTypes.filter((w) => w.isCollection).forEach((wt) => {
    const chip = document.createElement("button");
    chip.className = `chip ${state.selectedWasteId === wt.id ? "active" : ""}`;
    chip.innerHTML = `<span class="dot" style="background:${wt.color}"></span> ${wt.shortName}`;
    chip.onclick = () => {
      state.selectedWasteId = state.selectedWasteId === wt.id ? "0" : wt.id;
      wasteSelect.value = state.selectedWasteId;
      renderAll();
    };
    chipsRow.appendChild(chip);
  });
}

function renderUpcoming() {
  const events = getFilteredEvents();
  const todayIso = new Date().toISOString().slice(0, 10);
  const upcoming = events.filter((e) => e.date >= todayIso);

  const grouped = {};
  upcoming.forEach((ev) => {
    if (!grouped[ev.date]) grouped[ev.date] = [];
    grouped[ev.date].push(ev);
  });

  const dates = Object.keys(grouped).sort();
  const nextBox = document.getElementById("nextCollectionBox");

  if (dates.length > 0) {
    const nextDate = dates[0];
    const items = grouped[nextDate];
    nextBox.innerHTML = `
      <div class="next-header">
        <span style="font-size:0.8rem;font-weight:700;opacity:0.85">NEJBLIŽŠÍ SVOZ ODPADU</span>
        <span class="badge">${relativeLabel(nextDate)}</span>
      </div>
      <div style="font-size:1.15rem;font-weight:700;margin-bottom:8px">${formatCzechDate(nextDate)}</div>
      ${items.map((ev) => `
        <div class="event-row">
          <span class="event-color-box" style="background:${ev.color}"></span>
          <div>
            <strong>${ev.shortName}</strong>
            <div class="event-place">📍 ${ev.places.join(", ")}</div>
          </div>
        </div>
      `).join("")}
    `;
  } else {
    nextBox.innerHTML = `<div>Žádné nadcházející svozy pro vybraný filtr.</div>`;
  }

  const listEl = document.getElementById("upcomingList");
  listEl.innerHTML = dates.map((d) => `
    <div class="day-card">
      <div class="day-card-header">
        <span>${formatCzechDate(d)}</span>
        <span class="badge">${relativeLabel(d)}</span>
      </div>
      ${grouped[d].map((ev) => `
        <div class="event-row">
          <span class="event-color-box" style="background:${ev.color}"></span>
          <div>
            <div><strong>${ev.wasteTypeName}</strong></div>
            <div class="event-meta">${ev.description}</div>
            <div class="event-place">📍 ${ev.places.join(", ")}</div>
          </div>
        </div>
      `).join("")}
    </div>
  `).join("");
}

function renderCalendar() {
  const events = getFilteredEvents();
  const byDate = {};
  events.forEach((ev) => {
    if (!byDate[ev.date]) byDate[ev.date] = [];
    byDate[ev.date].push(ev);
  });

  document.getElementById("calMonthTitle").textContent = `${monthNames[state.currentMonth]} ${state.currentYear}`;
  const grid = document.getElementById("calGrid");
  grid.innerHTML = "";

  const firstDay = new Date(state.currentYear, state.currentMonth - 1, 1);
  const startOffset = (firstDay.getDay() + 6) % 7;
  const daysInMonth = new Date(state.currentYear, state.currentMonth, 0).getDate();
  const todayIso = new Date().toISOString().slice(0, 10);

  for (let i = 0; i < startOffset; i++) {
    grid.appendChild(document.createElement("div"));
  }

  for (let day = 1; day <= daysInMonth; day++) {
    const iso = `${state.currentYear}-${String(state.currentMonth).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    const cell = document.createElement("div");
    cell.className = `cal-cell ${iso === todayIso ? "today" : ""} ${iso === state.selectedDate ? "selected" : ""}`;
    const dayEvs = byDate[iso] || [];
    cell.innerHTML = `
      <strong>${day}</strong>
      <div class="cal-dots">
        ${dayEvs.slice(0, 5).map((e) => `<span class="dot" style="background:${e.color}"></span>`).join("")}
      </div>
    `;
    cell.onclick = () => {
      state.selectedDate = iso;
      renderCalendar();
    };
    grid.appendChild(cell);
  }

  const selBox = document.getElementById("selectedDayBox");
  const selEvs = byDate[state.selectedDate] || [];
  selBox.innerHTML = `
    <div class="day-card">
      <div class="day-card-header">
        <span>Vybraný den: ${formatCzechDate(state.selectedDate)}</span>
      </div>
      ${selEvs.length === 0 ? `<div class="event-meta">Žádný svoz odpadu pro vybraný filtr.</div>` : selEvs.map((ev) => `
        <div class="event-row">
          <span class="event-color-box" style="background:${ev.color}"></span>
          <div>
            <div><strong>${ev.wasteTypeName}</strong></div>
            <div class="event-meta">${ev.description}</div>
            <div class="event-place">📍 ${ev.places.join(", ")}</div>
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderAll() {
  renderFilters();
  renderUpcoming();
  renderCalendar();
}

async function init() {
  const res = await fetch("data/calendar.json");
  state.data = await res.json();
  renderAll();

  document.getElementById("toggleFilterBtn").onclick = () => {
    document.getElementById("filterPanel").classList.toggle("hidden");
  };
  document.getElementById("wasteSelect").onchange = (e) => {
    state.selectedWasteId = e.target.value;
    renderAll();
  };
  document.getElementById("placeSelect").onchange = (e) => {
    state.selectedPlace = e.target.value;
    renderAll();
  };
  document.getElementById("hideYardCheck").onchange = (e) => {
    state.hideYard = e.target.checked;
    renderAll();
  };
  document.getElementById("resetFilterBtn").onclick = () => {
    state.selectedWasteId = "0";
    state.selectedPlace = "";
    renderAll();
  };

  document.querySelectorAll(".tab-btn").forEach((btn) => {
    btn.onclick = () => {
      document.querySelectorAll(".tab-btn").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".tab-content").forEach((c) => c.classList.remove("active"));
      btn.classList.add("active");
      document.getElementById("tab-" + btn.dataset.tab).classList.add("active");
    };
  });

  document.getElementById("prevMonthBtn").onclick = () => {
    state.currentMonth--;
    if (state.currentMonth < 1) {
      state.currentMonth = 12;
      state.currentYear--;
    }
    renderCalendar();
  };
  document.getElementById("nextMonthBtn").onclick = () => {
    state.currentMonth++;
    if (state.currentMonth > 12) {
      state.currentMonth = 1;
      state.currentYear++;
    }
    renderCalendar();
  };
}

init();
