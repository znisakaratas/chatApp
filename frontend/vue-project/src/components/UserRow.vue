<template>
    <div style="display: flex; align-items: center; justify-content: space-between;">
      <!-- Sol: Avatar + Name + Email + Role -->
      <div style="display: flex; align-items: center;">
        <a-avatar :src="user.avatar" :size="40" />
        <div style="margin-left: 12px;">
          <div style="font-weight: 500;">{{ user.name }}</div>
          <div style="font-size: 12px; color: gray;">{{ user.email }} - {{ user.role }}</div>
        </div>
      </div>
   
    </div>
  </template>
  
  <script setup>
  import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
  defineProps({
    user: Object
  })
  </script>