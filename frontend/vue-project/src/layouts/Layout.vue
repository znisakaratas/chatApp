<template>
  <a-layout class="app-shell">
    <!-- NAVBAR -->
    <a-layout-header :style="headerStyle">
      <div style="color:#fff;font-size:20px;font-weight:bold">BİTES</div>

      <div style="display:flex;align-items:center;gap:12px">
        <!-- Admin toggle button -->
        <a-button
          v-if="isAdmin"
          size="small"
          :type=" 'default'"
          ghost
          @click="goToAdminOrHome"
        >
          {{ adminButtonLabel }}
        </a-button>

        <!-- Avatar + Dropdown -->
        <a-dropdown placement="bottomRight" :trigger="['click']">
          <a-avatar :style="avatarStyle" size="large" class="cursor-pointer" :src="avatarUrl">
            <template #icon><UserOutlined /></template>
          </a-avatar>

          <template #overlay>
            <a-menu>
              <a-menu-item key="1" @click="goToProfile">Profil</a-menu-item>
              <a-menu-item key="2" danger @click="confirmLogout">Çıkış Yap</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>

    <!-- Sayfa içeriği -->
    <a-layout-content :style="contentStyle">
      <router-view />
    </a-layout-content>

    <!-- Profil Modal -->
    <a-modal v-model:open="profileOpen" title="Profil" :footer="null">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:12px">
        <a-avatar :src="avatarUrl" size="large">
          <template #icon><UserOutlined /></template>
        </a-avatar>
        <div>
          <div style="font-weight:600">{{ fullName || '-' }}</div>
          <div style="color:#888">{{ user.username || user.email || '' }}</div>
        </div>
      </div>
      <a-descriptions bordered size="small" :column="1">
        <a-descriptions-item label="Ad Soyad">{{ fullName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Kullanıcı Adı">{{ user.username || '-' }}</a-descriptions-item>
        <a-descriptions-item label="E-posta">{{ user.email || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </a-layout>
</template>

<script setup>
import { ref, computed, onMounted, getCurrentInstance, inject } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useToast } from 'vue-toastification'
import { Modal } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const HEADER_H = 54; // ant default 64px, değiştirirsen alttakileri de güncelle

const headerStyle = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  zIndex: 1000,
  height: `${HEADER_H}px`,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  paddingInline: '24px'

};

const contentStyle = {
  marginTop: `${HEADER_H}px`,
  height: `calc(100vh - ${HEADER_H}px)`,
  padding: "24px",               // önemli: chat sayfası tam ekran çalışsın
  overflow: 'hidden',
  background: '#fff'
};
const avatarStyle = {
  backgroundColor: 'rgba(255,255,255,0.2)',
  color: '#fff',
}

const router = useRouter()
const route = useRoute()
const toast = useToast()
const { appContext } = getCurrentInstance()
const keycloak = appContext?.config?.globalProperties?.$keycloak

// provide edilmiş kullanıcı varsa kullan; yoksa /api/user'dan çek
const providedUser = inject('currentUser', null)
const user = ref(providedUser || {})

const buildName = (u) => [u.firstName, u.lastName].filter(Boolean).join(' ') || u.name || ''
const makeAvatar = (u) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(buildName(u) || u.username || u.email || 'User')}`

const fullName = computed(() => buildName(user.value))
const avatarUrl = computed(() => makeAvatar(user.value))

const isAdmin = computed(() => {
  const u = user.value || {}
  const type = String(u.type || u.role || u.userType || '').toUpperCase()
  const rolesRaw = u.roles || u.authorities || u.authorities?.map?.(a => a.authority) || []
  const roles = Array.isArray(rolesRaw) ? rolesRaw.map(r => String(r).toUpperCase()) : String(rolesRaw).toUpperCase()
  const hasRole =
    (Array.isArray(roles) ? roles.some(r => r.includes('ADMIN')) : roles.includes('ADMIN')) ||
    type.includes('ADMIN')
  return !!hasRole
})

// bulunduğumuz rota /admin mi?
const isOnAdmin = computed(() => route.path.startsWith('/admin'))
const adminButtonLabel = computed(() => (isOnAdmin.value ? 'Mesaj Sayfası' : 'Admin Sayfası'))
function goToAdminOrHome() {
  if (!isAdmin.value) return
  if (isOnAdmin.value) {
    router.push('/')            // Admin'deyken → Message Page (home)
  } else {
    router.push('/admin')       // Home'dayken → Admin Page
  }
}

// Profil Modal
const profileOpen = ref(false)
const goToProfile = () => { profileOpen.value = true }

const confirmLogout = () => {
  Modal.confirm({
    title: 'Çıkış yapmak istediğinizden emin misiniz?',
    okText: 'Evet',
    cancelText: 'Hayır',
    onOk: () => {
      toast.info('Çıkış yapılıyor...')
      setTimeout(() => {
        localStorage.clear()
        toast.success('Başarıyla çıkış yapıldı.')
        const logoutUrl = keycloak?.createLogoutUrl
          ? keycloak.createLogoutUrl({ redirectUri: window.location.origin })
          : window.location.origin
        window.location.href = logoutUrl
      }, 800)
    },
    onCancel: () => toast.info('Çıkış iptal edildi.')
  })
}

// kullanıcı yoksa yükle
onMounted(async () => {
    console.log('mounted: providedUser =', providedUser)

  if (!providedUser) {
    try {
      const { data } = await api.get('/user')
      user.value = data
      console.log('User from backend:', data)

    } catch (e) {
      console.error('user fetch error', e)
    }
  }
})
</script>

<style scoped>
.cursor-pointer { cursor: pointer; }
</style>
