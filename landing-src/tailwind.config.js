/** @type {import('tailwindcss').Config} */
module.exports = {
  // Paths are relative to the CWD when running the Tailwind CLI (repo root):
  //   npx tailwindcss@3 -c landing-src/tailwind.config.js -i landing-src/src.css -o landing/tailwind.css --minify
  // Recompile is optional — landing/tailwind.css is committed.
  content: [
    'landing/index.html',
  ],
  theme: {
    extend: {
      colors: {
        primary: '#1565C0', 'on-primary': '#FFFFFF',
        'primary-container': '#E3F2FD', 'on-primary-container': '#001C37',
        secondary: '#4A6572', tertiary: '#006874',
        surface: '#FBFAF8', 'surface-card': '#FFFFFF', 'surface-low': '#F6F5F2', 'surface-cont': '#F1F0ED',
        'on-surface': '#1D1B20', 'on-surface-variant': '#49454F',
        outline: '#79747E', 'outline-variant': '#E2E0DB', error: '#BA1A1A',
        'brand-blue': '#2196F3', 'brand-indigo': '#6366F1', 'brand-purple': '#A855F7',
        success: '#2E9E5B', star: '#F4A923',
      },
      fontFamily: { sans: ['Roboto', 'system-ui', 'sans-serif'] },
      borderRadius: { xs: '4px', sm: '8px', md: '12px', lg: '16px', xl: '24px', pill: '999px' },
      maxWidth: { content: '1120px' },
      boxShadow: { card: '0 1px 2px rgba(20,30,60,.06), 0 6px 24px rgba(20,30,60,.06)' },
    },
  },
};
