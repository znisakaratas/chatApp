<template>
  <a-layout style="height: 100vh;">
    <!-- Sidebar -->
    <a-layout-sider width="300" theme="light" >
      <div style="padding: 10px; font-weight: bold; font-size: 18px;">Rehber</div>
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
import { ref } from 'vue'

const users = ref([
  {
    id: '1',
    name: 'Ayşe Yılmaz',
    avatar: 'https://xsgames.co/randomusers/assets/avatars/female/1.jpg',
    lastMessage: 'Nasılsın?',
    messages: ['Merhaba!', 'Nasılsın?']
  },
  {
    id: '2',
    name: 'Mehmet Demir',
    avatar: 'https://xsgames.co/randomusers/assets/avatars/male/2.jpg',
    lastMessage: 'Yarın görüşelim mi?',
    messages: ['Selam!', 'Yarın görüşelim mi?']
  },
  {
    id: '3',
    name: 'Zeynep Kaya',
    avatar: 'https://xsgames.co/randomusers/assets/avatars/female/3.jpg',
    lastMessage: 'Tamamdır.',
    messages: ['Bugün işim var.', 'Tamamdır.']
  }
])

const selectedUser = ref(null)
const newMessage = ref('')

const handleSelectUser = ({ key }) => {
  selectedUser.value = users.value.find(user => user.id === key)
}

const sendMessage = () => {
  if (!newMessage.value.trim()) return
  selectedUser.value.messages.push(newMessage.value)
  selectedUser.value.lastMessage = newMessage.value
  newMessage.value = ''
}
</script>
