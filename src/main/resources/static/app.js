/**
 * AI Expense Tracker - Frontend Application Controller
 * Handles Password Toggles, Demo Credential Fill, Visualizations, Toasts, Modals, Keyboard Shortcuts & Floating Chat
 */

document.addEventListener('DOMContentLoaded', () => {
  initPasswordToggles();
  initDemoCredentials();
  initDropdowns();
  initMobileNav();
  initModals();
  initFloatingChat();
  initKeyboardShortcuts();
});

/* ==========================================================================
   1. Universal Password Visibility Toggle (Eye Button)
   ========================================================================== */
function initPasswordToggles() {
  const eyeSvg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
      <circle cx="12" cy="12" r="3"></circle>
    </svg>
  `;

  const eyeOffSvg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
      <line x1="1" y1="1" x2="23" y2="23"></line>
    </svg>
  `;

  document.addEventListener('click', (e) => {
    const toggleBtn = e.target.closest('.password-toggle-btn');
    if (!toggleBtn) return;

    e.preventDefault();
    e.stopPropagation();

    // Find input in the same group or via target attribute
    let targetInput = null;
    if (toggleBtn.dataset.target) {
      targetInput = document.querySelector(toggleBtn.dataset.target);
    } else {
      const group = toggleBtn.closest('.password-input-group') || toggleBtn.closest('.form-group');
      if (group) {
        targetInput = group.querySelector('input[type="password"], input[type="text"]');
      }
    }

    if (!targetInput) return;

    const isPassword = targetInput.getAttribute('type') === 'password';
    targetInput.setAttribute('type', isPassword ? 'text' : 'password');
    toggleBtn.innerHTML = isPassword ? eyeOffSvg : eyeSvg;
    toggleBtn.setAttribute('title', isPassword ? 'Hide password' : 'Show password');
    toggleBtn.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
    toggleBtn.setAttribute('aria-pressed', isPassword ? 'true' : 'false');
  });
}

/* ==========================================================================
   2. Demo Credentials Quick-Fill Helper
   ========================================================================== */
function initDemoCredentials() {
  document.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-fill-email]');
    if (!pill) return;

    e.preventDefault();
    const email = pill.getAttribute('data-fill-email');
    const password = pill.getAttribute('data-fill-password');

    const emailInput = document.querySelector('input[name="email"]');
    const passwordInput = document.querySelector('input[name="password"]');

    if (emailInput && email) {
      emailInput.value = email;
    }
    if (passwordInput && password) {
      passwordInput.value = password;
    }

    if (window.showToast) {
      window.showToast(`Loaded ${email} credentials! Click Log In.`, 'info', 2500);
    }
  });
}

/* ==========================================================================
   3. Dropdowns & Navigation
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
        navLinks.style.background = '#ffffff';
        navLinks.style.flexDirection = 'column';
        navLinks.style.padding = '1rem';
        navLinks.style.borderBottom = '1px solid var(--border-color)';
        navLinks.style.boxShadow = 'var(--shadow-md)';
      }
    });
  }
}

/* ==========================================================================
   4. Toast Notification System
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
   5. Modals & Dialogs
   ========================================================================== */
function initModals() {
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

  document.querySelectorAll('.modal-backdrop').forEach(modal => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.remove('show');
      }
    });
  });
}

/* ==========================================================================
   6. Floating AI Chat Widget
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

        appendChatBubble(chatBody, text, 'user');
        chatInput.value = '';

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

  let formatted = content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code style="background:#e2e8f0;padding:2px 4px;border-radius:4px;color:#0f172a;">$1</code>')
    .replace(/\n/g, '<br>');

  bubble.innerHTML = formatted;
  container.appendChild(bubble);
  container.scrollTop = container.scrollHeight;
}

/* ==========================================================================
   7. Keyboard Shortcuts
   ========================================================================== */
function initKeyboardShortcuts() {
  document.addEventListener('keydown', (e) => {
    if ((e.key === 'n' || e.key === 'N') && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
      const quickAddModal = document.getElementById('quickAddModal');
      if (quickAddModal) {
        e.preventDefault();
        quickAddModal.classList.add('show');
        const descInput = quickAddModal.querySelector('input[name="description"]');
        if (descInput) descInput.focus();
      }
    }

    if (e.key === 'Escape') {
      document.querySelectorAll('.modal-backdrop.show').forEach(m => m.classList.remove('show'));
      const drawer = document.getElementById('chatDrawer');
      if (drawer) drawer.classList.remove('show');
    }
  });
}
