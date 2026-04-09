import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: Home,
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: Login,
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const loggedIn = localStorage.getItem('demo_logged_in') === 'true'

  if (to.meta.requiresAuth && !loggedIn) {
    return '/login'
  }

  if (to.path === '/login' && loggedIn) {
    return '/'
  }

  return true
})

export default router

