/* global window, document, RTCPeerConnection, RTCSessionDescription, RTCIceCandidate */
(function () {
    'use strict';

    const MAX_PARTICIPANTS = 6;

    function create(options) {
        const Ui = window.WatchPartyUi;
        const peers = new Map();
        const callParticipants = new Set();
        let localStream = null;
        let inCall = false;
        let audioEnabled = true;
        let videoEnabled = true;
        let iceServersPromise = null;

        async function startCall() {
            if (inCall) {
                return;
            }

            localStream = await acquireLocalMedia();
            inCall = true;
            audioEnabled = localStream.getAudioTracks().some((track) => track.enabled);
            videoEnabled = localStream.getVideoTracks().some((track) => track.enabled);
            callParticipants.add(options.getUserId());
            renderLocalTile();
            updateButtons();

            publish(`/app/rooms/${options.getRoomId()}/webrtc/join`, {
                audioEnabled,
                videoEnabled
            });

            options.toast?.('تم الانضمام للمكالمة', 'success');
        }

        async function leaveCall({notify = true} = {}) {
            if (!inCall && !localStream && peers.size === 0) {
                renderEmptyCallState();
                updateButtons();
                return;
            }

            if (notify && inCall) {
                publish(`/app/rooms/${options.getRoomId()}/webrtc/leave`, {
                    audioEnabled: false,
                    videoEnabled: false
                });
            }

            peers.forEach((_, userId) => closePeer(userId));
            callParticipants.clear();
            stopLocalMedia();
            inCall = false;
            renderEmptyCallState();
            updateButtons();
        }

        function handleEnvelope(envelope) {
            if (!envelope || !envelope.type || !String(envelope.type).startsWith('WEBRTC_')) {
                return false;
            }

            if (envelope.type === 'WEBRTC_JOIN_CALL') {
                handlePeerJoined(envelope.payload, envelope.senderId);
                return true;
            }
            if (envelope.type === 'WEBRTC_LEAVE_CALL') {
                handlePeerLeft(envelope.payload, envelope.senderId);
                return true;
            }
            if (envelope.type === 'WEBRTC_OFFER') {
                handleOffer(envelope.payload).catch(reportError);
                return true;
            }
            if (envelope.type === 'WEBRTC_ANSWER') {
                handleAnswer(envelope.payload).catch(reportError);
                return true;
            }
            if (envelope.type === 'WEBRTC_ICE_CANDIDATE') {
                handleIceCandidate(envelope.payload).catch(reportError);
                return true;
            }
            return false;
        }

        function handlePeerJoined(payload, senderId) {
            const userId = payload?.userId || senderId;
            if (!userId || userId === options.getUserId()) {
                return;
            }
            callParticipants.add(userId);
            if (!inCall) {
                return;
            }
            if (currentParticipantCount() >= MAX_PARTICIPANTS && !peers.has(userId)) {
                options.toast?.('وصلت المكالمة إلى الحد الأقصى 6 أشخاص', 'warning');
                return;
            }

            // التعليق بالعربية: الموجودون في المكالمة يرسلون offer للعضو الجديد، والعضو الجديد ينتظر العروض.
            createOffer(userId).catch(reportError);
        }

        function handlePeerLeft(payload, senderId) {
            const userId = payload?.userId || senderId;
            if (!userId || userId === options.getUserId()) {
                return;
            }
            callParticipants.delete(userId);
            closePeer(userId);
            options.toast?.(`غادر ${shortId(userId)} المكالمة`, 'warning');
        }

        async function handleOffer(payload) {
            if (!payload || payload.targetUserId !== options.getUserId()) {
                return;
            }
            if (!inCall || !localStream) {
                // لا نطلب الكاميرا تلقائيًا بدون فعل المستخدم؛ لذلك نتجاهل العرض إن لم ينضم للمكالمة.
                return;
            }

            const fromUserId = payload.fromUserId;
            if (!fromUserId || fromUserId === options.getUserId()) {
                return;
            }
            if (currentParticipantCount() >= MAX_PARTICIPANTS && !peers.has(fromUserId)) {
                return;
            }

            const peer = await ensurePeer(fromUserId);
            await peer.pc.setRemoteDescription(new RTCSessionDescription({
                type: payload.sdpType || 'offer',
                sdp: payload.sdp
            }));
            const answer = await peer.pc.createAnswer();
            await peer.pc.setLocalDescription(answer);
            publish(`/app/rooms/${options.getRoomId()}/webrtc/answer`, {
                targetUserId: fromUserId,
                sdpType: peer.pc.localDescription.type,
                sdp: peer.pc.localDescription.sdp
            });
        }

        async function handleAnswer(payload) {
            if (!payload || payload.targetUserId !== options.getUserId()) {
                return;
            }
            const fromUserId = payload.fromUserId;
            const peer = peers.get(fromUserId);
            if (!peer || !payload.sdp) {
                return;
            }
            await peer.pc.setRemoteDescription(new RTCSessionDescription({
                type: payload.sdpType || 'answer',
                sdp: payload.sdp
            }));
        }

        async function handleIceCandidate(payload) {
            if (!payload || payload.targetUserId !== options.getUserId()) {
                return;
            }
            const fromUserId = payload.fromUserId;
            const peer = peers.get(fromUserId);
            if (!peer || !payload.candidate) {
                return;
            }

            await peer.pc.addIceCandidate(new RTCIceCandidate(payload.candidate));
        }

        async function createOffer(targetUserId) {
            const peer = await ensurePeer(targetUserId);
            const offer = await peer.pc.createOffer({offerToReceiveAudio: true, offerToReceiveVideo: true});
            await peer.pc.setLocalDescription(offer);
            publish(`/app/rooms/${options.getRoomId()}/webrtc/offer`, {
                targetUserId,
                sdpType: peer.pc.localDescription.type,
                sdp: peer.pc.localDescription.sdp
            });
        }

        async function ensurePeer(userId) {
            if (peers.has(userId)) {
                return peers.get(userId);
            }
            if (!localStream) {
                throw new Error('لا يوجد بث محلي للمكالمة');
            }

            const peerConnection = new RTCPeerConnection({
                iceServers: await iceServers()
            });

            localStream.getTracks().forEach((track) => {
                peerConnection.addTrack(track, localStream);
            });

            const remoteStream = new MediaStream();
            const peer = {pc: peerConnection, remoteStream};
            peers.set(userId, peer);

            peerConnection.ontrack = (event) => {
                event.streams[0]?.getTracks().forEach((track) => remoteStream.addTrack(track));
                renderRemoteTile(userId, remoteStream);
            };

            peerConnection.onicecandidate = (event) => {
                if (!event.candidate) {
                    return;
                }
                publish(`/app/rooms/${options.getRoomId()}/webrtc/ice-candidate`, {
                    targetUserId: userId,
                    candidate: event.candidate.toJSON()
                });
            };

            peerConnection.onconnectionstatechange = () => {
                const state = peerConnection.connectionState;
                updatePeerState(userId, state);
                if (state === 'failed' || state === 'disconnected' || state === 'closed') {
                    if (state === 'failed') {
                        peerConnection.restartIce?.();
                    }
                    if (state === 'closed') {
                        closePeer(userId);
                    }
                }
            };

            renderRemoteTile(userId, remoteStream);
            return peer;
        }

        async function iceServers() {
            if (iceServersPromise) {
                return iceServersPromise;
            }

            iceServersPromise = (async () => {
                const config = await window.WatchPartyConfig.load();
                const webRtc = config.webRtc || {};
                const servers = [];

                const stunUrls = Array.isArray(webRtc.stunUrls) && webRtc.stunUrls.length
                    ? webRtc.stunUrls
                    : ['stun:stun.l.google.com:19302'];
                servers.push({urls: stunUrls});

                if (webRtc.turnConfigured && Array.isArray(webRtc.turnUrls) && webRtc.turnUrls.length) {
                    servers.push({
                        urls: webRtc.turnUrls,
                        username: webRtc.turnUsername,
                        credential: webRtc.turnCredential
                    });
                }

                return servers;
            })();

            return iceServersPromise;
        }

        async function acquireLocalMedia() {
            if (!navigator.mediaDevices?.getUserMedia) {
                throw new Error('المتصفح لا يدعم getUserMedia للمكالمة');
            }

            try {
                return await navigator.mediaDevices.getUserMedia({
                    audio: true,
                    video: {
                        width: {ideal: 640},
                        height: {ideal: 360},
                        facingMode: 'user'
                    }
                });
            } catch (videoError) {
                options.toast?.('تعذر فتح الكاميرا، سنحاول الصوت فقط.', 'warning');
                return navigator.mediaDevices.getUserMedia({audio: true, video: false});
            }
        }

        function toggleAudio() {
            if (!localStream) {
                return false;
            }
            const tracks = localStream.getAudioTracks();
            if (!tracks.length) {
                options.toast?.('لا يوجد مسار صوت في هذه المكالمة', 'warning');
                return null;
            }
            audioEnabled = !audioEnabled;
            tracks.forEach((track) => {
                track.enabled = audioEnabled;
            });
            updateButtons();
            return audioEnabled;
        }

        function toggleVideo() {
            if (!localStream) {
                return false;
            }
            const tracks = localStream.getVideoTracks();
            if (!tracks.length) {
                options.toast?.('لا توجد كاميرا مفعّلة في هذه المكالمة', 'warning');
                return null;
            }
            videoEnabled = !videoEnabled;
            tracks.forEach((track) => {
                track.enabled = videoEnabled;
            });
            updateButtons();
            return videoEnabled;
        }

        function renderLocalTile() {
            const grid = callGrid();
            if (!grid) {
                return;
            }
            removeEmptyTile();
            let tile = document.getElementById('call-tile-local');
            if (!tile) {
                tile = document.createElement('div');
                tile.className = 'call-tile video-tile local-tile';
                tile.id = 'call-tile-local';
                tile.innerHTML = `
                    <video autoplay playsinline muted></video>
                    <span class="call-name">أنت</span>
                    <span class="call-state">محلي</span>
                `;
                grid.prepend(tile);
            }
            tile.querySelector('video').srcObject = localStream;
        }

        function renderRemoteTile(userId, stream) {
            const grid = callGrid();
            if (!grid) {
                return;
            }
            removeEmptyTile();
            let tile = document.getElementById(tileId(userId));
            if (!tile) {
                tile = document.createElement('div');
                tile.className = 'call-tile video-tile remote-tile';
                tile.id = tileId(userId);
                tile.innerHTML = `
                    <video autoplay playsinline></video>
                    <span class="call-name"></span>
                    <span class="call-state">جارٍ الاتصال...</span>
                `;
                grid.appendChild(tile);
            }
            tile.querySelector('.call-name').textContent = shortId(userId);
            tile.querySelector('video').srcObject = stream;
        }

        function updatePeerState(userId, state) {
            const tile = document.getElementById(tileId(userId));
            const stateLabel = tile?.querySelector('.call-state');
            if (stateLabel) {
                stateLabel.textContent = translatePeerState(state);
            }
        }

        function renderEmptyCallState() {
            const grid = callGrid();
            if (!grid) {
                return;
            }
            grid.innerHTML = `
                <div class="call-tile muted-tile" id="callEmptyState">
                    <span>🎙️</span>
                    <p>اضغط “بدء المكالمة” للتحدث مع الموجودين في الغرفة.</p>
                </div>
            `;
        }

        function closePeer(userId) {
            const peer = peers.get(userId);
            if (peer) {
                peer.pc.ontrack = null;
                peer.pc.onicecandidate = null;
                peer.pc.onconnectionstatechange = null;
                peer.pc.close();
            }
            peers.delete(userId);
            document.getElementById(tileId(userId))?.remove();
        }

        function stopLocalMedia() {
            if (localStream) {
                localStream.getTracks().forEach((track) => track.stop());
            }
            localStream = null;
            document.getElementById('call-tile-local')?.remove();
        }

        function updateButtons() {
            const start = document.getElementById('startCallButton');
            const leave = document.getElementById('leaveCallButton');
            const mic = document.getElementById('micButton');
            const camera = document.getElementById('cameraButton');

            if (start) {
                start.disabled = inCall;
            }
            if (leave) {
                leave.disabled = !inCall;
            }
            if (mic) {
                mic.disabled = !inCall;
                mic.textContent = audioEnabled ? 'كتم الميكروفون' : 'تشغيل الميكروفون';
            }
            if (camera) {
                camera.disabled = !inCall;
                camera.textContent = videoEnabled ? 'إيقاف الكاميرا' : 'تشغيل الكاميرا';
            }
        }

        function currentParticipantCount() {
            return 1 + peers.size;
        }

        function publish(destination, body) {
            return options.publish(destination, body || {});
        }

        function callGrid() {
            return document.getElementById('callGrid');
        }

        function removeEmptyTile() {
            document.getElementById('callEmptyState')?.remove();
        }

        function tileId(userId) {
            return `call-tile-${String(userId).replaceAll(':', '-')}`;
        }

        function shortId(userId) {
            return `مستخدم ${String(userId).slice(0, 8)}`;
        }

        function translatePeerState(state) {
            return {
                'new': 'جديد',
                'connecting': 'جارٍ الاتصال',
                'connected': 'متصل',
                'disconnected': 'انقطع',
                'failed': 'فشل',
                'closed': 'مغلق'
            }[state] || state;
        }

        function reportError(error) {
            console.error(error);
            options.toast?.(error.message || 'خطأ في المكالمة', 'error');
        }

        renderEmptyCallState();
        updateButtons();

        return {
            startCall,
            leaveCall,
            handleEnvelope,
            toggleAudio,
            toggleVideo,
            isInCall: () => inCall
        };
    }

    window.WatchPartyWebRTC = {create};
})();
