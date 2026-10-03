import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NotificationRow } from '@/components/notification/NotificationRow';
import type { NotificationItem, NotificationType } from '@/types/notification';
import { formatServerDateTime } from '@/utils/formatDate';

const baseItem: NotificationItem = {
  id: 1,
  type: 'RESTOCK',
  productId: 10,
  titleSnapshot: 'Kind of Blue',
  createdAt: '2026-09-01T10:00:00',
};

const renderRow = (item: NotificationItem, onRead = vi.fn(), onDelete = vi.fn()) => ({
  onRead,
  onDelete,
  ...render(
    <MemoryRouter>
      <NotificationRow item={item} onRead={onRead} onDelete={onDelete} />
    </MemoryRouter>,
  ),
});

describe('NotificationRow', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('안 읽은 항목을 클릭하면 onRead 가 해당 id 로 불린다', async () => {
    // given
    const user = userEvent.setup();
    const { onRead } = renderRow(baseItem);

    // when
    await user.click(screen.getByRole('link'));

    // then
    expect(onRead).toHaveBeenCalledWith(1);
  });

  it('이미 읽은 항목을 클릭하면 onRead 가 불리지 않는다', async () => {
    // given
    const user = userEvent.setup();
    const readItem: NotificationItem = { ...baseItem, readAt: '2026-09-01T11:00:00' };
    const { onRead } = renderRow(readItem);

    // when
    await user.click(screen.getByRole('link'));

    // then
    expect(onRead).not.toHaveBeenCalled();
  });

  it('RESTOCK 은 상품 상세로 링크된다', () => {
    // given & when
    renderRow(baseItem);

    // then
    expect(screen.getByRole('link').getAttribute('href')).toBe('/products/10');
  });

  it('NEW_PRESSING 은 앨범 상세로 링크된다', () => {
    // given
    const item: NotificationItem = {
      ...baseItem,
      type: 'NEW_PRESSING',
      productId: undefined,
      albumId: 20,
    };

    // when
    renderRow(item);

    // then
    expect(screen.getByRole('link').getAttribute('href')).toBe('/albums/20');
  });

  it('링크 대상이 없으면 link 역할이 없다', () => {
    // given
    const item: NotificationItem = { ...baseItem, productId: undefined };

    // when
    renderRow(item);

    // then
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('삭제 버튼을 누르면 onDelete 가 해당 id 로 불린다', async () => {
    // given
    const user = userEvent.setup();
    const { onDelete } = renderRow(baseItem);

    // when
    await user.click(screen.getByRole('button', { name: /알림 삭제/ }));

    // then
    expect(onDelete).toHaveBeenCalledWith(1);
  });

  it('삭제 버튼의 aria-label 에 알림 내용이 들어간다', () => {
    // given & when
    renderRow(baseItem);

    // then
    expect(
      screen.getByRole('button', { name: 'Kind of Blue 재입고되었습니다 알림 삭제' }),
    ).toBeInTheDocument();
  });

  it('링크 안에 삭제 버튼이 중첩되지 않는다', () => {
    // given
    const { container } = renderRow(baseItem);

    // when & then
    expect(container.querySelectorAll('a button').length).toBe(0);
  });

  it('유형이 섞인 목록도 예외 없이 모든 문구를 렌더한다', () => {
    // given
    const items: NotificationItem[] = [
      baseItem,
      {
        id: 2,
        type: 'STATS_MISMATCH',
        titleSnapshot: '매출 대사 불일치 3일 발생 (2026-08-28 ~ 2026-10-01)',
        createdAt: '2026-10-02T09:00:00',
      },
      {
        id: 3,
        type: 'FUTURE_TYPE' as unknown as NotificationType,
        titleSnapshot: 'Blue Train',
        createdAt: '2026-10-02T09:00:00',
      },
    ];

    // when
    render(
      <MemoryRouter>
        {items.map((item) => (
          <NotificationRow key={item.id} item={item} onRead={vi.fn()} onDelete={vi.fn()} />
        ))}
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText('Kind of Blue 재입고되었습니다')).toBeInTheDocument();
    expect(screen.getByText('Blue Train 관련 알림이 도착했습니다')).toBeInTheDocument();
    expect(
      screen
        .getByText('매출 대사 불일치 3일 발생 (2026-08-28 ~ 2026-10-01)')
        .closest('a')
        ?.getAttribute('href'),
    ).toBe('/admin#reconcile-logs');
  });

  it('KST 가 아닌 시간대에서도 생성 시각을 KST 기준으로 변환해 보여준다', () => {
    // given
    vi.stubEnv('TZ', 'UTC');
    const item: NotificationItem = { ...baseItem, createdAt: '2026-10-02T09:00:00' };

    // when
    renderRow(item);

    // then
    const expected = formatServerDateTime('2026-10-02T09:00:00');
    expect(expected).toContain('00:00');
    expect(screen.getByText(expected)).toBeInTheDocument();
  });
});
