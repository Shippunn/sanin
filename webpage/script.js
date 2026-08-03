/* Sanin landing page interactions */

/* Lazy-load hero gif on window load */
window.addEventListener('load', function () {
  var img = document.querySelector('[data-src]');
  if (img) img.src = img.dataset.src;
});

/* Copy-to-clipboard for repo boxes */
document.querySelectorAll('[data-copy]').forEach(function (b) {
  b.addEventListener('click', function () {
    var doCopy = function () {
      return navigator.clipboard.writeText(b.dataset.copy).then(function () {
        b.textContent = 'Copied';
        b.classList.add('done');
        setTimeout(function () {
          b.textContent = 'Copy';
          b.classList.remove('done');
        }, 1400);
      });
    };
    if (navigator.clipboard) {
      doCopy().catch(function () { fallbackCopy(b); });
    } else {
      fallbackCopy(b);
    }
  });
});

function fallbackCopy(b) {
  var ta = document.createElement('textarea');
  ta.value = b.dataset.copy;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  try {
    document.execCommand('copy');
    b.textContent = 'Copied';
    b.classList.add('done');
    setTimeout(function () {
      b.textContent = 'Copy';
      b.classList.remove('done');
    }, 1400);
  } catch (e) { /* ignore */ }
  document.body.removeChild(ta);
}

/* Features grid (data-driven) */
var featuresData = JSON.parse(document.getElementById('features-data').textContent);

var icons = {
  remote: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="6" y="2" width="12" height="20" rx="3"/><circle cx="12" cy="16" r="1.6" fill="currentColor" stroke="none"/><line x1="9" y1="6" x2="15" y2="6"/><line x1="9" y1="10" x2="9" y2="10.01"/><line x1="15" y1="10" x2="15" y2="10.01"/></svg>',
  play: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><polygon points="6 3 20 12 6 21 6 3" fill="currentColor" stroke="none"/><path d="M4 4v16M2 6v12"/></svg>',
  sync: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v3.5h-3.5"/><path d="M4 12a8 8 0 0 1 14.34-5.66"/></svg>',
  skip: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 4v16l11-8z" fill="currentColor" stroke="none"/><path d="M18 4v16"/></svg>',
  plugin: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 22v-2M12 4V2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/><circle cx="12" cy="12" r="4"/></svg>',
  palette: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3a9 9 0 0 0 0 18c1.5 0 2-1 1.5-2S13 17 13.5 16.5 15 17 16.5 17a4 4 0 0 0 4-4c0-5.5-4.5-8-8.5-8z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10" cy="7.5" r="1"/><circle cx="15" cy="7.5" r="1"/><circle cx="17.5" cy="11" r="1"/></svg>'
};

(function renderFeatures() {
  var grid = document.getElementById('features-grid');
  featuresData.forEach(function (f) {
    var card = document.createElement('div');
    card.className = 'feature-card';
    card.innerHTML =
      '<div class="feature-icon">' + (icons[f.icon] || icons.remote) + '</div>' +
      '<span class="feature-tag">' + f.tag + '</span>' +
      '<div class="feature-title">' + f.title + '</div>' +
      '<div class="feature-desc">' + f.description + '</div>';
    grid.appendChild(card);
  });
})();

/* Scroll reveal */
var sections = document.querySelectorAll('.section');
var io = new IntersectionObserver(function (entries) {
  entries.forEach(function (e) {
    if (e.isIntersecting) {
      e.target.classList.add('visible');
      io.unobserve(e.target);
    }
  });
}, { threshold: 0.12 });

sections.forEach(function (s) { io.observe(s); });

/* Active nav link on scroll */
var navLinks = document.querySelectorAll('.nav-links a[data-section]');
var sectionIds = Array.prototype.map.call(document.querySelectorAll('section[id]'), function (s) { return s.id; });

function onScroll() {
  var pos = window.scrollY + 120;
  var current = 'hero';
  sectionIds.forEach(function (id) {
    var el = document.getElementById(id);
    if (el && el.offsetTop <= pos) current = id;
  });
  navLinks.forEach(function (a) {
    a.classList.toggle('active', a.dataset.section === current);
  });
}

window.addEventListener('scroll', onScroll, { passive: true });

/* Smooth scroll for nav links */
document.querySelectorAll('[data-section]').forEach(function (el) {
  el.addEventListener('click', function (e) {
    var target = document.getElementById(el.dataset.section);
    if (!target) return;
    e.preventDefault();
    target.scrollIntoView({ behavior: 'smooth' });
    closeMobileMenu();
  });
});

/* Mobile menu */
var menuToggle = document.getElementById('menuToggle');
var menuClose = document.getElementById('menuClose');
var menuOverlay = document.getElementById('menuOverlay');
var mobileMenu = document.getElementById('mobileMenu');

function openMobileMenu() {
  mobileMenu.classList.add('open');
  menuOverlay.classList.add('open');
}

function closeMobileMenu() {
  mobileMenu.classList.remove('open');
  menuOverlay.classList.remove('open');
}

menuToggle.addEventListener('click', openMobileMenu);
menuClose.addEventListener('click', closeMobileMenu);
menuOverlay.addEventListener('click', closeMobileMenu);

/* Version pill — fetch latest release tag from GitHub */
var pill = document.getElementById('version-pill');
if (pill) {
  fetch('https://api.github.com/repos/Shippun/sanin/releases/latest')
    .then(function (r) { return r.json(); })
    .then(function (data) {
      pill.textContent = data.tag_name || 'v1.0.0';
    })
    .catch(function () { pill.textContent = 'open source'; });
}
