const tablist = document.querySelector('.showcase-tabs');
const tabs = [...tablist.querySelectorAll('[role="tab"]')];

function selectTab(selected, animate = false) {
  for (const tab of tabs) {
    const active = tab === selected;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
  }
  if (animate && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    document.getElementById(selected.getAttribute('aria-controls'))
      .animate([{ opacity: 0.4 }, { opacity: 1 }], { duration: 180, easing: 'ease-out' });
  }
}

for (const tab of tabs) {
  const panel = document.getElementById(tab.getAttribute('aria-controls'));
  panel.setAttribute('role', 'tabpanel');
  panel.setAttribute('aria-labelledby', tab.id);
  tab.addEventListener('click', () => selectTab(tab, true));
  tab.addEventListener('keydown', (event) => {
    const index = tabs.indexOf(tab);
    let next;
    if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
    if (event.key === 'ArrowLeft') next = tabs[(index + tabs.length - 1) % tabs.length];
    if (event.key === 'Home') next = tabs[0];
    if (event.key === 'End') next = tabs[tabs.length - 1];
    if (!next) return;
    event.preventDefault();
    selectTab(next, true);
    next.focus();
  });
}
selectTab(tabs.find((tab) => tab.getAttribute('aria-selected') === 'true'));
tablist.hidden = false;

const dialog = document.querySelector('.image-dialog');
const preview = dialog.querySelector('img');
const caption = dialog.querySelector('figcaption');

for (const button of document.querySelectorAll('[data-screenshot]')) {
  button.addEventListener('click', (event) => {
    if (typeof dialog.showModal !== 'function') return;
    event.preventDefault();
    const source = button.querySelector('img');
    preview.src = source.currentSrc || source.src;
    preview.alt = source.alt;
    caption.textContent = button.dataset.caption;
    dialog.showModal();
  });
}

dialog.addEventListener('click', (event) => {
  if (event.target !== dialog) return;
  const bounds = dialog.getBoundingClientRect();
  if (event.clientX < bounds.left || event.clientX > bounds.right ||
      event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
});

const languages = document.querySelector('.languages');
document.addEventListener('click', (event) => {
  if (!languages.contains(event.target)) languages.open = false;
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && languages.open) {
    languages.open = false;
    languages.querySelector('summary').focus();
  }
});
