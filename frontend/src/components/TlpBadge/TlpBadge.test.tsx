import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TLP_LEVELS } from '../../types/tlp';
import { TlpBadge } from './TlpBadge';

describe('TlpBadge', () => {
  it.each(TLP_LEVELS)('renders %s with its label and aria-label', (level) => {
    render(<TlpBadge tlp={level} />);
    const expected = level === 'AMBER_STRICT' ? 'TLP:AMBER+STRICT' : `TLP:${level}`;
    expect(screen.getByLabelText(expected)).toHaveTextContent(expected);
  });

  it('accepts lowercase input', () => {
    render(<TlpBadge tlp="green" />);
    expect(screen.getByLabelText('TLP:GREEN')).toBeInTheDocument();
  });

  it('renders a neutral fallback for unknown values instead of hiding them', () => {
    render(<TlpBadge tlp="ULTRAVIOLET" />);
    expect(screen.getByLabelText('TLP:ULTRAVIOLET')).toBeInTheDocument();
  });

  it('renders a placeholder when tlp is missing', () => {
    render(<TlpBadge tlp={undefined} />);
    expect(screen.getByLabelText('TLP:?')).toBeInTheDocument();
  });
});
