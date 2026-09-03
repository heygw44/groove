import type { OrderStatus } from '@/types/order';
import { ORDER_STATUS_LABEL, ORDER_STATUS_STEPS } from '@/utils/orderStatus';

interface OrderStatusTimelineProps {
  status: OrderStatus;
}

const STOPPED_MESSAGE: Partial<Record<OrderStatus, string>> = {
  CANCELED: '이 주문은 취소되었습니다',
  REFUNDED: '환불된 주문입니다',
};

export function OrderStatusTimeline({ status }: OrderStatusTimelineProps) {
  const stoppedMessage = STOPPED_MESSAGE[status];
  if (stoppedMessage) {
    return <p className="text-sm text-content-muted">{stoppedMessage}</p>;
  }

  const currentIndex = ORDER_STATUS_STEPS.indexOf(status as (typeof ORDER_STATUS_STEPS)[number]);

  return (
    <ol className="flex items-center">
      {ORDER_STATUS_STEPS.map((step, index) => {
        const isDone = index <= currentIndex;
        const isCurrent = index === currentIndex;
        return (
          <li key={step} className="flex flex-1 items-center last:flex-none">
            <div className="flex flex-col items-center gap-1.5">
              <span
                aria-current={isCurrent ? 'step' : undefined}
                className={`h-2.5 w-2.5 rounded-full ${isDone ? 'bg-accent' : 'bg-line-strong'}`}
              />
              <span
                className={`text-xs whitespace-nowrap ${
                  isDone ? 'font-medium text-content' : 'text-content-subtle'
                }`}
              >
                {ORDER_STATUS_LABEL[step]}
              </span>
            </div>
            {index < ORDER_STATUS_STEPS.length - 1 && (
              <div
                className={`mx-2 h-px flex-1 ${index < currentIndex ? 'bg-accent' : 'bg-line-strong'}`}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
