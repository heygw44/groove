import { twMerge } from 'tailwind-merge';

/** 뒤에 오는 클래스가 앞의 충돌 클래스를 이기도록 합쳐서 호출자의 크기·글자 지정이 유효하게 한다. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return twMerge(classes.filter(Boolean).join(' '));
}
