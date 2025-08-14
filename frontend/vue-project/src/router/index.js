import { createRouter, createWebHistory } from 'vue-router';
import Layout from '../layouts/Layout.vue';
import HomeView from '../pages/HomeView.vue';
import AdminView from '../pages/AdminView.vue';

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      {
        path: '',
        component: HomeView
      },
      {
        path: 'admin',
        component: AdminView
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;
