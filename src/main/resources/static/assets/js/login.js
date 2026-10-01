/* global window, document */
(function () {
    'use strict';

    const Ui = window.WatchPartyUi;
    const Auth = window.WatchPartyAuth;

    document.addEventListener('DOMContentLoaded', () => {
        boot().catch((error) => showState(error.message, 'error'));
    });

    async function boot() {
        setupTabs();
        bindForms();
        await Auth.init();

        const session = await Auth.getSession();
        if (session) {
            showState('أنت مسجل الدخول بالفعل. سيتم تحويلك الآن...', 'success');
            window.setTimeout(redirectAfterAuth, 500);
        }
    }

    function setupTabs() {
        Ui.qsa('.tab-button').forEach((button) => {
            button.addEventListener('click', () => {
                Ui.qsa('.tab-button').forEach((item) => {
                    item.classList.remove('active');
                    item.setAttribute('aria-selected', 'false');
                });
                Ui.qsa('.tab-panel').forEach((panel) => {
                    panel.classList.remove('active');
                    panel.hidden = true;
                });

                button.classList.add('active');
                button.setAttribute('aria-selected', 'true');
                const panel = document.getElementById(button.dataset.tab);
                panel.hidden = false;
                panel.classList.add('active');
            });
        });
    }

    function bindForms() {
        const loginForm = document.getElementById('loginForm');
        const signupForm = document.getElementById('signupForm');
        const guestForm = document.getElementById('guestForm');

        loginForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            Ui.setBusy(loginForm, true, 'جارٍ الدخول...');
            try {
                const data = Ui.formToObject(loginForm);
                await Auth.signIn(data.email, data.password);
                showState('تم تسجيل الدخول بنجاح.', 'success');
                redirectAfterAuth();
            } catch (error) {
                showState(humanAuthError(error), 'error');
            } finally {
                Ui.setBusy(loginForm, false);
            }
        });

        signupForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            Ui.setBusy(signupForm, true, 'جارٍ إنشاء الحساب...');
            try {
                const data = Ui.formToObject(signupForm);
                const result = await Auth.signUp(data.email, data.password, data.displayName);
                if (result.session) {
                    showState('تم إنشاء الحساب والدخول بنجاح.', 'success');
                    redirectAfterAuth();
                } else {
                    showState('تم إنشاء الحساب. إذا كان تأكيد البريد مفعّلًا، افتح بريدك لتأكيد الحساب ثم سجّل الدخول.', 'success');
                }
            } catch (error) {
                showState(humanAuthError(error), 'error');
            } finally {
                Ui.setBusy(signupForm, false);
            }
        });

        guestForm?.addEventListener('submit', async (event) => {
            event.preventDefault();
            Ui.setBusy(guestForm, true, 'جارٍ الدخول كضيف...');
            try {
                const data = Ui.formToObject(guestForm);
                await Auth.signInGuest(data.displayName);
                showState('تم الدخول كضيف.', 'success');
                redirectAfterAuth();
            } catch (error) {
                showState(humanAuthError(error), 'error');
            } finally {
                Ui.setBusy(guestForm, false);
            }
        });
    }

    function redirectAfterAuth() {
        const returnTo = Ui.getQueryParam('return') || '/index.html';
        window.location.href = returnTo.startsWith('/') ? returnTo : '/index.html';
    }

    function showState(message, type) {
        const box = document.getElementById('authStateBox');
        if (!box) {
            Ui.toast(message, type);
            return;
        }
        box.textContent = message;
        box.className = `state-box ${type === 'error' ? 'toast error' : ''}`;
        Ui.show(box);
        Ui.toast(message, type === 'error' ? 'error' : 'success');
    }

    function humanAuthError(error) {
        const message = error.message || 'فشل تسجيل الدخول';
        if (message.includes('Invalid login credentials')) {
            return 'البريد أو كلمة المرور غير صحيحة.';
        }
        if (message.includes('Email not confirmed')) {
            return 'يجب تأكيد البريد الإلكتروني قبل الدخول.';
        }
        if (message.toLowerCase().includes('anonymous')) {
            return 'الدخول كضيف غير مفعّل في Supabase. فعّل Anonymous Sign-ins أو استخدم بريدًا إلكترونيًا.';
        }
        return message;
    }
})();
