/**
 * AI Expense Tracker - Frontend Application Controller
 * Handles Theme, Chart.js Visualizations, Toasts, Modals, Keyboard Shortcuts & Floating Chat
 */

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initDropdowns();
  initMobileNav();
  initModals();
  initFloatingChat();
  initKeyboardShortcuts();
});

/* ==========================================================================
   1. Theme Management (Dark / Light Mode)
   ========================================================================== */
function initTheme() {
  const savedTheme = localStorage.getItem('theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);
  updateThemeIcon(savedTheme);

  const themeToggleBtn = document.getElementById('themeToggleBtn');
  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
      const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('theme', newTheme);
      updateThemeIcon(newTheme);

      // Re-render Chart.js charts if they exist to match theme palette
      if (window.renderDashboardCharts) {
        window.renderDashboardCharts();
      }
    });
  }
}

function updateThemeIcon(theme) {
  const themeToggleBtn = document.getElementById('themeToggleBtn');
  if (themeToggleBtn) {
    themeToggleBtn.innerHTML = theme === 'dark' ? '☀️' : '🌙';
    themeToggleBtn.setAttribute('title', `Switch to ${theme === 'dark' ? 'Light' : 'Dark'} Mode`);
  }
}

/* ==========================================================================
   2. Dropdowns & Navigation
   ========================================================================== */
function initDropdowns() {
  const userMenuBtn = document.getElementById('userMenuBtn');
  const userDropdownMenu = document.getElementById('userDropdownMenu');

  if (userMenuBtn && userDropdownMenu) {
    userMenuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      userDropdownMenu.classList.toggle('show');
    });

    document.addEventListener('click', () => {
      userDropdownMenu.classList.remove('show');
    });
  }
}

function initMobileNav() {
  const mobileToggle = document.getElementById('mobileToggle');
  const navLinks = document.querySelector('.nav-links');

  if (mobileToggle && navLinks) {
    mobileToggle.addEventListener('click', () => {
      const isVisible = navLinks.style.display === 'flex';
      navLinks.style.display = isVisible ? 'none' : 'flex';
      if (!isVisible) {
        navLinks.style.position = 'absolute';
        navLinks.style.top = '100%';
        navLinks.style.left = '0';
        navLinks.style.right = '0';
        navLinks.style.background = 'var(--bg-surface)';
        navLinks.style.flexDirection = 'column';
        navLinks.style.padding = '1rem';
        navLinks.style.borderBottom = '1px solid var(--border-color)';
      }
    });
  }
}

/* ==========================================================================
   3. Toast Notification System
   ========================================================================== */
window.showToast = function(message, type = 'info', duration = 4000) {
  let container = document.getElementById('toastContainer');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toastContainer';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;

  const iconMap = {
    success: '✅',
    danger: '❌',
    warning: '⚠️',
    info: 'ℹ️'
  };

  toast.innerHTML = `
    <div class="toast-content">
      <span>${iconMap[type] || 'ℹ️'}</span>
      <span>${message}</span>
    </div>
    <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
  `;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
};

/* ==========================================================================
   4. Modals & Dialogs
   ========================================================================== */
function initModals() {
  // Quick Add Expense Modal triggers
  const quickAddBtns = document.querySelectorAll('[data-open-modal="quickAddModal"]');
  const quickAddModal = document.getElementById('quickAddModal');
  const closeModalBtns = document.querySelectorAll('[data-close-modal]');

  quickAddBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      if (quickAddModal) quickAddModal.classList.add('show');
    });
  });

  closeModalBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const modal = btn.closest('.modal-backdrop');
      if (modal) modal.classList.remove('show');
    });
  });

  // Close modal on backdrop click
  document.querySelectorAll('.modal-backdrop').forEach(modal => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.remove('show');
      }
    });
  });
}

/* ==========================================================================
   5. Floating AI Chat Widget
   ========================================================================== */
function initFloatingChat() {
  const chatToggleBtn = document.getElementById('floatingChatBtn');
  const chatDrawer = document.getElementById('chatDrawer');
  const chatCloseBtn = document.getElementById('chatDrawerClose');
  const chatForm = document.getElementById('drawerChatForm');
  const chatInput = document.getElementById('drawerChatInput');
  const chatBody = document.getElementById('chatDrawerBody');

  if (chatToggleBtn && chatDrawer) {
    chatToggleBtn.addEventListener('click', () => {
      chatDrawer.classList.toggle('show');
      if (chatDrawer.classList.contains('show') && chatInput) {
        chatInput.focus();
      }
    });

    if (chatCloseBtn) {
      chatCloseBtn.addEventListener('click', () => {
        chatDrawer.classList.remove('show');
      });
    }

    if (chatForm && chatInput && chatBody) {
      chatForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const text = chatInput.value.trim();
        if (!text) return;

        // Append User Bubble
        appendChatBubble(chatBody, text, 'user');
        chatInput.value = '';

        // Append Loading Bubble
        const loadingId = 'loading-' + Date.now();
        appendChatBubble(chatBody, 'Thinking...', 'bot', loadingId);

        try {
          const response = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text })
          });
          const data = await response.json();

          const loadingEl = document.getElementById(loadingId);
          if (loadingEl) loadingEl.remove();

          appendChatBubble(chatBody, data.response || "No response received.", 'bot');
        } catch (err) {
          const loadingEl = document.getElementById(loadingId);
          if (loadingEl) loadingEl.remove();
          appendChatBubble(chatBody, "⚠️ Network error connecting to AI assistant.", 'bot');
        }
      });
    }
  }
}

function appendChatBubble(container, content, sender, customId = null) {
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${sender}`;
  if (customId) bubble.id = customId;

  // Simple Markdown parsing for chat bubbles (bold, list bullets)
  let formatted = content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code style="background:rgba(255,255,255,0.1);padding:2px 4px;border-radius:4px;">$1</code>')
    .replace(/\n/g, '<br>');

  bubble.innerHTML = formatted;
  container.appendChild(bubble);
  container.scrollTop = container.scrollHeight;
}

/* ==========================================================================
   6. Keyboard Shortcuts
   ========================================================================== */
function initKeyboardShortcuts() {
  document.addEventListener('keydown', (e) => {
    // Press 'N' or 'n' to open Add Expense modal when not focused on an input
    if ((e.key === 'n' || e.key === 'N') && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
      const quickAddModal = document.getElementById('quickAddModal');
      if (quickAddModal) {
        e.preventDefault();
        quickAddModal.classList.add('show');
        const descInput = quickAddModal.querySelector('input[name="description"]');
        if (descInput) descInput.focus();
      }
    }

    // Escape to close open modals
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal-backdrop.show').forEach(m => m.classList.remove('show'));
      const drawer = document.getElementById('chatDrawer');
      if (drawer) drawer.classList.remove('show');
    }
  });
}
