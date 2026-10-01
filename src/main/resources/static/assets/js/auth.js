/* global window */
(function () {
    'use strict';

    let client = null;
    let initPromise = null;

    async function init() {
        if (initPromise) {
            return initPromise;
        }

        initPromise = (async () => {
            const config = await window.WatchPartyConfig.load();
            const supabaseConfig = config.supabase || {};
            if (!supabaseConfig.configured || !supabaseConfig.url || !supabaseConfig.anonKey) {
                throw new Error('إعدادات Supabase العامة غير مضبوطة. اضبط SUPABASE_URL و SUPABASE_ANON_KEY في الخادم.');
            }
            if (!window.supabase || typeof window.supabase.createClient !== 'function') {
                throw new Error('تعذر تحميل مكتبة Supabase من CDN. تحقق من الاتصال بالإنترنت.');
            }
            client = window.supabase.createClient(supabaseConfig.url, supabaseConfig.anonKey, {
                auth: {
                    persistSession: true,
                    autoRefreshToken: true,
                    detectSessionInUrl: true
                }
            });
            return client;
        })();

        return initPromise;
    }

    async function getClient() {
        return client || init();
    }

    async function getSession() {
        const supabaseClient = await getClient();
        const {data, error} = await supabaseClient.auth.getSession();
        if (error) {
            throw error;
        }
        return data.session || null;
    }

    async function getUser() {
        const session = await getSession();
        return session ? session.user : null;
    }

    async function getAccessToken() {
        const session = await getSession();
        return session ? session.access_token : null;
    }

    async function requireSession() {
        const session = await getSession();
        if (!session) {
            const returnTo = encodeURIComponent(`${window.location.pathname}${window.location.search}`);
            window.location.href = `/login.html?return=${returnTo}`;
            return null;
        }
        return session;
    }

    async function signIn(email, password) {
        const supabaseClient = await getClient();
        const {data, error} = await supabaseClient.auth.signInWithPassword({email, password});
        if (error) {
            throw error;
        }
        return data;
    }

    async function signUp(email, password, displayName) {
        const supabaseClient = await getClient();
        const {data, error} = await supabaseClient.auth.signUp({
            email,
            password,
            options: {
                data: {
                    display_name: displayName,
                    name: displayName,
                    is_guest: false
                }
            }
        });
        if (error) {
            throw error;
        }
        return data;
    }

    async function signInGuest(displayName) {
        const supabaseClient = await getClient();
        if (typeof supabaseClient.auth.signInAnonymously !== 'function') {
            throw new Error('نسخة Supabase الحالية لا تدعم الدخول المجهول. استخدم بريدًا إلكترونيًا أو حدّث المكتبة.');
        }

        const {data, error} = await supabaseClient.auth.signInAnonymously({
            options: {
                data: {
                    display_name: displayName,
                    name: displayName,
                    is_guest: true,
                    guest: true
                }
            }
        });
        if (error) {
            throw error;
        }
        return data;
    }

    async function signOut() {
        const supabaseClient = await getClient();
        const {error} = await supabaseClient.auth.signOut();
        if (error) {
            throw error;
        }
    }

    function displayName(user) {
        if (!user) {
            return 'زائر';
        }
        return user.user_metadata?.display_name
            || user.user_metadata?.name
            || user.email
            || `مستخدم ${String(user.id).slice(0, 8)}`;
    }

    async function renderAuthActions(container) {
        if (!container) {
            return;
        }
        try {
            await init();
            const user = await getUser();
            if (!user) {
                container.innerHTML = '<a class="btn btn-ghost" href="/login.html">دخول</a>';
                return;
            }
            container.innerHTML = `
                <span class="badge">${window.WatchPartyUi.escapeHtml(displayName(user))}</span>
                <button class="btn btn-danger-soft" type="button" id="headerSignOutButton">خروج</button>
            `;
            const signOutButton = container.querySelector('#headerSignOutButton');
            signOutButton?.addEventListener('click', async () => {
                await signOut();
                window.WatchPartyUi.toast('تم تسجيل الخروج', 'success');
                window.location.reload();
            });
        } catch (error) {
            container.innerHTML = '<a class="btn btn-ghost" href="/login.html">إعداد الدخول</a>';
        }
    }

    window.WatchPartyAuth = {
        init,
        getClient,
        getSession,
        getUser,
        getAccessToken,
        requireSession,
        signIn,
        signUp,
        signInGuest,
        signOut,
        displayName,
        renderAuthActions
    };
})();
