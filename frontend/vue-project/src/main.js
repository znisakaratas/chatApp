// src/main.js
import { createApp, reactive } from 'vue'
import App from './layouts/App.vue'
import keycloak, { keycloakInitOptions } from './keycloak'
import router from './router'
import api from './api'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

keycloak.init(keycloakInitOptions).then(async (authenticated) => {
  if (!authenticated) return keycloak.login()

  const app = createApp(App)
  app.config.globalProperties.$keycloak = keycloak
  app.use(router)
  app.use(Antd)
  app.use(Toast, { position: 'top-right', timeout: 3000 })

  try {
    const { data } = await api.get('/user')

    if (data.status !== 'ACTIVE') {
      console.warn('Kullanıcı aktif değil, giriş engellendi')
      app.config.globalProperties.$toast?.error?.('Hesabınız aktif değil!') 
      const logoutUrl = keycloak.createLogoutUrl({ redirectUri: window.location.origin })
      window.location.href = logoutUrl
      return
    }
    const currentUser = reactive(data)
    console.log(data.status, "go kylie go")
    app.provide('currentUser', currentUser)
    await router.push('/')
  } catch (e) {
    console.error('api/user failed', e)
    app.config.globalProperties.$toast?.error?.('Kullanıcı bilgisi alınamadı')
    await router.push('/')
  }
  app.mount('#app')
  
})
