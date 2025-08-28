<template>
  <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 0;">
    <!-- Sol kısım: Avatar + İsim + Email + Rol -->
    <div style="display:flex; align-items:center;">
      <a-avatar :src="user.avatar" :size="40" />
      <div style="margin-left:12px;">
        <div style="font-weight:500;">{{ user.name }}</div>
        <div style="font-size:12px; color:gray;">{{ user.email }} </div>
      </div>
    </div>

    <!-- Sağ kısım: Bilgi Butonu + Toggle -->
    <div style="display:flex; align-items:center; gap:12px;">
      <a-button type="link" @click="openProfile">
        <template #icon><InfoCircleOutlined /></template>
      </a-button>

      <a-switch
        v-model:checked="isActive"
        :disabled = "isSelf"
        checked-children="Active"
        un-checked-children="Inactive"
        @change="toggleActive"
      />
    </div>

    <!-- Profil Modal -->
    <a-modal v-model:open="profileOpen" title="Profil" :footer="null">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:12px">
        <a-avatar :src="user.avatar" size="large">
          <template #icon><UserOutlined /></template>
        </a-avatar>
        <div>
          <div style="font-weight:600">{{ user.username || '-' }}</div>
         </div>
      </div>
      <a-descriptions bordered size="small" :column="1">
        <a-descriptions-item label="Ad Soyad">{{ user.name || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Kullanıcı Adı">{{ user.username || '-' }}</a-descriptions-item>
        <a-descriptions-item label="E-posta">{{ user.email || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Rol">{{ user.role || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Durum">{{ isActive ? 'Active' : 'Inactive' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, watch, toRefs ,inject,computed} from 'vue'
import { UserOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const props = defineProps({ user: { type: Object, required: true } }
    ,{currentUserId: { type: [String, Number], default: null }} // ← opsiyonel

)
const emit = defineEmits(['updated']) // üst listeyi güncellemek istersen
const providedCurrent = inject('currentUser', null)
const myId = computed(() => props.currentUserId ?? providedCurrent?.id)

const isSelf = computed(() => String(props.user.id) === String(myId.value))

// Profil modal state
const profileOpen = ref(false)
const openProfile = () => { profileOpen.value = true }

// Toggle state → backend status’ünden türet
const isActive = ref(props.user?.status === 'ACTIVE')

// props.user değişirse toggle’ı da senkronla
watch(() => props.user?.status, (v) => {
  isActive.value = v === 'ACTIVE'
})

const toggleActive = async (val) => {
    if (isSelf.value) return; // ekstra guard

  // optimistic update

  const old = !val
  try {
    await api.post('/user', {
      userId: props.user.id,
      status: val ? 'ACTIVE' : 'INACTIVE'
    })
    // local objeyi da senkron tut (liste item’ında görünürlük için)
    props.user.status = val ? 'ACTIVE' : 'INACTIVE'
    emit('updated', { id: props.user.id, status: props.user.status })
  } catch (e) {
    console.error('status update failed', e)
    // geri al
    isActive.value = old
  }
}
</script>
