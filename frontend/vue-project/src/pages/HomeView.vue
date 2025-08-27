<template>
  <a-layout style="height: 100vh;">
    <!-- Sidebar -->
    <a-layout-sider width="300" theme="light" >
      <div style="padding: 10px; font-weight: bold; font-size: 18px;">Kişiler</div>
      <a-menu
      :selectedKeys="selectedUser ? [selectedUser.id] : []"
      @click="handleSelectUser"
      style="padding-left: 0;"
    >
        <a-menu-item v-for="user in users" :key="user.id" :style="{ paddingLeft: '0px', height: '72px' }">
          <a-avatar :src="user.avatar" :size="40" style="float: left; margin-right: 12px;" />
          <div style="overflow: hidden;">
            <div style="font-weight: 500;">{{ user.name }}</div>
            <div style="color: gray; font-size: 12px;">{{ user.lastMessage }}</div>
          </div>
        </a-menu-item>

      </a-menu>
    </a-layout-sider>

    <!-- Chat panel -->
    <a-layout-content style="background: #f5f5f5;">
      <div v-if="selectedUser" style="display: flex; flex-direction: column; height: 100%;">
        <!-- Üst panel -->
        <div style="padding: 16px; background: #fff; font-weight: bold; border-bottom: 1px solid #ddd;">
          {{ selectedUser.name }}
        </div>

        <!-- Mesajlar -->
        <div style="flex: 1; padding: 16px; overflow-y: auto;">
          <div v-for="(msg, index) in selectedUser.messages" :key="index" style="margin-bottom: 8px;">
            <div style="background: #fff; padding: 8px 12px; border-radius: 8px; max-width: 60%;">
              {{ msg }}
            </div>
          </div>
        </div>

        <!-- Mesaj yazma alanı -->
        <div style="
    position: fixed;
    bottom: 0;
    right: 0;
    left: 300px; /* Sidebar genişliği kadar */
    background: #fff;
    padding: 12px 24px;
    border-top: 1px solid #ddd;
    z-index: 100;
  ">
          <a-input-search v-model:value="newMessage" placeholder="Mesaj yaz..." enter-button="Gönder"
            @search="sendMessage" />
        </div>

      </div>
      <div v-else style="display: flex; justify-content: center; align-items: center; height: 100%;">
        <p>Lütfen bir kişi seçin.</p>
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

const API_BASE = 'http://localhost:8080/api'

const users = ref([])
const selectedUser = ref(null)
const newMessage = ref('')
const loading = ref(true)
const error = ref(null)

const stomp = ref(null)
const connected = ref(false)

const buildName = (u) => {
  const full = [u.firstName, u.lastName].filter(Boolean).join(' ')
  return full || u.username || u.email || `Kullanıcı #${u.id}`
}
const makeAvatar = (u) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(buildName(u))}`

onMounted(async () => {
  const token = localStorage.getItem('jwtToken')
  const commonHeaders = { Authorization: token ? `Bearer ${token}` : undefined }

  try {
    const [meRes, listRes] = await Promise.all([
      fetch(`${API_BASE}/user`, { headers: commonHeaders }),
      fetch(`${API_BASE}/users`, { headers: commonHeaders })
    ])

    const me = meRes.ok ? await meRes.json() : null
    if (!listRes.ok) throw new Error(`Kişiler yüklenemedi (HTTP ${listRes.status})`)
    const list = await listRes.json()
    if (me?.id != null) localStorage.setItem('id', String(me.id))

    // Giriş yapan kişiyi rehberden çıkar
    const filtered = list.filter(u => {
      if (!me) return true
      if (me.id != null && u.id === me.id) return false
      if (me.keycloakId && u.keycloakId && u.keycloakId === me.keycloakId) return false
      if (me.username && u.username && u.username === me.username) return false
      return true
    })

    users.value = filtered.map(u => ({
      id: String(u.id),
      name: buildName(u),
      avatar: makeAvatar(u),
      lastMessage: '',
      messages: [] // buraya string listesi tutuyoruz (basit)
    }))

    // ---- WS'ye bağlan ----
    await connectWs()
  } catch (e) {
    error.value = e.message || String(e)
    console.error(e)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (stomp.value?.active) stomp.value.deactivate()
})

async function connectWs () {
  const token = localStorage.getItem('jwtToken')
  if (!token) {
    console.warn('JWT yok; WS bağlanmadı')
    return
  }

  const url = `http://localhost:8080/ws?access_token=${encodeURIComponent(token)}`
  const client = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 5000,           // otomatik yeniden bağlan
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (msg) => console.log('[STOMP]', msg)
  })

  client.onConnect = () => {
    connected.value = true
    // Kişisel kuyruğuna abone ol
    client.subscribe('/user/queue/messages', (frame) => {
      try {
        const payload = JSON.parse(frame.body) // { fromUserId, content, ts }
        const fromId = String(payload.fromUserId)
        const text = String(payload.content ?? '')

        const u = users.value.find(x => x.id === fromId)
        if (u) {
          u.messages.push(text)
          u.lastMessage = text
          // Şu anda açık konuşma bu kişi ise ekranda da görünür
        } else {
          // Rehberde yoksa istersen ekleyebilirsin
          console.warn('Mesaj gelen kullanıcı rehberde yok:', fromId, payload)
        }
      } catch (err) {
        console.error('Mesaj parse edilemedi', err, frame.body)
      }
    })
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers, frame.body)
  }
  client.onWebSocketError = (e) => {
    console.error('WS error', e)
  }

  stomp.value = client
  client.activate()
}

const handleSelectUser = ({ key }) => {
  selectedUser.value = users.value.find(user => user.id === key)
}

const sendMessage = () => {
  if (!newMessage.value.trim() || !selectedUser.value) return
  const text = newMessage.value

  // UI’yi hemen güncelle (optimistic)
  selectedUser.value.messages.push(text)
  selectedUser.value.lastMessage = text
  newMessage.value = ''

  // STOMP ile backend’e gönder
  if (stomp.value?.connected) {
    stomp.value.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({
        toUserId: selectedUser.value.id,
        content: text
      })
    })
  } else {
    console.warn('STOMP bağlı değil; mesaj gönderilemedi.')
  }
}
</script>
 