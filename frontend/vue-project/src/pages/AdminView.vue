<template>
  <div style="padding: 24px;">
    <!-- Butonlar -->
    <div style="margin-bottom: 16px;">
      <a-button type="primary" @click="activeTab = 'users'" style="margin-right: 8px;">Kullanıcı Listesi</a-button>
      <a-button @click="activeTab = 'groups'">Grup Listesi</a-button>
    </div>

    <!-- Kullanıcı Tablosu -->
    <div v-if="activeTab === 'users'">
      <a-table :columns="columns" :data-source="users" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <component
            v-if="column.key === 'customRow'"
            :is="UserRow"
            :user="record" 
          />
        </template>
      </a-table>
    </div>

    <!-- Grup Listesi Alanı (boş şimdilik) -->
    <div v-else>
      <p>Grup listesi buraya gelecek...</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import UserRow from '../components/UserRow.vue' 

const activeTab = ref('users')

const API_BASE = 'http://localhost:8080/api'  
const users = ref([])
const selectedUser = ref(null)
const newMessage = ref('')
const loading = ref(true)
const error = ref(null)
const buildName = (u) => {
  const full = [u.firstName, u.lastName].filter(Boolean).join(' ')
  return full || u.username || u.email || `Kullanıcı #${u.id}`
}
const makeAvatar = (u) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(buildName(u))}`

onMounted(async () => {
  try {
    const res = await fetch(`${API_BASE}/users`, {
      headers: { 
        'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
      }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)

    const data = await res.json()
    // UI’nin beklediği shape’e çevir: messages/lastMessage alanları UI içindir
    users.value = data.map(u => ({
      id: String(u.id),
      name: buildName(u),
      avatar: makeAvatar(u),
      lastMessage: '',
      messages: []
    }))

  } catch (e) {
    error.value = e.message || String(e)
    console.error(e)
  } finally {
    loading.value = false
  }
})

const columns = [
  {
    title: 'Kullanıcı Bilgileri',
    key: 'customRow',
    dataIndex: 'customRow'
  }
]
  
</script>
