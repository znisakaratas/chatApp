import { createRouter, createWebHistory } from 'vue-router';
import Layout from '../layouts/Layout.vue';
import HomeView from '../pages/HomeView.vue';
import AdminView from '../pages/AdminView.vue';
import { useToast } from 'vue-toastification'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      {
        path: '',
        name: 'Home',
        component: HomeView
      },
      {
        path: 'admin',
        name: 'Admin',
        component: AdminView,
        meta: {
          requiresAuth: true,    // Bu rota kimlik doğrulaması gerektiriyor
          requiresAdmin: true    // Bu rota admin yetkisi gerektiriyor
        }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});
function getCurrentUserRole() {
  return localStorage.getItem("role"); 
}
 
router.beforeEach((to, from, next) => {
  const toast = useToast();
  const userRole = getCurrentUserRole();
   if (to.meta.requiresAdmin && userRole !== 'admin') { 
    toast.error('Bu sayfaya erişiminiz yoktur.');
 
    next({ name: 'Home' });
  } else { 
    next();
  }
});
export default router;
