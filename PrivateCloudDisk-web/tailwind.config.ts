import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#165DFF',
        secondary: '#36CFC9',
        success: '#52C41A',
        warning: '#FAAD14',
        danger: '#FF4D4F',
        neutral: {
          100: '#F5F7FA',
          200: '#E4E7ED',
          300: '#C0C6CF',
          400: '#909399',
          500: '#606266',
          600: '#303133',
          700: '#1E1E1E',
        },
      },
      fontFamily: {
        inter: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card: '0 2px 12px 0 rgba(0, 0, 0, 0.08)',
        hover: '0 4px 16px 0 rgba(22, 93, 255, 0.15)',
      },
      animation: {
        'spin-slow': 'spin 1s linear infinite',
      },
    },
  },
  plugins: [],
} satisfies Config