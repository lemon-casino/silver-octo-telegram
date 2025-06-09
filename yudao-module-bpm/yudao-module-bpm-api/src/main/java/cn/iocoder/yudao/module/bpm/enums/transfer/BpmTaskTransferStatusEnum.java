package cn.iocoder.yudao.module.bpm.enums.transfer;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务转办配置状态枚举
 */
@Getter
@AllArgsConstructor
public enum BpmTaskTransferStatusEnum implements ArrayValuable<Integer> {

    WAIT(0, "待生效"),
    RUNNING(1, "代理中"),
    EXPIRED(2, "已过期"),
    CANCELED(3, "已撤销");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(BpmTaskTransferStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
