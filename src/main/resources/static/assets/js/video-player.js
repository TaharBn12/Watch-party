/* global window, document, YT */
(function () {
    'use strict';

    const listeners = new Map();
    let youtubePlayer = null;
    let currentType = null;
    let monitorTimer = null;
    let lastSample = null;
    let youtubeReadyResolver;
    const youtubeReady = new Promise((resolve) => {
        youtubeReadyResolver = resolve;
    });

    window.onYouTubeIframeAPIReady = function () {
        youtubeReadyResolver();
    };

    if (window.YT && typeof window.YT.Player === 'function') {
        youtubeReadyResolver();
    }

    document.addEventListener('DOMContentLoaded', () => {
        attachMp4Events();
        startSeekMonitor();
    });

    function on(eventName, handler) {
        if (!listeners.has(eventName)) {
            listeners.set(eventName, new Set());
        }
        listeners.get(eventName).add(handler);
    }

    function off(eventName, handler) {
        listeners.get(eventName)?.delete(handler);
    }

    function emit(eventName, payload = {}) {
        listeners.get(eventName)?.forEach((handler) => {
            try {
                handler(payload);
            } catch (error) {
                console.error(error);
            }
        });
    }

    function elements() {
        return {
            empty: document.getElementById('emptyVideoState'),
            youtube: document.getElementById('youtubePlayer'),
            mp4: document.getElementById('mp4Player')
        };
    }

    async function loadVideo(url, type) {
        const els = elements();
        hideAll(els);
        currentType = type || null;
        resetSeekMonitor();

        if (!url || !type) {
            currentType = null;
            els.empty.classList.remove('hidden');
            emit('videochange', {url: null, type: null});
            return;
        }

        if (type === 'YOUTUBE') {
            els.youtube.classList.remove('hidden');
            const videoId = extractYouTubeId(url);
            if (!videoId) {
                els.empty.classList.remove('hidden');
                emit('videochange', {url: null, type: null});
                return;
            }
            await loadYouTube(videoId);
            emit('videochange', {url, type});
            return;
        }

        if (type === 'MP4') {
            els.mp4.classList.remove('hidden');
            if (els.mp4.src !== url) {
                els.mp4.src = url;
                els.mp4.load();
            }
            emit('videochange', {url, type});
        }
    }

    async function loadYouTube(videoId) {
        await youtubeReady;
        if (!youtubePlayer) {
            youtubePlayer = new YT.Player('youtubePlayer', {
                videoId,
                playerVars: {
                    playsinline: 1,
                    rel: 0,
                    modestbranding: 1,
                    origin: window.location.origin
                },
                events: {
                    onReady: () => resetSeekMonitor(),
                    onStateChange: handleYouTubeStateChange
                }
            });
            return;
        }
        youtubePlayer.loadVideoById(videoId);
        youtubePlayer.pauseVideo();
        resetSeekMonitor();
    }

    function handleYouTubeStateChange(event) {
        if (!window.YT || !YT.PlayerState) {
            return;
        }
        if (event.data === YT.PlayerState.PLAYING) {
            emit('play', {currentTime: currentTime(), type: currentType});
        }
        if (event.data === YT.PlayerState.PAUSED) {
            emit('pause', {currentTime: currentTime(), type: currentType});
        }
        if (event.data === YT.PlayerState.ENDED) {
            emit('ended', {currentTime: currentTime(), type: currentType});
        }
    }

    function attachMp4Events() {
        const mp4 = elements().mp4;
        if (!mp4 || mp4.dataset.watchPartyEventsAttached === 'true') {
            return;
        }
        mp4.dataset.watchPartyEventsAttached = 'true';
        mp4.addEventListener('play', () => emit('play', {currentTime: currentTime(), type: currentType}));
        mp4.addEventListener('pause', () => emit('pause', {currentTime: currentTime(), type: currentType}));
        mp4.addEventListener('seeked', () => emit('seeked', {currentTime: currentTime(), type: currentType}));
        mp4.addEventListener('ended', () => emit('ended', {currentTime: currentTime(), type: currentType}));
    }

    function startSeekMonitor() {
        if (monitorTimer) {
            return;
        }
        monitorTimer = window.setInterval(() => {
            if (!currentType) {
                resetSeekMonitor();
                return;
            }

            const now = Date.now();
            const time = currentTime();
            const playing = isPlaying();

            if (!lastSample) {
                lastSample = {time, at: now, playing};
                return;
            }

            const elapsed = Math.max(0, (now - lastSample.at) / 1000);
            const expected = lastSample.time + (lastSample.playing ? elapsed : 0);
            const jump = Math.abs(time - expected);

            // التعليق بالعربية: YouTube لا يعطي seeked event موثوقًا، لذلك نرصد القفزات الكبيرة في الزمن.
            if (jump > 1.25) {
                emit('seeked', {currentTime: time, previousTime: lastSample.time, type: currentType});
            }

            lastSample = {time, at: now, playing};
        }, 500);
    }

    function resetSeekMonitor() {
        lastSample = null;
    }

    function hideAll(els) {
        els.empty.classList.add('hidden');
        els.youtube.classList.add('hidden');
        els.mp4.classList.add('hidden');
    }

    function play() {
        const els = elements();
        if (!els.mp4.classList.contains('hidden')) {
            return els.mp4.play().catch(() => undefined);
        }
        if (youtubePlayer && typeof youtubePlayer.playVideo === 'function') {
            youtubePlayer.playVideo();
        }
        return Promise.resolve();
    }

    function pause() {
        const els = elements();
        if (!els.mp4.classList.contains('hidden')) {
            els.mp4.pause();
        }
        if (youtubePlayer && typeof youtubePlayer.pauseVideo === 'function') {
            youtubePlayer.pauseVideo();
        }
    }

    function seek(seconds) {
        const target = Math.max(0, Number(seconds) || 0);
        const els = elements();
        if (!els.mp4.classList.contains('hidden')) {
            els.mp4.currentTime = target;
        }
        if (youtubePlayer && typeof youtubePlayer.seekTo === 'function') {
            youtubePlayer.seekTo(target, true);
        }
        resetSeekMonitor();
    }

    function currentTime() {
        const els = elements();
        if (!els.mp4.classList.contains('hidden')) {
            return els.mp4.currentTime || 0;
        }
        if (youtubePlayer && typeof youtubePlayer.getCurrentTime === 'function') {
            return youtubePlayer.getCurrentTime() || 0;
        }
        return Number(document.getElementById('seekInput')?.value || 0);
    }

    function isPlaying() {
        const els = elements();
        if (!els.mp4.classList.contains('hidden')) {
            return !els.mp4.paused && !els.mp4.ended;
        }
        if (youtubePlayer && typeof youtubePlayer.getPlayerState === 'function' && window.YT?.PlayerState) {
            return youtubePlayer.getPlayerState() === YT.PlayerState.PLAYING;
        }
        return false;
    }

    function extractYouTubeId(url) {
        try {
            const parsed = new URL(url);
            if (parsed.hostname.includes('youtu.be')) {
                return parsed.pathname.replace('/', '').split('/')[0];
            }
            return parsed.searchParams.get('v')
                || parsed.pathname.split('/').filter(Boolean).pop();
        } catch (error) {
            return null;
        }
    }

    window.WatchPartyVideoPlayer = {
        loadVideo,
        play,
        pause,
        seek,
        currentTime,
        isPlaying,
        on,
        off
    };
})();
