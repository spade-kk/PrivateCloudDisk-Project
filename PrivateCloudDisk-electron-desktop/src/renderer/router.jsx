/**
 * router.jsx - 应用路由配置
 *
 * 路由表:
 *   /login       登录页
 *   /register    注册页
 *   /home        首页 (文件浏览)
 *   /favorites   收藏
 *   /trash       回收站
 *   /search      搜索
 *   /profile     个人中心
 *   /settings    设置
 *   /file/:id    文件详情
 *   /video/:fileId  视频播放
 */
import { createHashRouter } from 'react-router-dom'
import Layout from '@/components/Layout'
import LoginPage from '@/pages/Login'
import RegisterPage from '@/pages/Register'
import HomePage from '@/pages/Home'
import FavoritesPage from '@/pages/Favorites'
import TrashPage from '@/pages/Trash'
import SearchPage from '@/pages/Search'
import ProfilePage from '@/pages/Profile'
import SettingsPage from '@/pages/Settings'
import FileDetailPage from '@/pages/FileDetail'
import VideoPlayerPage from '@/pages/VideoPlayer/VideoPlayer'

const router = createHashRouter([
  {
    path: '/login',
    element: <LoginPage />
  },
  {
    path: '/register',
    element: <RegisterPage />
  },
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'home', element: <HomePage /> },
      { path: 'favorites', element: <FavoritesPage /> },
      { path: 'trash', element: <TrashPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'file/:fileId', element: <FileDetailPage /> }
    ]
  },
  {
    path: '/video/:fileId',
    element: <VideoPlayerPage />
  }
])

export default router