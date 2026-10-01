/* global window, document, WebSocket, StompJs */
(function () {
    'use strict';

    const Ui = window.WatchPartyUi;
    const Auth = window.WatchPartyAuth;
    const Api = window.WatchPartyApi;
    const Player = window.WatchPartyVideoPlayer;

    const state = {
        session: null,
        user: null,
        roomId: null,
        inviteCode: null,
        room: null,
        stomp: null,
        sync: null,
        call: null,
        connected: false,
        members: [],
        playlist: []
    };

    document.addEventListener('DOMContentLoaded', () => {
        boot().catch((error) => {
            Ui.toast(error.message, 'error');
            setConnectionState('خطأ', 'error');
        });
    });

    async function boot() {
        await Auth.init();
        state.session = await Auth.requireSession();
        if (!state.session) {
            return;
        }
        state.user = state.session.user;

        state.roomId = Ui.getQueryParam('roomId');
        state.inviteCode = Ui.getQueryParam('invite') || Ui.getQueryParam('code');

        bindStaticActions();

        if (state.inviteCode) {
            await prepareInviteJoin(state.inviteCode);
            return;
        }
        if (!state.roomId) {
            throw new Error('رابط الغرفة غير مكتمل. افتح الغرفة من رابط دعوة صحيح.');
        }

        await enterRoom(state.roomId);
    }

    function bindStaticActions() {
        document.getElementById('signOutButton')?.addEventListener('click', async () => {
            await disconnectRealtime();
            await Auth.signOut();
            window.location.href = '/index.html';
        });

        document.getElementById('copyInviteButton')?.addEventListener('click', async () => {
            if (!state.room?.inviteCode) {
                Ui.toast('لا يوجد رابط دعوة بعد', 'warning');
                return;
            }
            const inviteUrl = `${window.location.origin}/room.html?invite=${encodeURIComponent(state.room.inviteCode)}`;
            await Ui.copy(inviteUrl);
            Ui.toast('تم نسخ رابط الدعوة', 'success');
        });

        document.getElementById('joinRoomGateForm')?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = Ui.formToObject(form);
            Ui.setBusy(form, true, 'جارٍ الانضمام...');
            try {
                let member;
                if (state.inviteCode) {
                    member = await Api.joinByInvite(state.inviteCode, data.password);
                } else {
                    member = await Api.joinRoom(state.roomId, data.password);
                }
                state.roomId = member.roomId;
                await enterRoom(state.roomId);
            } catch (error) {
                Ui.toast(error.message, 'error');
            } finally {
                Ui.setBusy(form, false);
            }
        });

        bindRoomForms();
        bindCallPlaceholders();
    }

    async function prepareInviteJoin(inviteCode) {
        const preview = await Api.getRoomPreview(inviteCode);
        state.roomId = preview.id;
        Ui.text(document.getElementById('joinGateTitle'), preview.name);
        Ui.text(
            document.getElementById('joinGateText'),
            preview.protectedRoom ? 'هذه الغرفة محمية بكلمة سر.' : 'اضغط انضمام للدخول إلى الغرفة.'
        );
        Ui.show(document.getElementById('joinGate'));
        Ui.hide(document.getElementById('roomContent'));

        if (!preview.protectedRoom) {
            try {
                const member = await Api.joinByInvite(inviteCode, null);
                state.roomId = member.roomId;
                await enterRoom(state.roomId);
            } catch (error) {
                Ui.toast(error.message, 'warning');
            }
        }
    }

    async function enterRoom(roomId) {
        Ui.hide(document.getElementById('joinGate'));
        Ui.show(document.getElementById('roomContent'));
        await loadRoomData(roomId);
        initSync();
        initCall();
        state.sync.applyRoomState(state.room, {applyPlayback: true});
        await connectRealtime();
    }

    async function loadRoomData(roomId) {
        const [room, members, playlist] = await Promise.all([
            Api.getRoom(roomId),
            Api.getMembers(roomId),
            Api.getPlaylist(roomId)
        ]);

        state.room = room;
        state.members = members;
        state.playlist = playlist;

        renderRoom(room);
        renderMembers(members);
        renderPlaylist(playlist);
        await Player.loadVideo(room.currentVideoUrl, room.currentVideoType);
        updateSeekInput(room.playbackPositionSeconds || 0);
    }

    function initSync() {
        if (state.sync) {
            return;
        }
        if (!window.WatchPartySync || typeof window.WatchPartySync.create !== 'function') {
            throw new Error('تعذر تحميل وحدة مزامنة الفيديو.');
        }

        state.sync = window.WatchPartySync.create({
            player: Player,
            getRoomId: () => state.roomId,
            getUserId: () => state.user?.id,
            getRoom: () => state.room,
            setRoom: (room) => {
                state.room = {...(state.room || {}), ...(room || {})};
                if (state.room.playbackPositionSeconds !== undefined) {
                    updateSeekInput(state.room.playbackPositionSeconds);
                }
            },
            publish,
            onDriftCorrected: (drift) => {
                console.info(`Watch Party drift corrected: ${drift.toFixed(2)}s`);
            }
        });
        state.sync.start();
    }

    function initCall() {
        if (state.call) {
            return;
        }
        if (!window.WatchPartyWebRTC || typeof window.WatchPartyWebRTC.create !== 'function') {
            throw new Error('تعذر تحميل وحدة WebRTC.');
        }

        state.call = window.WatchPartyWebRTC.create({
            getRoomId: () => state.roomId,
            getUserId: () => state.user?.id,
            getMembers: () => state.members,
            publish,
            toast: Ui.toast
        });
    }

    function renderRoom(room) {
        Ui.text(document.getElementById('roomTitle'), room.name || 'غرفة مشاهدة');
        document.title = `${room.name || 'غرفة'} | Watch Party`;
    }

    function renderMembers(members) {
        const list = document.getElementById('membersList');
        const count = document.getElementById('membersCount');
        Ui.text(count, members.length);
        if (!list) {
            return;
        }
        if (!members.length) {
            list.innerHTML = '<li class="empty-state">لا يوجد أعضاء ظاهرون.</li>';
            return;
        }

        list.innerHTML = members.map((member) => {
            const isMe = member.userId === state.user.id;
            const name = isMe ? `${Auth.displayName(state.user)} (أنت)` : shortId(member.userId);
            const role = member.role === 'HOST' ? 'مضيف' : 'عضو';
            return `
                <li class="member-item" data-user-id="${Ui.escapeHtml(member.userId)}">
                    <span class="member-main">
                        <span class="member-avatar">${Ui.escapeHtml(Ui.initials(name))}</span>
                        <span>
                            <strong class="member-name">${Ui.escapeHtml(name)}</strong>
                            <small>${role}${member.canControl ? ' • يتحكم' : ''}</small>
                        </span>
                    </span>
                    <span class="badge ${member.connected ? '' : 'muted'}">${member.connected ? 'متصل' : 'غير متصل'}</span>
                </li>
            `;
        }).join('');
    }

    function renderPlaylist(items) {
        const list = document.getElementById('playlistItems');
        if (!list) {
            return;
        }
        if (!items.length) {
            list.innerHTML = '<li class="empty-state">قائمة الانتظار فارغة.</li>';
            return;
        }

        list.innerHTML = items.map((item) => `
            <li class="playlist-item" data-item-id="${Ui.escapeHtml(item.id)}">
                <span>
                    <strong class="playlist-title">${Ui.escapeHtml(item.title || item.url)}</strong>
                    <small>${item.videoType} • ${item.status}</small>
                </span>
                <span class="playlist-actions">
                    <button class="btn btn-ghost" type="button" data-action="play">تشغيل</button>
                    <button class="btn btn-ghost" type="button" data-action="skip">تخطي</button>
                    <button class="btn btn-danger-soft" type="button" data-action="delete">حذف</button>
                </span>
            </li>
        `).join('');
    }

    function bindRoomForms() {
        document.getElementById('chatForm')?.addEventListener('submit', (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = Ui.formToObject(form);
            if (!data.content.trim()) {
                return;
            }
            publish(`/app/rooms/${state.roomId}/chat`, {content: data.content.trim()});
            form.reset();
        });

        Ui.qsa('[data-emoji]').forEach((button) => {
            button.addEventListener('click', () => {
                const input = document.querySelector('#chatForm input[name="content"]');
                input.value = `${input.value}${button.dataset.emoji}`;
                input.focus();
            });
        });

        document.getElementById('playButton')?.addEventListener('click', () => {
            state.sync?.requestPlay();
        });

        document.getElementById('pauseButton')?.addEventListener('click', () => {
            state.sync?.requestPause();
        });

        document.getElementById('seekButton')?.addEventListener('click', () => {
            const position = Number(document.getElementById('seekInput')?.value || 0);
            state.sync?.requestSeek(position);
        });

        document.getElementById('changeVideoForm')?.addEventListener('submit', (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = Ui.formToObject(form);
            state.sync?.requestChangeVideo(data.videoUrl);
            form.reset();
        });

        document.getElementById('playlistForm')?.addEventListener('submit', async (event) => {
            event.preventDefault();
            const form = event.currentTarget;
            Ui.setBusy(form, true, 'جارٍ الإضافة...');
            try {
                const data = Ui.formToObject(form);
                await Api.addPlaylistItem(state.roomId, {url: data.url, title: data.title || null});
                state.playlist = await Api.getPlaylist(state.roomId);
                renderPlaylist(state.playlist);
                form.reset();
                Ui.toast('تمت إضافة الفيديو إلى قائمة الانتظار', 'success');
            } catch (error) {
                Ui.toast(error.message, 'error');
            } finally {
                Ui.setBusy(form, false);
            }
        });

        document.getElementById('playlistItems')?.addEventListener('click', async (event) => {
            const button = event.target.closest('button[data-action]');
            const item = event.target.closest('[data-item-id]');
            if (!button || !item) {
                return;
            }
            await handlePlaylistAction(button.dataset.action, item.dataset.itemId);
        });

        document.getElementById('playNextButton')?.addEventListener('click', async () => {
            try {
                const item = await Api.playNext(state.roomId);
                if (item) {
                    Ui.toast('تم تشغيل الفيديو التالي', 'success');
                    state.sync?.requestChangeVideo(item.url);
                    state.playlist = await Api.getPlaylist(state.roomId);
                    renderPlaylist(state.playlist);
                } else {
                    Ui.toast('لا يوجد فيديو تالٍ', 'warning');
                }
            } catch (error) {
                Ui.toast(error.message, 'error');
            }
        });
    }

    async function handlePlaylistAction(action, itemId) {
        try {
            if (action === 'play') {
                const item = await Api.playPlaylistItem(state.roomId, itemId);
                Ui.toast('تم تشغيل العنصر', 'success');
                state.sync?.requestChangeVideo(item.url);
            }
            if (action === 'skip') {
                await Api.skipPlaylistItem(state.roomId, itemId);
                Ui.toast('تم تخطي العنصر', 'success');
            }
            if (action === 'delete') {
                await Api.deletePlaylistItem(state.roomId, itemId);
                Ui.toast('تم حذف العنصر', 'success');
            }
            state.playlist = await Api.getPlaylist(state.roomId);
            renderPlaylist(state.playlist);
        } catch (error) {
            Ui.toast(error.message, 'error');
        }
    }

    async function connectRealtime() {
        if (state.stomp?.active) {
            return;
        }
        const token = await Auth.getAccessToken();
        if (!token) {
            throw new Error('جلسة الدخول انتهت. سجّل الدخول مجددًا.');
        }
        if (!window.StompJs || typeof window.StompJs.Client !== 'function') {
            throw new Error('تعذر تحميل مكتبة STOMP من CDN.');
        }

        const config = await window.WatchPartyConfig.load();
        const wsUrl = window.WatchPartyConfig.webSocketUrl(config.webSocket?.endpoint || '/ws', token);

        state.stomp = new StompJs.Client({
            webSocketFactory: () => new WebSocket(wsUrl),
            connectHeaders: {
                Authorization: `Bearer ${token}`
            },
            reconnectDelay: 4000,
            debug: () => undefined,
            onConnect: () => {
                state.connected = true;
                setConnectionState('متصل', 'connected');
                subscribeRealtime();
                publish(`/app/rooms/${state.roomId}/join`, {});
            },
            onStompError: (frame) => {
                setConnectionState('خطأ', 'error');
                Ui.toast(frame.headers.message || 'خطأ في WebSocket', 'error');
            },
            onWebSocketClose: () => {
                state.connected = false;
                setConnectionState('غير متصل', 'muted');
            }
        });

        setConnectionState('جارٍ الاتصال...', 'muted');
        state.stomp.activate();
    }

    function subscribeRealtime() {
        state.stomp.subscribe(`/topic/rooms/${state.roomId}/events`, (message) => {
            handleEnvelope(JSON.parse(message.body));
        });
        state.stomp.subscribe(`/user/queue/rooms/${state.roomId}/sync`, (message) => {
            handleEnvelope(JSON.parse(message.body));
        });
        state.stomp.subscribe(`/user/queue/rooms/${state.roomId}/webrtc`, (message) => {
            handleEnvelope(JSON.parse(message.body));
        });
        state.stomp.subscribe('/user/queue/errors', (message) => {
            const error = JSON.parse(message.body);
            Ui.toast(error.message || 'خطأ في WebSocket', 'error');
        });
    }

    function publish(destination, body) {
        if (!state.stomp || !state.connected) {
            Ui.toast('الاتصال اللحظي غير جاهز بعد', 'warning');
            return Promise.resolve(false);
        }
        state.stomp.publish({destination, body: JSON.stringify(body || {})});
        return Promise.resolve(true);
    }

    async function disconnectRealtime() {
        await state.call?.leaveCall({notify: true});
        state.sync?.stop();
        if (state.stomp?.active) {
            await state.stomp.deactivate();
        }
    }

    function handleEnvelope(envelope) {
        if (!envelope || !envelope.type) {
            return;
        }
        if (state.sync?.handleEnvelope(envelope)) {
            return;
        }
        if (state.call?.handleEnvelope(envelope)) {
            return;
        }
        if (envelope.type === 'JOIN' || envelope.type === 'LEAVE') {
            upsertMember(envelope.payload?.member);
            return;
        }
        if (envelope.type === 'CHAT') {
            appendChatMessage(envelope.payload?.message);
            return;
        }
        if (envelope.type === 'SYNC_STATE') {
            applySyncState(envelope.payload);
        }
    }

    function upsertMember(member) {
        if (!member) {
            return;
        }
        const index = state.members.findIndex((item) => item.userId === member.userId);
        if (index >= 0) {
            state.members[index] = member;
        } else {
            state.members.push(member);
        }
        renderMembers(state.members.filter((item) => !item.leftAt));
    }

    function appendChatMessage(message) {
        if (!message) {
            return;
        }
        const container = document.getElementById('chatMessages');
        if (!container) {
            return;
        }
        const bubble = document.createElement('div');
        bubble.className = 'chat-bubble';
        bubble.innerHTML = `
            <div class="chat-meta">
                <strong>${Ui.escapeHtml(shortId(message.senderId))}</strong>
                <span>${Ui.escapeHtml(Ui.formatDate(message.createdAt))}</span>
            </div>
            <div>${Ui.escapeHtml(message.content)}</div>
        `;
        container.appendChild(bubble);
        container.scrollTop = container.scrollHeight;
    }

    async function applySyncState(payload) {
        if (!payload) {
            return;
        }
        if (payload.room) {
            state.room = payload.room;
            renderRoom(payload.room);
            if (state.sync) {
                state.sync.applyRoomState(payload.room, {applyPlayback: true});
            } else {
                await Player.loadVideo(payload.room.currentVideoUrl, payload.room.currentVideoType);
                updateSeekInput(payload.room.playbackPositionSeconds || 0);
            }
        }
        if (Array.isArray(payload.members)) {
            state.members = payload.members;
            renderMembers(state.members);
        }
        if (Array.isArray(payload.recentMessages)) {
            renderRecentMessages(payload.recentMessages);
        }
        if (Array.isArray(payload.playlist)) {
            state.playlist = payload.playlist;
            renderPlaylist(state.playlist);
        }
    }

    function renderRecentMessages(messages) {
        const container = document.getElementById('chatMessages');
        if (!container) {
            return;
        }
        container.innerHTML = '';
        messages.forEach(appendChatMessage);
    }

    function updateSeekInput(value) {
        const input = document.getElementById('seekInput');
        if (input) {
            input.value = Number(value || 0).toFixed(1);
        }
    }

    function setConnectionState(text, statusClass) {
        const pill = document.getElementById('connectionState');
        if (!pill) {
            return;
        }
        pill.className = `connection-pill ${statusClass || 'muted'}`;
        pill.textContent = text;
    }

    function shortId(id) {
        return id ? `مستخدم ${String(id).slice(0, 8)}` : 'مستخدم';
    }

    function bindCallPlaceholders() {
        document.getElementById('startCallButton')?.addEventListener('click', async () => {
            try {
                await state.call?.startCall();
            } catch (error) {
                Ui.toast(error.message || 'تعذر بدء المكالمة', 'error');
            }
        });

        document.getElementById('leaveCallButton')?.addEventListener('click', async () => {
            await state.call?.leaveCall();
        });

        document.getElementById('micButton')?.addEventListener('click', () => {
            const enabled = state.call?.toggleAudio();
            if (enabled !== null && enabled !== undefined) {
                Ui.toast(enabled ? 'تم تشغيل الميكروفون' : 'تم كتم الميكروفون', 'success');
            }
        });

        document.getElementById('cameraButton')?.addEventListener('click', () => {
            const enabled = state.call?.toggleVideo();
            if (enabled !== null && enabled !== undefined) {
                Ui.toast(enabled ? 'تم تشغيل الكاميرا' : 'تم إيقاف الكاميرا', 'success');
            }
        });
    }
})();
