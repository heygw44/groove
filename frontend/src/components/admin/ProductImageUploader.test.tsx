import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ProductImageUploader } from '@/components/admin/ProductImageUploader';
import { ToastContext } from '@/components/common/toastContext';

interface PendingUpload {
  file: File;
  resolve: (result: { url: string }) => void;
}

const pendingUploads: PendingUpload[] = [];

const mutateAsync = vi.fn(
  (file: File) =>
    new Promise<{ url: string }>((resolve) => {
      pendingUploads.push({ file, resolve });
    }),
);

vi.mock('@/hooks/mutations/useFileMutations', () => ({
  useUploadImage: () => ({ mutateAsync }),
}));

const DROP_ZONE_NAME = /클릭하거나 이미지를 끌어다 놓으세요/;
const FULL_DROP_ZONE_NAME = /이미지는 최대 10장까지 등록할 수 있습니다/;

const toUrl = (fileName: string) => `https://cdn.example.com/${fileName}`;
const buildFile = (fileName: string) => new File(['x'], fileName, { type: 'image/png' });

function UploaderHarness({
  initialValue = [],
  disabled = false,
}: {
  initialValue?: string[];
  disabled?: boolean;
}) {
  const [value, setValue] = useState<string[]>(initialValue);
  return <ProductImageUploader value={value} onChange={setValue} disabled={disabled} />;
}

const renderUploader = (props: { initialValue?: string[]; disabled?: boolean } = {}) =>
  render(
    <ToastContext.Provider value={{ showToast: vi.fn() }}>
      <UploaderHarness {...props} />
    </ToastContext.Provider>,
  );

const dropFiles = (zone: HTMLElement, files: File[]) => {
  fireEvent.drop(zone, { dataTransfer: { files } });
};

const resolveNextUpload = async () => {
  await waitFor(() => expect(pendingUploads.length).toBeGreaterThan(0));
  const next = pendingUploads.shift();
  if (!next) {
    throw new Error('대기 중인 업로드가 없습니다.');
  }
  await act(async () => {
    next.resolve({ url: toUrl(next.file.name) });
  });
};

const getRenderedImageUrls = () =>
  screen.queryAllByRole('img').map((image) => image.getAttribute('src'));

describe('ProductImageUploader', () => {
  afterEach(() => {
    mutateAsync.mockClear();
    pendingUploads.length = 0;
  });

  it('첫 업로드가 끝나기 전에 두 번째 드롭이 들어오면 두 번째 드롭을 무시하고 첫 업로드 결과만 남긴다', async () => {
    // given
    renderUploader();
    const zone = screen.getByRole('button', { name: DROP_ZONE_NAME });
    dropFiles(zone, [buildFile('a.png'), buildFile('b.png')]);
    await waitFor(() => expect(pendingUploads).toHaveLength(1));

    // when
    dropFiles(zone, [buildFile('c.png')]);
    await resolveNextUpload();
    await resolveNextUpload();

    // then
    await waitFor(() => expect(getRenderedImageUrls()).toEqual([toUrl('a.png'), toUrl('b.png')]));
    expect(mutateAsync.mock.calls.map(([file]) => file.name)).toEqual(['a.png', 'b.png']);
  });

  it('업로드가 끝나면 다시 드롭할 수 있다', async () => {
    // given
    renderUploader();
    const zone = screen.getByRole('button', { name: DROP_ZONE_NAME });
    dropFiles(zone, [buildFile('a.png')]);
    await resolveNextUpload();
    await waitFor(() => expect(getRenderedImageUrls()).toEqual([toUrl('a.png')]));

    // when
    dropFiles(zone, [buildFile('b.png')]);
    await resolveNextUpload();

    // then
    await waitFor(() => expect(getRenderedImageUrls()).toEqual([toUrl('a.png'), toUrl('b.png')]));
  });

  it('disabled 이면 드롭을 무시한다', () => {
    // given
    renderUploader({ disabled: true });
    const zone = screen.getByRole('button', { name: DROP_ZONE_NAME });

    // when
    dropFiles(zone, [buildFile('a.png')]);

    // then
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('이미지가 10장이면 드롭을 무시한다', () => {
    // given
    const fullValue = Array.from({ length: 10 }, (_, index) => toUrl(`full-${index}.png`));
    renderUploader({ initialValue: fullValue });
    const zone = screen.getByRole('button', { name: FULL_DROP_ZONE_NAME });

    // when
    dropFiles(zone, [buildFile('a.png')]);

    // then
    expect(mutateAsync).not.toHaveBeenCalled();
    expect(getRenderedImageUrls()).toEqual(fullValue);
  });

  it('업로드 중에는 이미지 삭제·이동 버튼이 비활성화된다', async () => {
    // given
    renderUploader({ initialValue: [toUrl('x.png'), toUrl('y.png')] });
    const zone = screen.getByRole('button', { name: DROP_ZONE_NAME });

    // when
    dropFiles(zone, [buildFile('a.png')]);
    await waitFor(() => expect(pendingUploads).toHaveLength(1));

    // then
    expect(screen.getByRole('button', { name: '1번째 이미지 뒤로 이동' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '2번째 이미지 앞으로 이동' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '1번째 이미지 삭제' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '2번째 이미지 삭제' })).toBeDisabled();

    await resolveNextUpload();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '1번째 이미지 삭제' })).toBeEnabled(),
    );
  });
});
