/* global document, navigator */
(function () {
    'use strict';

    const Ui = {
        qs(selector, root = document) {
            return root.querySelector(selector);
        },

        qsa(selector, root = document) {
            return Array.from(root.querySelectorAll(selector));
        },

        show(element) {
            if (element) {
                element.classList.remove('hidden');
            }
        },

        hide(element) {
            if (element) {
                element.classList.add('hidden');
            }
        },

        text(element, value) {
            if (element) {
                element.textContent = value == null ? '' : String(value);
            }
        },

        setBusy(formOrButton, busy, busyText) {
            if (!formOrButton) {
                return;
            }
            const buttons = formOrButton.matches && formOrButton.matches('button')
                ? [formOrButton]
                : Array.from(formOrButton.querySelectorAll('button'));

            buttons.forEach((button) => {
                if (busy) {
                    button.dataset.originalText = button.textContent;
                    button.textContent = busyText || 'جارٍ التنفيذ...';
                    button.disabled = true;
                } else {
                    button.disabled = false;
                    if (button.dataset.originalText) {
                        button.textContent = button.dataset.originalText;
                        delete button.dataset.originalText;
                    }
                }
            });
        },

        toast(message, type = 'success', timeout = 3600) {
            const root = document.getElementById('toastRoot');
            if (!root) {
                console.log(message);
                return;
            }

            const toast = document.createElement('div');
            toast.className = `toast ${type}`;
            toast.textContent = message;
            root.appendChild(toast);

            window.setTimeout(() => {
                toast.style.opacity = '0';
                toast.style.transform = 'translateY(8px)';
                window.setTimeout(() => toast.remove(), 220);
            }, timeout);
        },

        formatDate(value) {
            if (!value) {
                return '';
            }
            try {
                return new Intl.DateTimeFormat('ar-DZ', {
                    hour: '2-digit',
                    minute: '2-digit',
                    day: '2-digit',
                    month: 'short'
                }).format(new Date(value));
            } catch (error) {
                return String(value);
            }
        },

        formatSeconds(seconds) {
            const total = Math.max(0, Math.floor(Number(seconds) || 0));
            const minutes = Math.floor(total / 60);
            const rest = total % 60;
            return `${minutes}:${String(rest).padStart(2, '0')}`;
        },

        initials(nameOrId) {
            const value = (nameOrId || '؟').trim();
            return value.slice(0, 1).toUpperCase();
        },

        escapeHtml(value) {
            return String(value == null ? '' : value)
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#039;');
        },

        formToObject(form) {
            const data = new FormData(form);
            return Object.fromEntries(data.entries());
        },

        copy(text) {
            if (navigator.clipboard && window.isSecureContext) {
                return navigator.clipboard.writeText(text);
            }
            const input = document.createElement('textarea');
            input.value = text;
            input.setAttribute('readonly', 'readonly');
            input.style.position = 'fixed';
            input.style.opacity = '0';
            document.body.appendChild(input);
            input.select();
            document.execCommand('copy');
            input.remove();
            return Promise.resolve();
        },

        getQueryParam(name) {
            return new URLSearchParams(window.location.search).get(name);
        },

        normalizeInvite(input) {
            const raw = String(input || '').trim();
            if (!raw) {
                return '';
            }
            try {
                const url = new URL(raw, window.location.origin);
                return url.searchParams.get('invite') || url.searchParams.get('code') || raw.replace(/^.*\//, '');
            } catch (error) {
                return raw.replace(/^.*\//, '');
            }
        }
    };

    window.WatchPartyUi = Ui;
})();
