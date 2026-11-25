import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RoadNameCombo from './index';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({ default: { road: { getRoadNames: vi.fn() } } }));

const getRoadNames = vi.mocked(API.road.getRoadNames);

const Harness = ({ onChange }: { onChange: (name: string) => void }) => {
  const [value, setValue] = useState('');
  return (
    <RoadNameCombo
      id="road-name"
      value={value}
      onChange={(name) => {
        setValue(name);
        onChange(name);
      }}
    />
  );
};

const renderCombo = (onChange = vi.fn()) => {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <Harness onChange={onChange} />
    </QueryClientProvider>,
  );
  return onChange;
};

describe('RoadNameCombo', () => {
  beforeEach(() => {
    getRoadNames.mockReset();
  });

  it('lists LRMOPS road names for the text typed, and filters on the one picked', async () => {
    getRoadNames.mockResolvedValue(['BIG CREEK FSR', 'BIG CROW RD'] as never);
    const onChange = renderCombo();

    await userEvent.type(screen.getByRole('combobox'), 'big c');

    const option = await screen.findByRole('option', { name: 'Big Crow RD' });
    expect(getRoadNames).toHaveBeenLastCalledWith('big c');
    await userEvent.click(option);

    expect(onChange).toHaveBeenLastCalledWith('BIG CROW RD');
    expect(screen.getByRole('combobox')).toHaveValue('Big Crow RD');
  });

  it('does not ask for fewer than three characters', async () => {
    renderCombo();

    await userEvent.type(screen.getByRole('combobox'), 'bi');
    await new Promise((resolve) => setTimeout(resolve, 400));

    expect(getRoadNames).not.toHaveBeenCalled();
  });

  it('says so beside the field when LRMOPS cannot be reached', async () => {
    getRoadNames.mockRejectedValue({ status: 503 });
    renderCombo();

    await userEvent.type(screen.getByRole('combobox'), 'big');

    await waitFor(() =>
      expect(screen.getByText('Road lookup is unavailable right now.')).toBeInTheDocument(),
    );
  });
});
