<template>
  <div class="group-row">
    <!-- Sol: Grup adı + Kullanıcılar -->
    <div class="left">
      <a-avatar :size="40" class="avatar">
        <template #icon><TeamOutlined /></template>
      </a-avatar>

      <div class="meta">
        <div class="name">{{ group.name }}</div>
        <div class="members">
          <span class="label">Kullanıcılar:</span>
          <span class="list">
            <template v-if="previewNames.length">
              {{ previewNames.slice(0, 3).join(', ') }}<span v-if="previewNames.length > 3">, …</span>
            </template>
            <template v-else>-</template>
          </span>
        </div>
      </div>
    </div>

    <!-- Sağ: Aksiyonlar -->
    <div class="actions">
      <a-button size="small" type="link" @click="openView" :loading="viewLoading">
        <template #icon><InfoCircleOutlined /></template>
       </a-button>
      <a-button size="small" type="link" @click="$emit('edit', group)"  >
        <template #icon><EditOutlined /></template>
       </a-button>
      <a-button size="small" type="link" danger @click="$emit('delete', group)">
        <template #icon><DeleteOutlined /></template>
      </a-button>
    </div>

    <!-- Görüntüleme Modal -->
    <a-modal v-model:open="viewOpen" :title="`Grup: ${group.name}`" :footer="null" :confirmLoading="viewLoading">
      <a-descriptions bordered size="small" :column="1">
        <a-descriptions-item label="Grup Adı">{{ group.name }}</a-descriptions-item>
        <a-descriptions-item label="Üye Sayısı">{{ resolvedMemberNames.length }}</a-descriptions-item>
        <a-descriptions-item label="Üyeler">
          <template v-if="resolvedMemberNames.length">
            <a-tag v-for="n in resolvedMemberNames" :key="n" style="margin-bottom:6px">{{ n }}</a-tag>
          </template>
          <template v-else>-</template>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { TeamOutlined, InfoCircleOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import api from '@/api'

/**
 * Props:
 *  - group: { id, name, memberIds?: number[] }
 * Events:
 *  - edit  (payload: group)
 *  - delete(payload: group)
 */
const props = defineProps({
  group: { type: Object, required: true }
})
defineEmits(['edit', 'delete'])

// ---- helpers
const buildName = (u) => {
  const full = [u.firstName, u.lastName].filter(Boolean).join(' ')
  return full || u.username || u.email || `Kullanıcı #${u.id}`
}

// ---- cache: tüm kullanıcılar (id->user)
const usersMap = ref(new Map())

async function ensureUsersLoaded() {
  if (usersMap.value.size > 0) return
  try {
    const { data } = await api.get('/users')
    const m = new Map()
    for (const u of data) {
      m.set(String(u.id), u)
    }
    usersMap.value = m
  } catch (e) {
    console.error('users load failed', e)
    message.error('Kullanıcılar yüklenemedi')
  }
}

// Satır önizlemesi için ilk 3 isim (props.group.memberIds varsa)
const previewNames = computed(() => {
  if (!props.group?.memberIds || props.group.memberIds.length === 0 || usersMap.value.size === 0) return []
  return props.group.memberIds
    .map(id => usersMap.value.get(String(id)))
    .filter(Boolean)
    .map(buildName)
})

// ---- View (modal) state
const viewOpen = ref(false)
const viewLoading = ref(false)
const resolvedMemberIds = ref([])   // /api/group/{id} cevabından
const resolvedMemberNames = computed(() => {
  if (resolvedMemberIds.value.length === 0) return []
  return resolvedMemberIds.value
    .map(id => usersMap.value.get(String(id)))
    .filter(Boolean)
    .map(buildName)
})

async function openView() {
  viewOpen.value = true
  viewLoading.value = true
  try {
    // grup detayını çek
    const { data } = await api.get(`/group/${props.group.id}`)
    // kullanıcıları cache’e al (ilk kez ise)
    await ensureUsersLoaded()
    resolvedMemberIds.value = Array.isArray(data.memberIds) ? data.memberIds : []
  } catch (e) {
    console.error('group view failed', e)
    message.error('Grup bilgisi alınamadı')
  } finally {
    viewLoading.value = false
  }
}

onMounted(async () => {
  // Satırda isimleri göstermek için kullanıcıları bir kez yükleyelim
  await ensureUsersLoaded()
})
</script>

<style scoped>
.group-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}
.left {
  display: flex;
  align-items: center;
  min-width: 0;
}
.avatar {
  margin-right: 12px;
  background: #e6f4ff;
  color: #1677ff;
}
.meta {
  overflow: hidden;
}
.name {
  font-weight: 600;
  line-height: 1.2;
  margin-bottom: 4px;
}
.members {
  font-size: 12px;
  color: #666;
}
.members .label {
  color: #999;
  margin-right: 4px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
