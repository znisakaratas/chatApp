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
import { ref } from 'vue'
import UserRow from '../components/UserRow.vue' 

const activeTab = ref('users')

const users = ref([
  {
    id: 1,
    name: 'Ayşe Yılmaz',
    email: 'ayse@example.com',
    role: 'Admin',
    avatar: 'https://xsgames.co/randomusers/assets/avatars/female/1.jpg'
  },
  {
    id: 2,
    name: 'Mehmet Demir',
    email: 'mehmet@example.com',
    role: 'User',
    avatar: 'https://xsgames.co/randomusers/assets/avatars/male/2.jpg'
  }
])

const columns = [
  {
    title: 'Kullanıcı Bilgileri',
    key: 'customRow',
    dataIndex: 'customRow'
  }
]
  
</script>
