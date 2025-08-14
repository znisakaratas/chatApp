<template>
  <a-layout class="min-h-screen">
    <!-- NAVBAR -->
    <a-layout-header :style="headerStyle">
      <div style="color: white; font-size: 20px; font-weight: bold;">BİTES</div>

      <!-- Avatar + Dropdown -->
      <a-dropdown placement="bottomRight" trigger="click">
        <a-avatar :style="avatarStyle" size="large" class="cursor-pointer">
          <template #icon>
            <UserOutlined />
          </template>
        </a-avatar>

        <template #overlay>
          <a-menu>
            <a-menu-item key="1" @click="goToProfile">Profil</a-menu-item>
            <a-menu-item key="2" danger @click="confirmLogout">Çıkış Yap</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </a-layout-header>

    <!-- Sayfa içeriği -->
    <a-layout-content style="padding: 24px; background: #fff;">
      <router-view />
    </a-layout-content>
  </a-layout>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useToast } from 'vue-toastification'
import { getCurrentInstance } from 'vue'
import { Modal } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const toast = useToast()

const { appContext } = getCurrentInstance()
const keycloak = appContext.config.globalProperties.$keycloak

const goToProfile = () => {
  router.push('/profile')
}

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
        const logoutUrl = keycloak.createLogoutUrl({
          redirectUri: window.location.origin
        })
        window.location.href = logoutUrl
      }, 800)
    },
    onCancel: () => {
      toast.info('Çıkış iptal edildi.')
    }
  })
}
</script>

<style scoped>
.cursor-pointer {
  cursor: pointer;
}
</style>

<script>
const headerStyle = {
  backgroundColor: '#1e3a8a',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '0 24px',
  height: '60px'
}
const avatarStyle = {
  backgroundColor: 'rgba(255, 255, 255, 0.2)',
  color: '#fff'
}
</script>
