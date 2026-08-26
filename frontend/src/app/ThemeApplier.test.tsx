import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { afterEach, describe, expect, it } from 'vitest';
import { makeStore } from '../stores';
import { themeChanged } from '../stores/uiSlice';
import { ThemeApplier } from './ThemeApplier';

afterEach(() => {
  document.documentElement.classList.remove('dark');
});

function renderApplier(store = makeStore()) {
  return render(
    <Provider store={store}>
      <ThemeApplier />
    </Provider>,
  );
}

describe('ThemeApplier', () => {
  it('applies the dark class when theme is dark', () => {
    const store = makeStore();
    store.dispatch(themeChanged('dark'));
    renderApplier(store);
    expect(document.documentElement).toHaveClass('dark');
  });

  it('removes the dark class when theme is light', () => {
    document.documentElement.classList.add('dark');
    const store = makeStore();
    store.dispatch(themeChanged('light'));
    renderApplier(store);
    expect(document.documentElement).not.toHaveClass('dark');
  });

  it('follows prefers-color-scheme when theme is system', () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: true,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
    try {
      renderApplier();
      expect(document.documentElement).toHaveClass('dark');
    } finally {
      window.matchMedia = original;
    }
  });
});
