<template>
<a-layout :style="chatLayoutStyle">
  <a-layout-sider :style="siderStyle" width="300" theme="light">
      <div style="padding:10px;font-weight:600;font-size:18px">Kişiler</div>
      <a-menu :selectedKeys="selectedUser ? [selectedUser.id] : []" @click="handleSelectUser" style="padding-left:0">
        <a-menu-item v-for="u in users" :key="u.id" :style="{ paddingLeft: '0px', height: '72px', position: 'relative' }">
          <a-avatar :src="u.avatar" :size="40" style="float:left;margin-right:12px" />
          <div style="overflow:hidden">
            <div style="font-weight:500">{{ u.name }}</div>
            <div style="color:gray;font-size:12px">{{ u.lastMessage }}</div>
          </div>
          <!-- basit mavi rozet -->
          <span v-if="u.unread > 0"
            style="position:absolute; right:12px; top:8px; background:#1677ff; color:#fff; border-radius:999px;
                       min-width:20px; height:20px; line-height:20px; text-align:center; font-size:12px; padding:0 6px; z-index:1;">
            {{ u.unread }}
          </span>
        </a-menu-item>
      </a-menu>
      <div style="padding:10px;font-weight:600;font-size:18px">Gruplar
        <a-button style="border: transparent; padding-left: 150px;" type="primary" ghost @click="openCreateGroup">+</a-button>
      </div> 
      <a-menu
        :selectedKeys="selectedGroup ? [String(selectedGroup.id)] : []"
        @click="handleSelectGroup"
        style="padding-left:0"
      >
        <a-menu-item
          v-for="g in groups"
          :key="String(g.id)"
          :style="{ position:'relative' }"
          :class="{ 'is-unread': (g.unread || 0) > 0 }"
          
        >
          <!--CLASS YAZAN KISIM PAGINATION ÖNCESİ KALDIRILACAK PAGINATION OLUNCA -->

          <div class="group-item">
            <div class="title">{{ g.name }}</div>
            <div class="sub">{{ g.lastMessage || ' ' }}</div>
          </div>
 
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-modal
      v-model:open="createOpen"
      title="Create Group"
      :confirmLoading="creating"
      @ok="handleCreateGroup"
      @cancel="resetCreate"
      :okButtonProps="{ disabled: !groupName.trim() }"
    >
      <!-- Grup Adı (zorunlu) -->
      <div style="margin-bottom:12px;">
        <div style="font-weight:500;margin-bottom:6px;">Group Name <span style="color:#f5222d">*</span></div>
        <a-input v-model:value="groupName" placeholder="e.g., Developers Team" allow-clear />
      </div>

      <!-- Arama -->
      <div style="margin: 12px 0;">
        <a-input-search
          v-model:value="search"
          placeholder="Search users by name/username/email"
          allow-clear
          @search="noop"
        />
      </div>

      <!-- Kullanıcı Seçimi (opsiyonel) -->
      <a-table
        :data-source="filteredUsers"
        :columns="selectColumns"
        :rowSelection="rowSelection"
        :pagination="{ pageSize: 5 }"
        row-key="id"
        size="small"
      />
      <div style="margin-top:8px; font-size:12px; color:#888;">
        {{ selectedIds.length }} user selected (optional)
      </div>
    </a-modal>
    <a-layout-content style="background:#f5f5f5">
      <div v-if="selectedUser" style="display: flex;flex-direction:column;height:100%">
        <div style="padding:16px;background:#fff;font-weight:600;border-bottom:1px solid #ddd">
        {{ selectedUser.name }}
          <template v-if="selectedUser?.id?.startsWith?.('group:')">
          <a-dropdown trigger="click">
            <a-button shape="circle">
              <template #icon><EllipsisOutlined /></template>
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="openMembersView">Kişileri Görüntüle</a-menu-item>
                <a-menu-item @click="openPerms">Roller &amp; İzinler</a-menu-item>
                <a-menu-divider />
                <a-menu-item v-if="groupPerms.canUpdate" @click="openEditGroup">Grubu Düzenle</a-menu-item>
                <a-menu-item v-if="groupPerms.canDelete" danger @click="confirmDeleteGroup">Grubu Sil</a-menu-item>
              </a-menu>
            </template>
              </a-dropdown>
            </template>
       </div>

        <div ref="scrollPane" style="flex:1;padding:16px;overflow-y:auto">
        <!-- Üst-ortada kayan gün etiketi -->
        <div v-show="floatingDay"
            class="floating-day">
          {{ floatingDay }}
        </div>
          <div v-if="selectedUser && (hasMoreByPeer.get(selectedUser.id) || false)" style="text-align:center;margin-bottom:8px">
          <a-button size="small" @click="loadOlderMessages(selectedUser.id)" :loading="loadingOlder">
            Önceki mesajları yükle
          </a-button>
        </div>
         <div v-for="(m, i) in selectedUser.messages" :key="m.id || i"
              class="msg-row"
              :data-ts="m.createdAt"
              style="margin-bottom:8px;display:flex"
              :style="{ justifyContent: isMine(m) ? 'flex-end' : 'flex-start' }">
           <div style="background:#fff;padding:8px 12px;border-radius:8px;max-width:70%">
             <div v-if="isGroupId(selectedUser?.id)"
                  style="font-weight:600;font-size:12px;margin-bottom:4px"
                  :style="{ color: nameColor(m.fromUserId) }">
               {{ userNameById(m.fromUserId) }}
             </div>
             <div style="white-space:pre-wrap; word-break:break-word;">
               {{ m.content }}
             </div>
             <div style="color:#999;font-size:11px;margin-top:4px">{{ fmtTime(m.createdAt) }}</div>
           </div>
         </div>
    </div>

        <div style="position:sticky;bottom:0;background:#fff;padding:12px 24px;border-top:1px solid #ddd;z-index:10">
          <a-input-search v-model:value="newMessage" placeholder="Mesaj yaz..." enter-button="Gönder"
            @search="sendMessage" />
        </div>
      </div>

      <div v-else style="display:flex;justify-content:center;align-items:center;height:100%">
        <p>Lütfen bir kişi seçin.</p>
      </div>
              
<a-modal
  v-model:open="membersViewOpen"
  :title="`Grup: ${selectedGroup?.name || ''}`"
  :footer="null"
  destroyOnClose
  :bodyStyle="{ maxHeight: '60vh', overflowY: 'auto' }"
>
  <a-list
    v-if="selectedGroup?.memberIds?.length"
    :data-source="selectedGroup.memberIds"
    item-layout="horizontal"
    bordered
  >
    <template #renderItem="{ item }">
      <a-list-item>
        <a-list-item-meta
          :title="userNameById(item)"
        />
      </a-list-item>
    </template>
  </a-list>

  <div v-else>-</div>
</a-modal>

        <a-modal v-model:open="editOpen"
         title="Grubu Düzenle"
         :confirmLoading="updating"
         @ok="handleEditSave"
         @cancel="resetEdit"
         :okButtonProps="{ disabled: !editName.trim() }"
         destroyOnClose>
          <!-- Grup Adı -->
          <div style="margin-bottom:12px;">
            <div style="font-weight:500;margin-bottom:6px;">Group Name <span style="color:#f5222d">*</span></div>
            <a-input v-model:value="editName" placeholder="Group name" allow-clear />
          </div>

          <!-- Arama -->
          <div style="margin:12px 0;">
            <a-input-search v-model:value="editSearch" placeholder="Search users" allow-clear />
          </div>

          <!-- Üye seçimi -->
          <a-table
            :data-source="editFilteredUsers"
            :columns="[{title:'Name',dataIndex:'name'},{title:'Username',dataIndex:'username'},{title:'Email',dataIndex:'email'}]"
            :rowSelection="editRowSelection"
            :pagination="{ pageSize: 6 }"
            row-key="id"
            size="small"
          />
          <div style="margin-top:8px;font-size:12px;color:#888;">
            {{ editSelectedIds.length }} kişi
          </div>
        </a-modal>
<a-modal v-model:open="permsOpen" title="Roller & İzinler" :confirmLoading="permsSaving"
         @ok="savePerms" ok-text="Kaydet" cancel-text="Kapat" width="800px"  :okButtonProps="{ disabled: !groupPerms.canGrant }" >
  <div style="margin-bottom:12px">
    <a-input-search v-model:value="permSearch" placeholder="Kullanıcı ara" allow-clear />
  </div>

  <a-table
    :data-source="permFilteredRows"
    :columns="permColumns"
    :pagination="{ pageSize: 6 }"
    row-key="userId"
    size="small"
  >
    <template style="width: fit-content;" #bodyCell="{ column, record }">
      <template v-if="column.key === 'name'">
        {{ userNameById(record.userId) }}
      </template>

      <template  v-else-if="column.key === 'canUpdate'">
        <a-switch v-model:checked="record.canUpdate" :disabled="isRowLocked(record.userId)" />
      </template>

      <template v-else-if="column.key === 'canDelete'">
        <a-switch v-model:checked="record.canDelete" :disabled="isRowLocked(record.userId)" />
      </template>

      <template v-else-if="column.key === 'actions'">
        <a-button size="small" danger @click="revokePerm(record)" :disabled="isRowLocked(record.userId)">
          Yetkiyi Kaldır
        </a-button>
      </template>
    </template>
  </a-table>
</a-modal>

    </a-layout-content>
  </a-layout>
</template> 
<script setup>

const HEADER_H = 54;
const chatLayoutStyle = {
  
  height: `100%`,
  overflow: 'hidden',   
  background: '#fff'
};

const siderStyle = {
  height: '100%',
  overflowY: 'auto',     
  borderRight: '1px solid #eee'
};


import { ref, onMounted, onBeforeUnmount, nextTick,computed } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'  
import {Modal, message } from 'ant-design-vue'
import api from '@/api' 
import { EllipsisOutlined, TeamOutlined } from '@ant-design/icons-vue'
 
const windowFocused = ref(typeof document !== 'undefined' ? document.hasFocus() : true)
const pageVisible   = ref(typeof document !== 'undefined' ? document.visibilityState === 'visible' : true)
function isPeerOpen(peerId) {
  // sohbet gerçekten “görülür” kabul edilsin: seçili + odak + görünür
  return !!selectedUser.value
    && selectedUser.value.id === peerId
    && windowFocused.value
    && pageVisible.value
}
const API_BASE = 'http://localhost:8080/api'
const WS_URL = 'http://localhost:8080/ws'
const MSG_LIMIT = 15
const users = ref([]) 
const meId = ref(null)
const selectedUser = ref(null)
const newMessage = ref('')
const stomp = ref(null)
const scrollPane = ref(null)
const groups = ref([])             // [{id,name,memberIds}]
const selectedGroup = ref(null)    // seçili grup
const groupPerms   = ref({ canUpdate:false, canDelete:false })
const allUsersMap  = ref(new Map())       // id->user (isim çözümleme için)
const membersViewOpen = ref(false)
const permsOpen       = ref(false)        // “Roller & İzinler” placeholder
const editOpen        = ref(false)        // “Grubu Düzenle” modalı
const editName        = ref('')
const editSelectedIds = ref([])
const editSearch      = ref('')
const isAdmin = ref(false)
const groupCache = ref(new Map()) // key: "group:ID" -> Array<msg>
// peerId -> boolean (history yüklendi mi?)
const historyReadyByPeer = ref(new Map()); 
const loadingOlder = ref(false)
const hasMoreByPeer = ref(new Map()) // zaten var sende
const nextCursorByPeer = ref(new Map()) // EKLE
const LAST_SEEN_KEY = 'chat_last_seen_ms'
// --- Roller & İzinler state ---
const permsSaving   = ref(false)
const permSearch    = ref('')
const permRows      = ref([]) // [{ userId, canUpdate, canDelete }]
const permColumns   = [
  { title: 'Kullanıcı', key: 'name',       dataIndex: 'name' },
  { title: 'Güncelleme',  key: 'canUpdate',  dataIndex: 'canUpdate' },
  { title: 'Silme',       key: 'canDelete',  dataIndex: 'canDelete' },
  { title: 'İşlem',     key: 'actions',    dataIndex: 'actions' }
]
const permFilteredRows = computed(() => {
  const q = permSearch.value.trim().toLowerCase()
  if (!q) return permRows.value
  return permRows.value.filter(r => userNameById(r.userId).toLowerCase().includes(q))
})
function isAdminUser(userId) {
  const u = allUsersMap.value.get(String(userId))
  const role = String(u?.role || u?.authority || '').toUpperCase()
  return role.includes('ADMIN')
}

function isRowLocked(userId) {
  // Admin satırı her zaman kilitli
  if (isAdminUser(userId)) return true
  // Onun dışındakilerde de “grant edebilir miyim?” kuralı
  return !groupPerms.value.canGrant
}


async function openPerms() {
  if (!selectedGroup.value) return
  try {
    await ensureAllUsersLoaded()

    // Üye listesi yoksa grubu getir
    if (!selectedGroup.value.memberIds) {
      const { data } = await api.get(`/group/${selectedGroup.value.id}`)
      selectedGroup.value = data
    }

    // Tüm grant'leri listele
    const { data } = await api.get(`/group/${selectedGroup.value.id}/grants`)
    const map = new Map(data.map(r => [String(r.userId), r]))

    // ---- KENDİNİ GÖSTERME: meId'yı dışla ----
    const myId = Number(meId.value)

    // Temel: üyeleri al
    let ids = (selectedGroup.value.memberIds || []).map(Number)

    // (Opsiyonel) Üye olmayan ama grant verilmiş kullanıcılar da tabloda görünsün istersen aç:
    // ids = Array.from(new Set([...ids, ...data.map(g => Number(g.userId))]))

    // Kendini listeden çıkar
    ids = ids.filter(uid => uid !== myId)

    // tablo satırlarını hazırla (üyeler + varsa grant)
    permRows.value = ids.map(uid => {
      const g = map.get(String(uid))
      return { userId: uid, canUpdate: !!g?.canUpdate, canDelete: !!g?.canDelete }
    })

    permsOpen.value = true
  } catch (e) {
    console.error(e)
    const myId = Number(meId.value)
    const ids = (selectedGroup.value?.memberIds || [])
      .map(Number)
      .filter(uid => uid !== myId) // hata durumunda da kendini çıkar

    permRows.value = ids.map(uid => ({
      userId: uid, canUpdate:false, canDelete:false
    }))
    permsOpen.value = true
  }
}


async function savePerms() {
  if (!selectedGroup.value) return
    if (!groupPerms.value.canGrant) return message.warning('Yetkin yok')
  permsSaving.value = true
  try {
    for (const r of permRows.value) {
        if (isAdminUser(r.userId)) continue   // 🔒 admin kilitli
      if (!r.canUpdate && !r.canDelete) {
        // hiçbir yetki yoksa revoke
        await api.delete(`/group/${selectedGroup.value.id}/grants/${r.userId}`)
      } else {
        // grant/update
        await api.put(`/group/${selectedGroup.value.id}/grants`, {
          userId: r.userId,
          canUpdate: r.canUpdate,
          canDelete: r.canDelete
        })
      }
    }
    message.success('İzinler güncellendi')
    permsOpen.value = false
    await fetchGroupPerms(selectedGroup.value.id) // başlıktaki menü görünürlüğü tazelensin
  } catch (e) {
    console.error(e)
    message.error(e?.response?.data?.message || 'İzinler güncellenemedi')
  } finally {
    permsSaving.value = false
  }
}


async function revokePerm(row) {
  try {
    await api.delete(`/group/${selectedGroup.value.id}/grants/${row.userId}`)
    row.canUpdate = false
    row.canDelete = false
    message.success('Yetkiler kaldırıldı')
  } catch (e) {
    console.error(e)
    message.error(e?.response?.data?.message || 'Kaldırılamadı')
  }
}


function getLastSeen() { return Number(localStorage.getItem(LAST_SEEN_KEY) || 0) }
function setLastSeen(ms = Date.now()) { localStorage.setItem(LAST_SEEN_KEY, String(ms)) }
const updating = ref(false)

const editAll = computed(() => {
  // allUsersMap -> diziye dönüştür
  return Array.from(allUsersMap.value.values()).map(u => ({
    id: String(u.id),
    name: buildName(u),
    username: u.username,
    email: u.email
  }))
})

const editFilteredUsers = computed(() => {
  const q = editSearch.value.trim().toLowerCase()
  if (!q) return editAll.value
  return editAll.value.filter(u =>
    (u.name && u.name.toLowerCase().includes(q)) ||
    (u.username && String(u.username).toLowerCase().includes(q)) ||
    (u.email && String(u.email).toLowerCase().includes(q))
  )
})

const editRowSelection = computed(() => ({
  selectedRowKeys: editSelectedIds.value,
  onChange: (keys) => { editSelectedIds.value = keys }
}))

async function openEditGroup() {
  try {
    // en güncel grup bilgisi
    const { data } = await api.get(`/group/${selectedGroup.value.id}`)
    selectedGroup.value = data
    await ensureAllUsersLoaded()

    editName.value = data.name || ''
    editSelectedIds.value = (data.memberIds || []).map(id => String(id))
    editOpen.value = true
  } catch (e) {
    console.error(e)
    message.error('Grup bilgisi alınamadı')
  }
}

function resetEdit() {
  editOpen.value = false
  editName.value = ''
  editSearch.value = ''
  editSelectedIds.value = []
}

async function handleEditSave() {
  const name = editName.value.trim()
  if (!name) return
  updating.value = true
  try {
    const userIds = Array.from(new Set(editSelectedIds.value)).map(Number)
    const { data } = await api.put(`/group/${selectedGroup.value.id}`, { name, userIds })

    // soldaki listeyi yenile
    await loadMyGroups()

    // seçili grubu listeden güncel haline çekelim
    const updated = groups.value.find(g => String(g.id) === String(selectedGroup.value.id))
    if (updated) {
      selectedGroup.value = updated
      // sağ panel başlığı
      if (selectedUser.value?.id === `group:${updated.id}`) {
        selectedUser.value.name = updated.name
      }
    }

    message.success('Group updated')
    resetEdit()
  } catch (e) {
    console.error(e)
    message.error(e?.response?.data?.message || 'Update failed')
  } finally {
    updating.value = false
  }
}

function confirmDeleteGroup() {
  Modal.confirm({
    title: 'Grubu sil?',
    content: `"${selectedGroup.value?.name}" kalıcı olarak silinecek.`,
    okText: 'Sil',
    okType: 'danger',
    cancelText: 'Vazgeç',
    async onOk() {
      try {
        await api.delete(`/group/${selectedGroup.value.id}`)
        message.success('Group deleted')
        // paneli kapat
        selectedGroup.value = null
        selectedUser.value = null
        await loadMyGroups()
      } catch (e) {
        console.error(e)
        message.error(e?.response?.data?.message || 'Delete failed')
      }
    }
  })
}

// peerId -> Set(messageId)
const seenIdsByPeer = ref(new Map())

// localStorage'da okunma zamanı
const READ_KEY = 'chat_last_read_at' // JSON: { [peerId]: epochMs }
function getReadMap() {
  try { return JSON.parse(localStorage.getItem(READ_KEY) || '{}') } catch { return {} }
}
function saveReadMap(map) {
  localStorage.setItem(READ_KEY, JSON.stringify(map))
}
const readMapRef = ref(getReadMap())

function lastMessageTs(peer) {
  return peer.messages.length ? peer.messages[peer.messages.length - 1].createdAt : 0
}

function computeUnread(peer) {
  const lastRead = Number(readMapRef.value[peer.id] ?? 0)
  const me = String(meId.value)

  if (isGroupId(peer.id)) { 
    return peer.messages.filter(m =>
      m.groupId && String(m.fromUserId) !== me && Number(m.createdAt) > lastRead
    ).length
  }

  // DM’lerde eskisi gibi
  return peer.messages.filter(m =>
    String(m.toUserId) === me && Number(m.createdAt) > lastRead
  ).length
}


function setLastRead(peerId, ts = Date.now()) {
  const prev = Number(readMapRef.value[peerId] || 0)
  // eşit zamanlı mesajlar kaçmasın diye -1ms
  const safe = Math.max(prev, (ts))
  readMapRef.value[peerId] = safe
  saveReadMap(readMapRef.value)
  const peer = users.value.find(u => u.id === peerId)
  if (peer) peer.unread = computeUnread(peer)
}

const buildName = (u) => {
  const full = [u.firstName, u.lastName].filter(Boolean).join(' ')
  return full || u.username || u.email || `Kullanıcı #${u.id}`
}
const makeAvatar = (u) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(buildName(u))}`

const authHeaders = () => {
  const t = localStorage.getItem('jwtToken')
  return t ? { Authorization: `Bearer ${t}` } : {}
}
let onBye
onMounted(async () => {
  try {
    const [meRes, listRes] = await Promise.all([
      fetch(`${API_BASE}/user`, { headers: authHeaders() }),
      fetch(`${API_BASE}/users`, { headers: authHeaders() })
    ])
    const me = meRes.ok ? await meRes.json() : null
    if (me?.id != null) {
      meId.value = String(me.id)
      localStorage.setItem('id', meId.value)
    }
    isAdmin.value = String(me?.role || '').toUpperCase() === 'ADMIN'

    const list = listRes.ok ? await listRes.json() : [] 
    const filtered = list.filter(u => !me || String(u.id) !== String(me.id))
    users.value = filtered.map(u => ({
      id: String(u.id),
      name: buildName(u),
      avatar: makeAvatar(u),
      lastMessage: '',
      messages: [],
      unread: 0,
      lastTs: 0
    }))

    await loadAllHistories()

    await connectWs()
    onBye = () => setLastSeen(Date.now())
    window.addEventListener('beforeunload', onBye)
    const onFocus = () => (windowFocused.value = true)
    const onBlur = () => (windowFocused.value = false)
    const onVis = () => (pageVisible.value = document.visibilityState === 'visible')
    window.addEventListener('focus', onFocus)
    window.addEventListener('blur', onBlur)
    document.addEventListener('visibilitychange', onVis)
    cleanup.push(() => {
    window.removeEventListener('focus', onFocus)
    window.removeEventListener('blur', onBlur)
    document.removeEventListener('visibilitychange', onVis)
    })
  } catch (e) {
    console.error('init error', e)
  }
  await loadMyGroups()
  await prefetchGroupHeads()
  const pane = scrollPane.value
  if (pane) pane.addEventListener('scroll', onPaneScroll, { passive:true })
  rebuildDayOnNextTick()
})

const cleanup = []
onBeforeUnmount(() => {
  if (onBye) window.removeEventListener('beforeunload', onBye)
  if (stomp.value?.active) stomp.value.deactivate()
  cleanup.forEach(fn => { try { fn() } catch {} })
  const pane = scrollPane.value
  if (pane) pane.removeEventListener('scroll', onPaneScroll)
})
async function prefetchGroupHeads() {
  const me = String(meId.value)
  await Promise.all((groups.value || []).map(async (g) => {
    const peerId = `group:${g.id}`
    const res = await fetch(`${API_BASE}/messages?peerId=${encodeURIComponent(peerId)}&limit=${MSG_LIMIT}`, {
      headers: authHeaders()
    })
    if (!res.ok) return
    const json = await res.json()
    const { items ,hasMore,nextCursor} = unwrapHistory(json)

    // FE’deki loadHistory ile aynı ID kurgusu
    const msgs = items.map(x => {
      const ts  = toMillis(x.createdAt)
 const gid = normalizeGroupId(x.groupId)
 const id  = String(x.id ?? makeMid({
   fromId: String(x.fromUserId),
   toId:   String(x.toUserId ?? ''),
   ts,
   content: x.content,
   gid
 }))
      return {
        id,
        fromUserId: String(x.fromUserId),
        toUserId:   String(x.toUserId ?? ''),
        content:    String(x.content ?? ''),
        createdAt:  ts,
        groupId:    gid
      }
    }).sort((a,b) => a.createdAt - b.createdAt)

groupCache.value.set(peerId, msgs)
    seenIdsByPeer.value.set(peerId, new Set(msgs.map(m => m.id)))
    historyReadyByPeer.value.set(peerId, true)
    // menüde son mesaj
    
// Doğrudan backend'den gelen `hasMore` değerini kullan
hasMoreByPeer.value.set(String(peerId), hasMore)

// Veya daha güvenli bir yöntem olarak `nextCursor`'ı kontrol edin
hasMoreByPeer.value.set(String(peerId), nextCursor != null)   
 g.lastMessage = msgs.at(-1)?.content || ''

    // menüde unread,
    nextCursorByPeer.value.set(String(peerId), nextCursor ?? null)
    const lastRead = Number(readMapRef.value[peerId] ?? 0) 
    g.unread = msgs.filter(m => m.groupId && String(m.fromUserId) !== me && m.createdAt > lastRead).length 
  }))
}

async function loadMyGroups() {
  try {
    const res = await fetch(`${API_BASE}/my-groups`, { headers: authHeaders() })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    groups.value = await res.json()
  } catch (e) {
    console.error('groups load error', e)
  }
} 
const handleSelectGroup = async ({ key }) => {
  const g = groups.value.find(x => String(x.id) === String(key))
  if (!g) return
  selectedGroup.value = g
  await ensureAllUsersLoaded()
  await fetchGroupPerms(g.id)

  const peerId = `group:${g.id}`
  const cached = groupCache.value.get(peerId)

  selectedUser.value = {
    id: peerId,
    name: g.name,
    avatar: null,
    messages: cached ? cached : [],
    unread: 0,
    lastTs: 0
  }

 if (cached) {
    const uniq = Array.from(new Map(cached.map(m => [m.id, m])).values())
   if (uniq.length !== cached.length) {
     groupCache.value.set(peerId, uniq)
     selectedUser.value.messages = uniq
   }
   seenIdsByPeer.value.set(peerId, new Set(selectedUser.value.messages.map(m => m.id)))
   historyReadyByPeer.value.set(peerId, true)
 } else {
    await loadHistory(peerId)
  }
  hasMoreByPeer.value.set(peerId, (selectedUser.value?.messages?.length || 0) >= MSG_LIMIT)
  // okundu
 if (!nextCursorByPeer.value.has(peerId) || nextCursorByPeer.value.get(peerId) == null) {
   const oldest = selectedUser.value.messages?.[0]?.createdAt
   if (oldest != null) nextCursorByPeer.value.set(peerId, Number(oldest) - 1)
 }
  const ts = selectedUser.value.messages.at(-1)?.createdAt || Date.now()
  setLastRead(peerId, ts)
  const gg = groups.value.find(x => String(x.id) === String(g.id))
  if (gg) gg.unread = 0

  await scrollBottom()
  await rebuildDayOnNextTick()
}

async function ensureAllUsersLoaded() {
  if (allUsersMap.value.size > 0) return
  const { data } = await api.get('/users')
  const m = new Map()
  data.forEach(u => m.set(String(u.id), u))
  allUsersMap.value = m
}
function openMembersView() {
  // eğer memberIds yoksa detay çek
  if (!selectedGroup.value?.memberIds) {
    api.get(`/group/${selectedGroup.value.id}`).then(({data}) => {
      selectedGroup.value = data
      membersViewOpen.value = true
    }).catch(()=> membersViewOpen.value = true)
  } else {
    membersViewOpen.value = true
  }
}

function userNameById(id) {
  const u = allUsersMap.value.get(String(id))
  if (!u) return `#${id}`
  const full = [u.first_name || u.firstName, u.last_name || u.lastName].filter(Boolean).join(' ')
  return full || u.username || u.email || `#${id}`
}
 
async function fetchGroupPerms(id) {
  try {
    const { data } = await api.get(`/group/${id}/permissions`)
    groupPerms.value = {
      canUpdate: !!data?.canUpdate,
      canDelete: !!data?.canDelete,
      canGrant:  !!data?.canGrant,   // 👈 eklendi
    }
  } catch {
    groupPerms.value = isAdmin.value
      ? { canUpdate:true, canDelete:true, canGrant:true }
      : { canUpdate:false, canDelete:false, canGrant:false }
  }
}


async function loadAllHistories() {
  const readMap = readMapRef.value
  const lastSeen = getLastSeen()

  for (const u of users.value) {
    await loadHistory(u.id)

    if (readMap[u.id] == null && lastSeen > 0) {
      readMap[u.id] = lastSeen
    }
    u.lastTs = lastMessageTs(u) || 0
    u.unread = computeUnread(u)

  }
  saveReadMap(readMap)
  sortUserList()
}
async function loadOlderMessages(peerId) {
  if (loadingOlder.value) return
  const key = String(peerId)
  const cursor = nextCursorByPeer.value.get(key)
  const hasMore = hasMoreByPeer.value.get(key) || false
  if (!hasMore || cursor == null) return

  loadingOlder.value = true
  try {
    const container = scrollPane.value
    const prevScrollHeight = container?.scrollHeight ?? 0
    const firstIdBefore = selectedUser.value?.messages?.[0]?.id

    const params = new URLSearchParams({
      peerId: key,
      limit: String(MSG_LIMIT),
      beforeTs: String(cursor)   // DİKKAT: nextCursor → beforeTs
    })
    const res = await fetch(`${API_BASE}/messages?${params.toString()}`, { headers: authHeaders() })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const { items, hasMore: moreAfter, nextCursor } = unwrapHistory(await res.json())

    // ... (mapleme, dedup, tepeye prepend + scroll koruma)


    // --- mapleme (loadHistory ile birebir)
    const isGroup = isGroupId(key)
    const mapped = items.map(x => {
      const ts  = toMillis(x.createdAt)
      const gid = normalizeGroupId(x.groupId)
      const toIdForMid = isGroup ? null : key
      const id  = String(x.id ?? makeMid({
        fromId: String(x.fromUserId),
        toId:   toIdForMid,
        ts,
        content: x.content,
        gid
      }))
      return {
        id,
        fromUserId: String(x.fromUserId),
        toUserId:   String(x.toUserId ?? ''),
        content:    String(x.content ?? ''),
        createdAt:  ts,
        groupId:    gid,
      }
    }).sort((a,b) => a.createdAt - b.createdAt)

    // --- dedup  TEPEYE PREPEND
    const set = seenIdsByPeer.value.get(key) ?? new Set()
    const older = []
    for (const m of mapped) {
      if (set.has(m.id)) continue
      set.add(m.id)
      older.push(m)
    }
    seenIdsByPeer.value.set(key, set)
    if (older.length > 0) {
      // mevcut liste
      const peer =
        (selectedUser.value?.id === key ? selectedUser.value :
         users.value.find(u => u.id === key)) || selectedUser.value
      // tepeye ekle
      peer.messages = [...older, ...peer.messages]

      // grup cache de güncel kalsın
      if (isGroup) {
        const cur = groupCache.value.get(key) || []
        groupCache.value.set(key, [...older, ...cur])
      }

      // --- scroll koruma: önceki ilk mesaja göre offset ayarla
      await nextTick()
      if (container) {
        const newScrollHeight = container.scrollHeight
        const delta = newScrollHeight - prevScrollHeight
        container.scrollTop += delta 
      }
      
    }
    await rebuildDayOnNextTick()
    // --- pagination flag/cursor güncelle
    hasMoreByPeer.value.set(key,nextCursor ?? null)
    nextCursorByPeer.value.set(key, nextCursor ?? null)
    console.log('[older] fetched:', items.length, 'mapped:', mapped.length, 'prepended:', older.length, 'next:', nextCursor)
if (older.length === 0) {
  message.info('Daha eski mesaj yok'); // ant message
}

  } finally {
    loadingOlder.value = false
  }
}
async function loadHistory(peerId, beforeTs = null) {
  historyReadyByPeer.value.set(String(peerId), false);
  const params = new URLSearchParams({
    peerId: String(peerId),
    limit: String(MSG_LIMIT)
  })
  if (beforeTs != null) params.set('beforeTs', String(beforeTs)) // DİKKAT: beforeTs

  const res = await fetch(`${API_BASE}/messages?${params.toString()}`, { headers: authHeaders() })
  if (!res.ok) return
  const payload = await res.json()
  const { items, hasMore, nextCursor } = unwrapHistory(payload)
 console.log('[older] cursor in:',   'got', items?.length, 'nextCursor:', nextCursor, 'hasMore:', hasMore, payload)

  let peer = users.value.find(u => u.id === String(peerId));
  if (!peer && isGroupId(peerId) && selectedUser.value?.id === String(peerId)) peer = selectedUser.value;
  if (!peer) return;

  const key = String(peerId);
  const set = seenIdsByPeer.value.get(key) ?? new Set();
  
  const me = String(meId.value);
  const isGroup = isGroupId(peerId);

  const msgs = items.map(x => {
    const ts = toMillis(x.createdAt);
    const gid = normalizeGroupId(x.groupId);

    // DM'ler için toId'yi doğru şekilde ayarla
    const toIdForMid = isGroup ? null : peerId;
    
    // Mesaj kimliğini oluştur veya kullan
    const id = String(x.id ?? makeMid({
      fromId: String(x.fromUserId),
      toId: toIdForMid,
      ts,
      content: x.content,
      gid
    }));
    
    return {
      id,
      fromUserId: String(x.fromUserId),
      toUserId: String(x.toUserId ?? ''),
      content: String(x.content ?? ''),
      createdAt: ts,
      groupId: gid,
    };
  }).sort((a,b) => a.createdAt - b.createdAt);
  
  for (const m of msgs) {
    if (set.has(m.id)) continue;
    set.add(m.id);
    peer.messages.push(m);
  }
  
  seenIdsByPeer.value.set(key, set);
  if (isGroupId(peerId)) {
    const cur = groupCache.value.get(key) || [];
    const have = new Set(cur.map(x => x.id));
    for (const m of peer.messages) if (!have.has(m.id)) cur.push(m);
    groupCache.value.set(key, cur);
  }
  
  peer.lastMessage = peer.messages.at(-1)?.content ?? '';
  peer.lastTs = lastMessageTs(peer) || 0;
  peer.unread = computeUnread(peer);
  hasMoreByPeer.value.set(String(peerId), !!nextCursor || !!hasMore)
  nextCursorByPeer.value.set(String(peerId), nextCursor ?? null)
  historyReadyByPeer.value.set(String(peerId), true)
  await rebuildDayOnNextTick()
}


async function connectWs () {
  const token = localStorage.getItem('jwtToken')
  if (!token) return

  const client = new Client({
    webSocketFactory: () => new SockJS(`${WS_URL}?access_token=${encodeURIComponent(token)}`),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (msg) => console.log('[STOMP]', msg)
  })

  client.onConnect = () => {
client.subscribe('/user/queue/messages', async (frame) => {
  let payload
  try { payload = JSON.parse(frame.body) } catch { return }

  const fromId = String(payload.fromUserId)
  const toId   = String(payload.toUserId)
  const ts     = toMillis(payload.createdAt)

  const gid = normalizeGroupId(payload.groupId)
 const isGroup = !!gid
 const mid = String(payload.id ?? makeMid({ fromId, toId, ts, content: payload.content, gid }))

  const peerId = isGroup ? gid : (fromId === meId.value ? toId : fromId)

  // --- Dedup peerId bazında 
  const set = seenIdsByPeer.value.get(peerId) ?? new Set()
  if (set.has(mid)) return
  set.add(mid)
  seenIdsByPeer.value.set(peerId, set)

  const msg = { id: mid, fromUserId: fromId, toUserId: toId, content: String(payload.content ?? ''), createdAt: ts ,groupId: gid || null}

  if (isGroup) {
    if (!historyReadyByPeer.value.get(peerId)) {
      const arr = groupCache.value.get(peerId) || []
      if (!arr.some(m => m.id === mid)) arr.push(msg)
      groupCache.value.set(peerId, arr)
      return
    }
     // Menü: son mesaj + rozet
    const gidNum = peerId.replace('group:', '')
    const g = groups.value.find(x => String(x.id) === gidNum)
    if (g) {
      g.lastMessage = msg.content
    if (selectedUser.value?.id !== peerId && fromId !== meId.value) {
      g.unread = (g.unread || 0) + 1
    }    }

  // Grup ekranı açıksa doğrudan ekle okundu scroll
  if (selectedUser.value?.id === peerId) {
    selectedUser.value.messages.push(msg)
    setLastRead(peerId, ts)
    await scrollBottom()
  }
  return
  }

  // --- DM akışı (mevcut davranış)
  let peer = users.value.find(u => u.id === peerId)
  if (!peer) {
    peer = {
      id: peerId,
      name: `Kullanıcı #${peerId}`,
      avatar: makeAvatar({ firstName: '?', lastName: peerId }),
      lastMessage: '',
      messages: [],
      unread: 0,
      lastTs: 0
    }
    users.value.unshift(peer)
  }

  peer.messages.push(msg)
  peer.lastMessage = msg.content
  peer.lastTs = ts

  const isIncoming = (toId === meId.value)
  if (isIncoming) {
    const openNow = isPeerOpen(peer.id)
    if (openNow) {
      setLastRead(peer.id, ts)
      peer.unread = 0
    } else {
      peer.unread = (peer.unread || 0) + 1
    }
  }
  bumpPeerTop(peer, ts)
  sortUserList()

  if (selectedUser.value?.id === peer.id){
     if (fromId !== meId.value && isPeerOpen(peerId)) {
      setLastRead(peerId, msg.createdAt)
    }
    await scrollBottom()

   return
  } 
})

  }

  client.onStompError    = (f) => console.error('STOMP error', f.headers, f.body)
  client.onWebSocketError = (e) => console.error('WS error', e)

  stomp.value = client  
  client.activate()
}


const handleSelectUser = async ({ key }) => {
  const peer = users.value.find(u => u.id === key)
  if (!peer) return
  selectedUser.value = peer;

  if (peer.messages.length === 0) {
    await loadHistory(peer.id)
  }
  hasMoreByPeer.value.set(peer.id, (peer.messages?.length || 0) >= MSG_LIMIT)
  await scrollBottom() 
  await rebuildDayOnNextTick()
  const ts = lastIncomingTs(peer) || lastMessageTs(peer) || Date.now()
  setLastRead(peer.id, ts)
  peer.unread = 0
  sortUserList()
 if (!nextCursorByPeer.value.has(peer.id) || nextCursorByPeer.value.get(peer.id) == null) {
   const oldest = peer.messages?.[0]?.createdAt
   if (oldest != null) nextCursorByPeer.value.set(peer.id, Number(oldest) - 1)
 }
}

// === helpers ===  
// --- Kayan gün etiketi state'i ---
const floatingDay = ref('')
let measureRaf = 0

function dayLabelFromKey(key) {
 const today = dayKey(Date.now())
 if (key === today) return 'Bugün'
 const yesterday = dayKey(Date.now() - 24*60*60*1000)
 if (key === yesterday) return 'Dün'
 const [y,m,d] = key.split('-').map(Number)
 return new Date(y, m-1, d).toLocaleDateString('tr-TR', { day:'2-digit', month:'short', year:'numeric' })
}

function updateFloatingDay() {
 const pane = scrollPane.value
 if (!pane) return
 const nodes = pane.querySelectorAll('.msg-row[data-ts]')
 if (!nodes.length) { floatingDay.value = ''; return }
 const targetY = pane.scrollTop +8
 let chosenTs = Number(nodes[0].dataset.ts)
 for (let i=0;i<nodes.length;i++){
   const el = nodes[i]
   if (el.offsetTop <= targetY) chosenTs = Number(el.dataset.ts)
   else break
 }
 floatingDay.value = dayLabelFromKey(dayKey(chosenTs))
}
function onPaneScroll(){
 if (measureRaf) cancelAnimationFrame(measureRaf)
 measureRaf = requestAnimationFrame(updateFloatingDay)
}
async function rebuildDayOnNextTick(){
 await nextTick()
 updateFloatingDay()
}

// YYYY-MM-DD (kullanıcının saat dilimine göre)
function dayKey(ms) {
  const d = new Date(Number(ms || Date.now()))
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function dayLabel(key) {
  const today = dayKey(Date.now())
  if (key === today) return 'Bugün'

  const yesterday = dayKey(Date.now() - 24 * 60 * 60 * 1000)
  if (key === yesterday) return 'Dün'

  // Daha eski günler: 12 Eyl 2025 formatı (tr-TR yerelleştirme)
  const [y, m, d] = key.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  return dt.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
}

function isGroupId(id) {
  return String(id || '').startsWith('group:')
}
 
function nameColor(userId) {
  const s = String(userId ?? '')
  // basit hash
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  // 0..360
  const hue = h % 360
  return `hsl(${hue}, 65%, 45%)`
}
// === helpers ===
function unwrapHistory(json) {
  if (Array.isArray(json)) return { items: json, nextCursor: null, hasMore: false }
  const items = Array.isArray(json?.items) ? json.items : []
  return { items, nextCursor: json?.nextCursor ?? null, hasMore: !!json?.hasMore }
} 

function sortUserList() {
  users.value = [...users.value].sort((a, b) => {
    const ua = (a.unread || 0) > 0 ? 1 : 0
    const ub = (b.unread || 0) > 0 ? 1 : 0
    if (ua !== ub) return ub - ua
    const ta = a.lastTs ?? lastMessageTs(a) ?? 0
    const tb = b.lastTs ?? lastMessageTs(b) ?? 0
    return tb - ta
  })
}

function bumpPeerTop(peer, ts) {
  peer.lastTs = ts ?? lastMessageTs(peer) ?? Date.now()
  const idx = users.value.findIndex(u => u.id === peer.id)
  if (idx > -1) {
    users.value.splice(idx, 1)
    users.value.unshift(peer)
  }
}
const sendMessage = () => {
  if (!newMessage.value.trim() || !selectedUser.value) return;

  const text = newMessage.value;
  const peerId = selectedUser.value.id;
  
  if (stomp.value?.connected) {
    const payload = { toUserId: peerId, content: text };
    
    stomp.value.publish({
      destination: '/app/chat.send',
      body: JSON.stringify(payload),
    });

    const now = Date.now();
    const isGroup = isGroupId(peerId);
    
    const tempMsg = {
      id: makeMid({
        fromId: String(meId.value),
        toId: isGroup ? null : peerId, // Correct: Use peerId for DMs
        ts: now,
        content: text,
        gid: isGroup ? peerId : null
      }),
      fromUserId: String(meId.value), 
      toUserId: isGroup ? null : peerId, // Correct: Use peerId for DMs
      content: text,
      createdAt: now,
      groupId: isGroup ? peerId : null,
    };

    if (isPeerOpen(peerId)) setLastRead(peerId, now)
    bumpPeerTop(selectedUser.value, now)
    if (isGroup) {
          selectedUser.value.messages.push(tempMsg);

      const g = groups.value.find(x => x.id === peerId.substring('group:'.length));
      if (g) g.lastMessage = text;
    } else {
      const u = users.value.find(x => x.id === peerId);
      if (u) u.lastMessage = text;
    }

    nextTick(() => scrollBottom());
    rebuildDayOnNextTick()
    newMessage.value = '';
  } else {
    console.warn('STOMP bağlı değil.');
  }
};
function lastIncomingTs(peer) {
  const me = String(meId.value)
  let t = 0
  for (const m of peer.messages) {
    if (String(m.toUserId) === me) t = Math.max(t, Number(m.createdAt) || 0)
  }
  return t
}
function normalizeGroupId(raw) {
  if (!raw) return null
  const s = String(raw)
  return s.startsWith('group:') ? s : `group:${s}`
}
 
function makeMid({ fromId, toId, ts, content, gid }) {
  if (gid) {
    return `${gid}|${fromId}|${ts}|${content ?? ''}`;
  }
  // DM'ler için ID'yi tutarlı hale getir
  const [user1, user2] = [String(fromId), String(toId)].sort();
  return `${user1}|${user2}|${ts}|${content ?? ''}`;
}

function isMine(m) { return String(m.fromUserId) === String(meId.value) }
function fmtTime(ms) {
  if (!ms) return ''
  const d = new Date(ms)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
function toMillis(v) {
  if (!v) return Date.now()
  if (typeof v === 'number') return v
  const t = Date.parse(v); return Number.isNaN(t) ? Date.now() : t
}
async function scrollBottom() {
  await nextTick()
  const el = scrollPane.value
  if (el) el.scrollTop = el.scrollHeight
}
/* ---------- Create Group Modal state ---------- */
const createOpen = ref(false)
const creating = ref(false)
const groupName = ref('')
const search = ref('')
const selectedIds = ref([])

const openCreateGroup = () => {
  createOpen.value = true
}

const resetCreate = () => {
  groupName.value = ''
  search.value = ''
  selectedIds.value = []
  createOpen.value = false
}

const filteredUsers = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter(u =>
    (u.name && u.name.toLowerCase().includes(q)) ||
    (u.username && String(u.username).toLowerCase().includes(q)) ||
    (u.email && String(u.email).toLowerCase().includes(q))
  )
})

const selectColumns = [
  { title: 'Name', dataIndex: 'name' },
  { title: 'Username', dataIndex: 'username' },
  { title: 'Email', dataIndex: 'email' }
]

const rowSelection = {
  selectedRowKeys: selectedIds,
  onChange: (keys) => { selectedIds.value = keys }
}

const noop = () => {}

const handleCreateGroup = async () => {
  const name = groupName.value.trim()
  if (!name) return
  creating.value = true
  try {
    const { data } = await api.post('/group', {
      name,
      userIds: selectedIds.value.map(id => Number(id)) // backend Long bekliyor
    })
    message.success(`Group ${data.name} succesfully created `)
    resetCreate()
    loadMyGroups()
  } catch (e) {
    console.error(e)
    const msg = e?.response?.data?.message || 'Failed to create group'
    message.error(msg)
  } finally {
    creating.value = false
  }
}
</script>  
<style scoped>
/* Ant Menu item’ın iki satırı sığdırabilmesi için */ 
:deep(.ant-menu-item) {
  height: auto !important;               /* sabit yükseklik yok */
  padding-top: 8px !important;
  padding-bottom: 8px !important;
  align-items: flex-start !important;     /* içeriği yukarı hizala */
}

/* Seçilince de gri alt satırın rengi bozulmasın */
:deep(.ant-menu-item-selected) .sub {
  color: #8c8c8c !important;
}
/* Okunmamış grup satırı vurgusu */
:deep(.ant-menu-item.is-unread) {
  background: #e6f4ff !important; /* hafif parlak mavi */
}

/* Sol ince vurgu şeridi */
:deep(.ant-menu-item.is-unread)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: #1677ff;
  border-radius: 2px;
}

/* Başlığı biraz daha baskın yap */
:deep(.ant-menu-item.is-unread .group-item .title) {
  font-weight: 700;
}

/* İç kapsayıcı: iki satır */
.group-item {
  display: grid;
  grid-template-rows: auto auto;
  row-gap: 2px;
  width: 100%;
}

.group-item .title {
  font-weight: 600;
  line-height: 18px;
}

.group-item .sub {
  font-size: 12px;
  line-height: 16px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Sağ üstte okunmamış rozet */
.badge {
  position: absolute;
  right: 12px;
  top: 8px;
  background: #1677ff;
  color: #fff;
  border-radius: 999px;
  min-width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  font-size: 12px;
  padding: 0 6px;
}
 .floating-day{
  position: sticky;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  display: inline-block;
  padding: 4px 10px;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 12px;
  font-weight: 600;
  border-radius: 999px;
  border: 1px solid #bae0ff;
  pointer-events: none;
}

</style> 
