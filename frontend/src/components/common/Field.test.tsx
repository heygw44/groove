import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Field } from '@/components/common/Field';
import { Input } from '@/components/common/Input';

describe('Field', () => {
  it('help 가 있으면 자식 input 에 aria-describedby 를 연결한다', () => {
    // given & when
    render(
      <Field htmlFor="nickname" label="닉네임" help="2~20자">
        <Input id="nickname" />
      </Field>,
    );

    // then
    const input = screen.getByLabelText('닉네임');
    const help = screen.getByText('2~20자');
    expect(input).toHaveAttribute('aria-describedby', help.id);
  });

  it('error 가 있으면 자식 input 에 aria-describedby 를 연결하고 role=alert 로 보여준다', () => {
    // given & when
    render(
      <Field htmlFor="nickname" label="닉네임" error="이미 사용 중입니다.">
        <Input id="nickname" />
      </Field>,
    );

    // then
    const input = screen.getByLabelText('닉네임');
    const error = screen.getByRole('alert');
    expect(error).toHaveTextContent('이미 사용 중입니다.');
    expect(input).toHaveAttribute('aria-describedby', error.id);
  });

  it('자식이 이미 aria-describedby 를 갖고 있으면 덮어쓰지 않고 이어붙인다', () => {
    // given & when
    render(
      <Field htmlFor="nickname" label="닉네임" help="2~20자">
        <Input id="nickname" aria-describedby="external-hint" />
      </Field>,
    );

    // then
    const input = screen.getByLabelText('닉네임');
    expect(input.getAttribute('aria-describedby')).toBe('external-hint nickname-message');
  });

  it('required 면 시각적 별표와 스크린리더용 (필수) 문구, aria-required 를 함께 준다', () => {
    // given & when
    render(
      <Field htmlFor="nickname" label="닉네임" required>
        <Input id="nickname" />
      </Field>,
    );

    // then
    expect(screen.getByText('(필수)')).toHaveClass('sr-only');
    expect(screen.getByLabelText(/닉네임/)).toHaveAttribute('aria-required', 'true');
  });

  it('자식이 단일 엘리먼트가 아니면 주입을 건너뛰고 그대로 렌더한다', () => {
    // given & when
    render(
      <Field htmlFor="content" label="내용" help="글자수 제한 있음">
        <Input id="content" />
        <p>보조 텍스트</p>
      </Field>,
    );

    // then
    expect(screen.getByLabelText('내용')).not.toHaveAttribute('aria-describedby');
    expect(screen.getByText('보조 텍스트')).toBeInTheDocument();
  });

  it('trailing 은 children 과 별개로 렌더되고 aria 주입 대상이 아니다', () => {
    // given & when
    render(
      <Field htmlFor="content" label="내용" trailing={<p>0/500</p>}>
        <Input id="content" />
      </Field>,
    );

    // then
    expect(screen.getByText('0/500')).toBeInTheDocument();
    expect(screen.getByLabelText('내용')).not.toHaveAttribute('aria-describedby');
  });
});
