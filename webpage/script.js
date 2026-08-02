const DOWNLOAD_URL = "https://github.com/Shippun/sanin/releases/latest";

async function loadRelease() {
  try {
    const res = await fetch("https://api.github.com/repos/Shippun/sanin/releases/latest");
    if (!res.ok) throw new Error(String(res.status));
    const data = await res.json();
    document.getElementById("release-tag").textContent = data.tag_name;
  } catch {
    document.getElementById("release-tag").textContent = "0.2.15";
  }
}

function openModal() {
  const modal = document.getElementById("download-modal");
  modal.hidden = false;
  const link = document.getElementById("modal-reopen");
  link.href = DOWNLOAD_URL;
  link.click();
}

function closeModal() {
  document.getElementById("download-modal").hidden = true;
}

document.querySelectorAll("[data-download]").forEach((btn) => {
  btn.addEventListener("click", openModal);
});

document.querySelectorAll("[data-close]").forEach((el) => {
  el.addEventListener("click", closeModal);
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeModal();
});

const toggle = document.querySelector(".nav-toggle");
const menu = document.querySelector(".mobile-menu");
toggle.addEventListener("click", () => {
  const open = menu.classList.toggle("open");
  toggle.setAttribute("aria-expanded", String(open));
});

document.querySelectorAll(".copy-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const el = document.getElementById(btn.dataset.copy);
    try {
      await navigator.clipboard.writeText(el.textContent.trim());
    } catch {
      const range = document.createRange();
      range.selectNodeContents(el);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      document.execCommand("copy");
      sel.removeAllRanges();
    }
    const original = btn.textContent;
    btn.textContent = "Copied ✓";
    btn.classList.add("copied");
    setTimeout(() => {
      btn.textContent = original;
      btn.classList.remove("copied");
    }, 1800);
  });
});

const observer = new IntersectionObserver(
  (entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        entry.target.classList.add("visible");
        observer.unobserve(entry.target);
      }
    }
  },
  { threshold: 0.12 }
);

document.querySelectorAll(".reveal").forEach((el) => observer.observe(el));

loadRelease();
