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
      <a-table
        :columns="groupColumns"
        :data-source="groups"
        :loading="groupLoading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <component
            v-if="column.key === 'groupRow'"
            :is="GroupRow"
            :group="record"
            @edit="onEditGroup"
            @delete="onDeleteGroup"
          />
        </template>
      </a-table>
    </div>
        <a-modal
      v-model:open="updateOpen"
      title="Grubu Güncelle"
      :confirmLoading="updating"
      @ok="handleUpdateGroup"
      @cancel="resetUpdate"
      :okButtonProps="{ disabled: !editName.trim() }"
      destroyOnClose
    >
      <!-- Grup Adı -->
      <div style="margin-bottom:12px;">
        <div style="font-weight:500;margin-bottom:6px;">
          Group Name <span style="color:#f5222d">*</span>
        </div>
        <a-input v-model:value="editName" placeholder="Group name" allow-clear />
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

      <a-table
        :data-source="filteredAllUsers"
        :columns="selectColumns"
        :rowSelection="rowSelection"
        :pagination="{ pageSize: 6 }"
        row-key="id"
        size="small"
      />
      <div style="margin-top:8px; font-size:12px; color:#888;">
        {{ selectedIds.length }} user selected
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted,computed } from 'vue'
import UserRow from '../components/UserRow.vue'  
import GroupRow from '../components/GroupRow.vue'  
import api from '@/api'
import { message,Modal } from 'ant-design-vue'

const activeTab = ref('users')

const API_BASE = 'http://localhost:8080/api'  
const users = ref([])
const selectedUser = ref(null)
const newMessage = ref('')
const loading = ref(true)
const error = ref(null)
const groups = ref([])                
const groupLoading = ref(false)       
const allUsers = ref([])
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
    users.value = data.map(u => ({
      id: String(u.id),
      name: buildName(u),
      avatar: makeAvatar(u),
      email: u.email,
      role: u.role ,
      status: u.status,
      username: u.username,
      firstName: u.first_name,
      lastName: u.last_name,
      lastMessage: '',
      messages: []
    }))
    allUsers.value = data.map(u => ({
      id: String(u.id),
      name: buildName(u),
      username: u.username,
      email: u.email
    }))
  } catch (e) {
    error.value = e.message || String(e)
    console.error(e)
  } finally {
    loading.value = false
  }
  await loadAllGroups()   
})
async function loadAllGroups() {
  try {
    groupLoading.value = true
    const { data } = await api.get('/group') 
     groups.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('groups load error', e)
  } finally {
    groupLoading.value = false
  }
}
const columns = [
  {
    title: 'Kullanıcı Bilgileri',
    key: 'customRow',
    dataIndex: 'customRow'
  }
]
const groupColumns = [
  { title: 'Grup Bilgileri', key: 'groupRow', dataIndex: 'groupRow' }
]
const updateOpen = ref(false)
const updating = ref(false)
const editingGroup = ref(null)   // {id,name,memberIds}
const editName = ref('')
const search = ref('')
const selectedIds = ref([])      // string[] (user ids)

const filteredAllUsers = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return allUsers.value
  return allUsers.value.filter(u =>
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

const rowSelection = computed(() => ({
  selectedRowKeys: selectedIds.value,
  onChange: (keys) => { selectedIds.value = keys }
}))

const noop = () => {}
 
async function onEditGroup(g) {
  try { 
    const { data } = await api.get(`/group/${g.id}`)
    editingGroup.value = data
    editName.value = data.name || g.name || '' 
    selectedIds.value = (data.memberIds || []).map(id => String(id))
 
    if (allUsers.value.length === 0) {
      const res = await api.get('/users')
      allUsers.value = (res.data || []).map(u => ({
        id: String(u.id),
        name: buildName(u),
        username: u.username,
        email: u.email
      }))
    }

    updateOpen.value = true
  } catch (e) {
    console.error(e)
    message.error('Grup bilgisi alınamadı')
  }
}
function resetUpdate() {
  updateOpen.value = false
  updating.value = false
  editingGroup.value = null
  editName.value = ''
  search.value = ''
  selectedIds.value = []
}

async function handleUpdateGroup() {
  if (!editingGroup.value) return
  const name = editName.value.trim()
  if (!name) return
  updating.value = true
  try { 
    const ids = Array.from(new Set(selectedIds.value.map(String))).map(Number)

    await api.put(`/group/${editingGroup.value.id}`, {
      name,
      userIds: ids
    })
    message.success('Group updated')
    resetUpdate()
    await loadAllGroups()
  } catch (e) {
    console.error(e)
    const msg = e?.response?.data?.message || 'Update failed'
    message.error(msg)
  } finally {
    updating.value = false
  }
}
 
function onDeleteGroup(g) {
  Modal.confirm({
    title: `Grup silinsin mi?`,
    content: `"${g.name}" kalıcı olarak silinecek.`,
    okText: 'Sil',
    okType: 'danger',
    cancelText: 'Vazgeç',
    async onOk() {
      try {
        await api.delete(`/group/${g.id}`)
        message.success('Group deleted')
        await loadAllGroups()
      } catch (e) {
        console.error(e)
        const msg = e?.response?.data?.message || 'Delete failed'
        message.error(msg)
      }
    }
  })
}

</script> 

