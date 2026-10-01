/* global window */
(function () {
    'use strict';

    const PLAYBACK_TYPES = new Set(['PLAY', 'PAUSE', 'SEEK', 'CHANGE_VIDEO']);
    const DRIFT_THRESHOLD_SECONDS = 2;
    const DRIFT_CHECK_MS = 1000;
    const LOCAL_THROTTLE_MS = 650;
    const REMOTE_SUPPRESS_MS = 1300;

    function create(options) {
        const player = options.player;
        let applyingRemote = false;
        let suppressLocalUntil = 0;
        let driftTimer = null;
        let lastLocalCommand = {type: null, at: 0, position: null};

        function start() {
            player.on('play', handleNativePlay);
            player.on('pause', handleNativePause);
            player.on('seeked', handleNativeSeek);
            driftTimer = window.setInterval(correctDriftIfNeeded, DRIFT_CHECK_MS);
        }

        function stop() {
            player.off('play', handleNativePlay);
            player.off('pause', handleNativePause);
            player.off('seeked', handleNativeSeek);
            if (driftTimer) {
                window.clearInterval(driftTimer);
                driftTimer = null;
            }
        }

        function handleEnvelope(envelope) {
            if (!envelope || !PLAYBACK_TYPES.has(envelope.type)) {
                return false;
            }

            const payload = envelope.payload || {};
            updateAuthoritativeRoom(payload);

            if (envelope.senderId && envelope.senderId === options.getUserId()) {
                // التعليق بالعربية: نتجاهل تطبيق صدى الحدث الذي أرسلناه نحن حتى لا نعيد تشغيل event محلي ونرسل حلقة جديدة.
                suppressLocal(REMOTE_SUPPRESS_MS);
                return true;
            }

            applyRemotePlayback(envelope.type, payload);
            return true;
        }

        function applyRoomState(room, settings = {}) {
            if (!room) {
                return;
            }
            options.setRoom(room);
            if (settings.applyPlayback) {
                applyRemotePlayback('SYNC_STATE', room);
            }
        }

        function requestPlay() {
            const positionSeconds = player.currentTime();
            suppressLocal(LOCAL_THROTTLE_MS);
            player.play();
            return sendPlaybackCommand('PLAY', {positionSeconds});
        }

        function requestPause() {
            const positionSeconds = player.currentTime();
            suppressLocal(LOCAL_THROTTLE_MS);
            player.pause();
            return sendPlaybackCommand('PAUSE', {positionSeconds});
        }

        function requestSeek(positionSeconds) {
            const safePosition = normalizeSeconds(positionSeconds);
            suppressLocal(LOCAL_THROTTLE_MS);
            player.seek(safePosition);
            return sendPlaybackCommand('SEEK', {
                positionSeconds: safePosition,
                status: player.isPlaying() ? 'PLAYING' : 'PAUSED'
            });
        }

        function requestChangeVideo(videoUrl) {
            suppressLocal(LOCAL_THROTTLE_MS);
            return publish(`/app/rooms/${options.getRoomId()}/video`, {videoUrl});
        }

        function requestSync() {
            return publish(`/app/rooms/${options.getRoomId()}/sync`, {});
        }

        function handleNativePlay() {
            if (shouldIgnoreLocal('PLAY')) {
                return;
            }
            sendPlaybackCommand('PLAY', {positionSeconds: player.currentTime()});
        }

        function handleNativePause() {
            if (shouldIgnoreLocal('PAUSE')) {
                return;
            }
            sendPlaybackCommand('PAUSE', {positionSeconds: player.currentTime()});
        }

        function handleNativeSeek(event) {
            if (shouldIgnoreLocal('SEEK')) {
                return;
            }
            sendPlaybackCommand('SEEK', {
                positionSeconds: normalizeSeconds(event?.currentTime ?? player.currentTime()),
                status: player.isPlaying() ? 'PLAYING' : 'PAUSED'
            });
        }

        function sendPlaybackCommand(type, payload) {
            if (isDuplicateLocal(type, payload.positionSeconds)) {
                return Promise.resolve(false);
            }

            lastLocalCommand = {
                type,
                at: Date.now(),
                position: normalizeSeconds(payload.positionSeconds)
            };

            const destination = {
                PLAY: 'play',
                PAUSE: 'pause',
                SEEK: 'seek'
            }[type];

            return publish(`/app/rooms/${options.getRoomId()}/${destination}`, payload);
        }

        function publish(destination, payload) {
            if (!options.getRoomId()) {
                return Promise.resolve(false);
            }
            return Promise.resolve(options.publish(destination, payload));
        }

        async function applyRemotePlayback(type, payload) {
            if (!payload) {
                return;
            }

            await withRemoteSuppression(async () => {
                const targetPosition = expectedPosition(payload);

                if (payload.currentVideoUrl !== undefined || payload.currentVideoType !== undefined) {
                    await player.loadVideo(payload.currentVideoUrl, payload.currentVideoType);
                }

                if (Number.isFinite(targetPosition)) {
                    player.seek(targetPosition);
                }

                const status = payload.status || payload.playbackStatus;
                if (type === 'PLAY' || status === 'PLAYING') {
                    await player.play();
                } else if (type === 'PAUSE' || type === 'CHANGE_VIDEO' || status === 'PAUSED') {
                    player.pause();
                }
            });
        }

        function updateAuthoritativeRoom(payload) {
            const current = options.getRoom() || {};
            const updated = {...current};

            if (payload.currentVideoUrl !== undefined) {
                updated.currentVideoUrl = payload.currentVideoUrl;
            }
            if (payload.currentVideoType !== undefined) {
                updated.currentVideoType = payload.currentVideoType;
            }
            if (payload.status !== undefined) {
                updated.playbackStatus = payload.status;
            }
            if (payload.playbackStatus !== undefined) {
                updated.playbackStatus = payload.playbackStatus;
            }
            if (payload.positionSeconds !== undefined) {
                updated.playbackPositionSeconds = payload.positionSeconds;
            }
            if (payload.playbackPositionSeconds !== undefined) {
                updated.playbackPositionSeconds = payload.playbackPositionSeconds;
            }
            if (payload.playbackUpdatedAt !== undefined) {
                updated.playbackUpdatedAt = payload.playbackUpdatedAt;
            }

            options.setRoom(updated);
        }

        async function correctDriftIfNeeded() {
            const room = options.getRoom();
            if (!room || !room.currentVideoUrl || !room.playbackStatus) {
                return;
            }
            if (applyingRemote || Date.now() < suppressLocalUntil) {
                return;
            }

            const expected = expectedPosition(room);
            if (!Number.isFinite(expected)) {
                return;
            }

            const actual = player.currentTime();
            const drift = Math.abs(actual - expected);

            if (drift > DRIFT_THRESHOLD_SECONDS) {
                await withRemoteSuppression(async () => {
                    player.seek(expected);
                    if (room.playbackStatus === 'PLAYING') {
                        await player.play();
                    }
                });
                options.onDriftCorrected?.(drift, expected, actual);
                return;
            }

            if (room.playbackStatus === 'PLAYING' && !player.isPlaying()) {
                await withRemoteSuppression(() => player.play());
            }
            if (room.playbackStatus === 'PAUSED' && player.isPlaying()) {
                await withRemoteSuppression(() => player.pause());
            }
        }

        async function withRemoteSuppression(callback) {
            applyingRemote = true;
            suppressLocal(REMOTE_SUPPRESS_MS);
            try {
                await callback();
            } finally {
                window.setTimeout(() => {
                    applyingRemote = false;
                }, 350);
            }
        }

        function shouldIgnoreLocal(type) {
            if (applyingRemote || Date.now() < suppressLocalUntil) {
                return true;
            }
            return isDuplicateLocal(type, player.currentTime());
        }

        function isDuplicateLocal(type, positionSeconds) {
            const now = Date.now();
            const position = normalizeSeconds(positionSeconds);
            return lastLocalCommand.type === type
                && now - lastLocalCommand.at < LOCAL_THROTTLE_MS
                && Math.abs((lastLocalCommand.position || 0) - position) < 0.35;
        }

        function suppressLocal(milliseconds) {
            suppressLocalUntil = Math.max(suppressLocalUntil, Date.now() + milliseconds);
        }

        function expectedPosition(payload) {
            const rawPosition = payload.positionSeconds ?? payload.playbackPositionSeconds;
            const base = normalizeSeconds(rawPosition);
            const status = payload.status || payload.playbackStatus;
            const updatedAt = payload.playbackUpdatedAt;

            if (status !== 'PLAYING' || !updatedAt) {
                return base;
            }

            const updatedTime = new Date(updatedAt).getTime();
            if (!Number.isFinite(updatedTime)) {
                return base;
            }

            const elapsedSeconds = Math.max(0, (Date.now() - updatedTime) / 1000);
            return base + elapsedSeconds;
        }

        function normalizeSeconds(value) {
            const numeric = Number(value);
            if (!Number.isFinite(numeric) || numeric < 0) {
                return 0;
            }
            return Number(numeric.toFixed(3));
        }

        return {
            start,
            stop,
            handleEnvelope,
            applyRoomState,
            requestPlay,
            requestPause,
            requestSeek,
            requestChangeVideo,
            requestSync
        };
    }

    window.WatchPartySync = {create};
})();
