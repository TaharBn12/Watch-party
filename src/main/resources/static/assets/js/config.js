/* global fetch, window */
(function () {
    'use strict';

    let cachedConfig = null;

    async function load() {
        if (cachedConfig) {
            return cachedConfig;
        }

        const fallback = window.WATCH_PARTY_CONFIG || {};
        try {
            const response = await fetch('/api/public/config', {headers: {'Accept': 'application/json'}});
            if (!response.ok) {
                throw new Error('تعذر تحميل إعدادات التطبيق');
            }
            cachedConfig = await response.json();
        } catch (error) {
            cachedConfig = fallback;
        }

        cachedConfig.supabase = cachedConfig.supabase || {};
        cachedConfig.webSocket = cachedConfig.webSocket || {endpoint: '/ws', sockJsEndpoint: '/ws-sockjs'};
        cachedConfig.webRtc = cachedConfig.webRtc || {stunUrls: [], turnUrls: []};
        return cachedConfig;
    }

    function absoluteHttpUrl(path) {
        if (/^https?:\/\//i.test(path)) {
            return path;
        }
        return `${window.location.origin}${path.startsWith('/') ? path : `/${path}`}`;
    }

    function webSocketUrl(path, accessToken) {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const endpoint = path || '/ws';
        const url = new URL(`${protocol}//${window.location.host}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`);
        if (accessToken) {
            url.searchParams.set('access_token', accessToken);
        }
        return url.toString();
    }

    window.WatchPartyConfig = {
        load,
        absoluteHttpUrl,
        webSocketUrl
    };
})();
