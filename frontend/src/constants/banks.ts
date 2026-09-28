/**
 * 토스페이먼츠 은행 코드 → 한글 은행명. 토스 응답은 두 자리·세 자리 코드를 모두 쓸 수 있고, 국민은행처럼
 * 두 형식이 단순 0-패딩 관계가 아닌 은행도 있어 둘 다 등록한다. 요청(환불계좌)에는 두 자리 코드를 보낸다.
 */
const BANKS: ReadonlyArray<{ code: string; code3: string; name: string }> = [
  { code: '39', code3: '039', name: '경남은행' },
  { code: '34', code3: '034', name: '광주은행' },
  { code: '12', code3: '012', name: '단위농협' },
  { code: '32', code3: '032', name: '부산은행' },
  { code: '45', code3: '045', name: '새마을금고' },
  { code: '64', code3: '064', name: '산림조합' },
  { code: '88', code3: '088', name: '신한은행' },
  { code: '48', code3: '048', name: '신협' },
  { code: '27', code3: '027', name: '씨티은행' },
  { code: '20', code3: '020', name: '우리은행' },
  { code: '71', code3: '071', name: '우체국' },
  { code: '50', code3: '050', name: '저축은행' },
  { code: '37', code3: '037', name: '전북은행' },
  { code: '35', code3: '035', name: '제주은행' },
  { code: '90', code3: '090', name: '카카오뱅크' },
  { code: '89', code3: '089', name: '케이뱅크' },
  { code: '92', code3: '092', name: '토스뱅크' },
  { code: '81', code3: '081', name: '하나은행' },
  { code: '03', code3: '003', name: 'IBK기업은행' },
  { code: '06', code3: '004', name: 'KB국민은행' },
  { code: '31', code3: '031', name: 'iM뱅크(대구)' },
  { code: '02', code3: '002', name: 'KDB산업은행' },
  { code: '11', code3: '011', name: 'NH농협은행' },
  { code: '23', code3: '023', name: 'SC제일은행' },
  { code: '07', code3: '007', name: '수협은행' },
];

export const BANK_NAMES: Readonly<Record<string, string>> = Object.fromEntries(
  BANKS.flatMap((bank) => [
    [bank.code, bank.name],
    [bank.code3, bank.name],
  ]),
);

/** 모르는 코드면 코드를 그대로 돌려준다. */
export const getBankName = (code: string): string => BANK_NAMES[code] ?? code;

export interface BankOption {
  code: string;
  name: string;
}

/** 환불계좌 은행 선택용. 이름순 정렬. */
export const BANK_OPTIONS: BankOption[] = BANKS.map(({ code, name }) => ({ code, name })).sort(
  (a, b) => a.name.localeCompare(b.name, 'ko'),
);
