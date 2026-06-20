/** Tailwind config — tokens lifted verbatim from the BBC SMS prototype. */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#F1F5FA',
          100: '#DCE6F1',
          200: '#B7CADC',
          300: '#7E9CBA',
          400: '#4F75A0',
          500: '#2D5586',
          600: '#1B3A5C', // primary
          700: '#152F4B',
          800: '#0F2238',
          900: '#0A1828',
        },
        gold: {
          50:  '#FBF6E8',
          100: '#F5E9C1',
          200: '#ECD58A',
          300: '#E2C05A',
          400: '#D4A843', // accent
          500: '#B98B25',
          600: '#94701D',
        },
        ink: '#0F2238',
        mute: '#5B6B82',
        surface: '#F5F6FA',
      },
      fontFamily: {
        sans: ['Manrope', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['Fraunces', 'Georgia', 'serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(15,34,56,0.05), 0 4px 16px rgba(15,34,56,0.06)',
        pop: '0 12px 32px rgba(15,34,56,0.16)',
      },
      borderRadius: { xl2: '14px' },
    },
  },
  plugins: [],
};
