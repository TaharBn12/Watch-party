/* global window, document */
(function () {
    'use strict';

    const Ui = window.WatchPartyUi;
    const Auth = window.WatchPartyAuth;
    const Api = window.WatchPartyApi;

    document.addEventListener('DOMContentLoaded', () => {
        boot().catch((error) => Ui.toast(error.message, 'error'));
    });

    async function boot() {
        await Auth.renderAuthActions(document.getElementById('authActions'));
        bindCreateRoomForm();
        bindJoinRoomForm();
        await renderPublicRooms();
    }

    function bindCreateRoomForm() {
        const form = document.getElementById('createRoomForm');
        if (!form) {
            return;
        }

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            Ui.setBusy(form, true, 'جارٍ إنشاء الغرفة...');
            try {
                const session = await Auth.getSession();
                if (!session) {
                    const returnTo = encodeURIComponent('/index.html#create');
                    window.location.href = `/login.html?return=${returnTo}`;
                    return;
                }

                const data = Ui.formToObject(form);
                const room = await Api.createRoom({
                    name: data.name,
                    initialVideoUrl: data.initialVideoUrl || null,
                    publicRoom: data.publicRoom === 'true',
                    password: data.password || null,
                    controlMode: data.controlMode || 'HOST_ONLY'
                });

                Ui.toast('تم إنشاء الغرفة بنجاح', 'success');
                window.location.href = `/room.html?roomId=${encodeURIComponent(room.id)}`;
            } catch (error) {
                Ui.toast(error.message, 'error');
            } finally {
                Ui.setBusy(form, false);
            }
        });
    }

    function bindJoinRoomForm() {
        const form = document.getElementById('joinRoomForm');
        const preview = document.getElementById('invitePreview');
        if (!form) {
            return;
        }

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const data = Ui.formToObject(form);
            const inviteCode = Ui.normalizeInvite(data.invite);
            if (!inviteCode) {
                Ui.toast('أدخل كود دعوة صالحًا', 'warning');
                return;
            }

            Ui.setBusy(form, true, 'جارٍ فتح الغرفة...');
            try {
                const room = await Api.getRoomPreview(inviteCode);
                preview.innerHTML = `
                    <strong>${Ui.escapeHtml(room.name)}</strong>
                    <p>${room.protectedRoom ? 'الغرفة محمية بكلمة سر.' : 'الغرفة لا تحتاج كلمة سر.'}</p>
                `;
                Ui.show(preview);
                window.setTimeout(() => {
                    window.location.href = Api.roomUrlFromInvite(room.inviteCode || inviteCode);
                }, 550);
            } catch (error) {
                Ui.toast(error.message, 'error');
            } finally {
                Ui.setBusy(form, false);
            }
        });
    }

    async function renderPublicRooms() {
        const list = document.getElementById('publicRoomsList');
        if (!list) {
            return;
        }
        list.innerHTML = '<div class="loading">جارٍ تحميل الغرف العامة...</div>';

        try {
            const rooms = await Api.getPublicRooms();
            if (!rooms.length) {
                list.innerHTML = '<div class="empty-state">لا توجد غرف عامة مفتوحة حاليًا. كن أول من يبدأ سهرة!</div>';
                return;
            }

            list.innerHTML = rooms.map((room) => `
                <article class="room-card card">
                    <div>
                        <h3>${Ui.escapeHtml(room.name)}</h3>
                        <p>${room.protectedRoom ? 'محمية بكلمة سر' : 'مفتوحة'} • ${Ui.formatDate(room.createdAt)}</p>
                    </div>
                    <a class="btn btn-secondary" href="${Api.roomUrlFromInvite(room.inviteCode)}">فتح الغرفة</a>
                </article>
            `).join('');
        } catch (error) {
            list.innerHTML = `<div class="empty-state">${Ui.escapeHtml(error.message)}</div>`;
        }
    }
})();
