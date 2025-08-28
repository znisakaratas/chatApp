<template>
  <a-layout style="height:100vh">
    <a-layout-sider width="300" theme="light">
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
            

      <a-menu>
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
      <div v-if="selectedUser" style="display:flex;flex-direction:column;height:100%">
        <div style="padding:16px;background:#fff;font-weight:600;border-bottom:1px solid #ddd">
          {{ selectedUser.name }}
        </div>

        <div ref="scrollPane" style="flex:1;padding:16px;overflow-y:auto">
          <div v-for="(m, i) in selectedUser.messages" :key="m.id || i" style="margin-bottom:8px;display:flex"
            :style="{ justifyContent: isMine(m) ? 'flex-end' : 'flex-start' }">
            <div style="background:#fff;padding:8px 12px;border-radius:8px;max-width:70%">
              <div>{{ m.content }}</div>
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
    </a-layout-content>
  </a-layout>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick,computed } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'  
import { message } from 'ant-design-vue'
import api from '@/api'
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
const MSG_LIMIT = 50

const users = ref([])           // { id, name, avatar, lastMessage, messages:[], unread }
const meId = ref(null)
const selectedUser = ref(null)
const newMessage = ref('')
const stomp = ref(null)
const scrollPane = ref(null)
const LAST_SEEN_KEY = 'chat_last_seen_ms'
function getLastSeen() { return Number(localStorage.getItem(LAST_SEEN_KEY) || 0) }
function setLastSeen(ms = Date.now()) { localStorage.setItem(LAST_SEEN_KEY, String(ms)) }

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
})
const cleanup = []
onBeforeUnmount(() => {
  if (onBye) window.removeEventListener('beforeunload', onBye)
  if (stomp.value?.active) stomp.value.deactivate()
  cleanup.forEach(fn => { try { fn() } catch {} })
})

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


async function loadHistory(peerId) {
  const res = await fetch(`${API_BASE}/messages?peerId=${encodeURIComponent(peerId)}&limit=${MSG_LIMIT}`, {
    headers: authHeaders()
  })
  if (!res.ok) return
  const list = await res.json() // [{id,fromUserId,toUserId,content,createdAt}]
  const peer = users.value.find(u => u.id === String(peerId))
  if (!peer) return

  const set = seenIdsByPeer.value.get(peer.id) ?? new Set()
  for (const x of list) {
    const msg = {
      id: String(x.id ?? `${x.fromUserId}|${x.toUserId}|${x.createdAt}|${x.content ?? ''}`),
      fromUserId: String(x.fromUserId),
      toUserId: String(x.toUserId ?? ''),
      content: String(x.content ?? ''),
      createdAt: toMillis(x.createdAt)
    }
    if (set.has(msg.id)) continue
    set.add(msg.id)
    peer.messages.push(msg)
  }
  seenIdsByPeer.value.set(peer.id, set)

  // son mesaj metnini güncelle
  peer.lastMessage = peer.messages.at(-1)?.content ?? ''
  peer.lastTs = lastMessageTs(peer) || 0
  peer.unread = computeUnread(peer)

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
      const mid    = String(payload.id ?? `${fromId}|${toId}|${ts}|${payload.content ?? ''}`)

      // Hangi sohbet?
      const peerId = (fromId === meId.value) ? toId : fromId

      // Peer hazırla (yoksa oluştur)
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

      // Dedup
      const set = seenIdsByPeer.value.get(peer.id) ?? new Set()
      if (set.has(mid)) return
      set.add(mid)
      seenIdsByPeer.value.set(peer.id, set)

      // Mesajı ekle
      const msg = { id: mid, fromUserId: fromId, toUserId: toId, content: String(payload.content ?? ''), createdAt: ts }
      peer.messages.push(msg)
      peer.lastMessage = msg.content
      peer.lastTs = ts

      // --- ROZET KURALI (tek yer) ---
      const isIncoming = (toId === meId.value)
      if (isIncoming) {
        const openNow = isPeerOpen(peer.id) // seçili + fokus + visible
        if (openNow) {
          // sohbet ekranda → okundu
          setLastRead(peer.id, ts)
          peer.unread = 0
        } else {
          // sohbet kapalı → rozet +1 (MANUEL)
          peer.unread = (peer.unread || 0) + 1
        }
      }
      // NOT: Outgoing için unread değiştirmiyoruz

      // Listeyi güncelle
      bumpPeerTop(peer, ts)
      sortUserList()

      // Açık pencere ise aşağı kaydır
      if (selectedUser.value?.id === peer.id) await scrollBottom()
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
  await scrollBottom() 
  const ts = lastIncomingTs(peer) || lastMessageTs(peer) || Date.now()
  setLastRead(peer.id, ts)
  peer.unread = 0
  sortUserList()

}

// === helpers ===
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
  if (!newMessage.value.trim() || !selectedUser.value) return
  const text = newMessage.value
  newMessage.value = ''

  // optimistic yok → WS echo ile gelecek
  if (stomp.value?.connected) {
    stomp.value.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ toUserId: selectedUser.value.id, content: text })
    })
  } else {
    console.warn('STOMP bağlı değil')
  }
}
function lastIncomingTs(peer) {
  const me = String(meId.value)
  let t = 0
  for (const m of peer.messages) {
    if (String(m.toUserId) === me) t = Math.max(t, Number(m.createdAt) || 0)
  }
  return t
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
    // İstersen burada grup listesini yeniletebilirsin
  } catch (e) {
    console.error(e)
    const msg = e?.response?.data?.message || 'Failed to create group'
    message.error(msg)
  } finally {
    creating.value = false
  }
}
</script>