import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useToast } from '@/components/common/toastContext';

function TriggerButton({ type, message }: { type: 'success' | 'error' | 'info'; message: string }) {
  const { showToast } = useToast();
  return (
    <button type="button" onClick={() => showToast(type, message)}>
      토스트 띄우기
    </button>
  );
}

function renderWithTrigger(type: 'success' | 'error' | 'info', message: string) {
  render(
    <ToastProvider>
      <TriggerButton type={type} message={message} />
    </ToastProvider>,
  );
}

describe('ToastProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('일반 메시지는 role=status 라이브 리전에 나타난다', () => {
    // given
    renderWithTrigger('success', '장바구니에 담았습니다.');

    // when
    fireEvent.click(screen.getByRole('button'));

    // then
    expect(screen.getByRole('status')).toHaveTextContent('장바구니에 담았습니다.');
  });

  it('에러 메시지는 role=alert 라이브 리전에 나타난다', () => {
    // given
    renderWithTrigger('error', '위시 등록에 실패했습니다.');

    // when
    fireEvent.click(screen.getByRole('button'));

    // then
    expect(screen.getByRole('alert')).toHaveTextContent('위시 등록에 실패했습니다.');
  });

  it('라이브 리전 컨테이너는 토스트가 없어도 항상 DOM 에 존재한다', () => {
    // given & when
    renderWithTrigger('success', '메시지');

    // then
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('5초가 지나면 자동으로 사라진다', () => {
    // given
    renderWithTrigger('info', '안내 메시지');
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('안내 메시지')).toBeInTheDocument();

    // when
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    // then
    expect(screen.queryByText('안내 메시지')).not.toBeInTheDocument();
  });

  it('마우스를 올리고 있는 동안은 타이머가 멈추고, 벗어나면 5초 뒤 다시 사라진다', () => {
    // given
    renderWithTrigger('info', '안내 메시지');
    fireEvent.click(screen.getByRole('button'));
    const toast = screen.getByText('안내 메시지');

    // when: 4초 경과 후 hover 시작 → 이후 오래 기다려도 사라지지 않는다
    act(() => {
      vi.advanceTimersByTime(4000);
    });
    fireEvent.mouseEnter(toast);
    act(() => {
      vi.advanceTimersByTime(10000);
    });

    // then
    expect(screen.getByText('안내 메시지')).toBeInTheDocument();

    // when: hover 를 벗어나면 타이머가 5초로 재시작된다
    fireEvent.mouseLeave(toast);
    act(() => {
      vi.advanceTimersByTime(4999);
    });
    expect(screen.getByText('안내 메시지')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1);
    });

    // then
    expect(screen.queryByText('안내 메시지')).not.toBeInTheDocument();
  });

  it('포커스가 들어오면 타이머가 멈추고, 벗어나면 다시 시작된다', () => {
    // given
    renderWithTrigger('info', '포커스 메시지');
    fireEvent.click(screen.getByRole('button'));
    const toast = screen.getByText('포커스 메시지');

    // when
    fireEvent.focus(toast);
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    // then
    expect(screen.getByText('포커스 메시지')).toBeInTheDocument();

    // when
    fireEvent.blur(toast);
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    // then
    expect(screen.queryByText('포커스 메시지')).not.toBeInTheDocument();
  });
});
