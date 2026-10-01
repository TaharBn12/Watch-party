/* global fetch, window */
(function () {
    'use strict';

    async function request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');
        if (options.body && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }

        const accessToken = await window.WatchPartyAuth.getAccessToken().catch(() => null);
        if (accessToken && !headers.has('Authorization')) {
            headers.set('Authorization', `Bearer ${accessToken}`);
        }

        const response = await fetch(path, {
            ...options,
            headers
        });

        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json') ? await response.json() : await response.text();

        if (!response.ok) {
            throw normalizeError(payload, response.status);
        }

        return payload;
    }

    function normalizeError(payload, status) {
        if (payload && typeof payload === 'object') {
            const validation = payload.validationErrors && Object.keys(payload.validationErrors).length
                ? `: ${Object.values(payload.validationErrors).join('، ')}`
                : '';
            return new Error(`${payload.message || 'فشل الطلب'}${validation}`);
        }
        return new Error(payload || `فشل الطلب برمز ${status}`);
    }

    function jsonBody(data) {
        return JSON.stringify(data || {});
    }

    function roomUrlFromInvite(inviteCode) {
        return `/room.html?invite=${encodeURIComponent(inviteCode)}`;
    }

    window.WatchPartyApi = {
        request,
        roomUrlFromInvite,

        createRoom(data) {
            return request('/api/rooms', {method: 'POST', body: jsonBody(data)});
        },

        getPublicRooms() {
            return request('/api/rooms/public');
        },

        getRoom(roomId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}`);
        },

        getRoomPreview(inviteCode) {
            return request(`/api/rooms/invite/${encodeURIComponent(inviteCode)}`);
        },

        joinRoom(roomId, password) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/members/join`, {
                method: 'POST',
                body: jsonBody({password: password || null})
            });
        },

        joinByInvite(inviteCode, password) {
            return request(`/api/rooms/invite/${encodeURIComponent(inviteCode)}/join`, {
                method: 'POST',
                body: jsonBody({password: password || null})
            });
        },

        getMembers(roomId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/members`);
        },

        getPlaylist(roomId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist`);
        },

        addPlaylistItem(roomId, data) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist`, {
                method: 'POST',
                body: jsonBody(data)
            });
        },

        playPlaylistItem(roomId, itemId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist/${encodeURIComponent(itemId)}/play`, {
                method: 'POST'
            });
        },

        playNext(roomId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist/next`, {method: 'POST'});
        },

        skipPlaylistItem(roomId, itemId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist/${encodeURIComponent(itemId)}/skip`, {
                method: 'POST'
            });
        },

        deletePlaylistItem(roomId, itemId) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playlist/${encodeURIComponent(itemId)}`, {
                method: 'DELETE'
            });
        },

        changeVideo(roomId, videoUrl) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/video`, {
                method: 'POST',
                body: jsonBody({videoUrl})
            });
        },

        updatePlayback(roomId, status, positionSeconds) {
            return request(`/api/rooms/${encodeURIComponent(roomId)}/playback`, {
                method: 'POST',
                body: jsonBody({status, positionSeconds})
            });
        }
    };
})();
