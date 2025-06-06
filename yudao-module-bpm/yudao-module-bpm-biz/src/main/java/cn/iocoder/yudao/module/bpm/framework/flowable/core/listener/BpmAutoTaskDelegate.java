package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 自动任务执行的 {@link JavaDelegate} 实现类
 *
 * 用于仿钉钉流程设计器中“自动通过”节点。
 */
@Component(BpmAutoTaskDelegate.BEAN_NAME)
@Slf4j
public class BpmAutoTaskDelegate implements JavaDelegate {

    public static final String BEAN_NAME = "bpmAutoTaskDelegate";

    @Override
    public void execute(DelegateExecution execution) {
        log.info("[execute][流程({}) 自动任务({}) 自动完成]", execution.getProcessInstanceId(), execution.getCurrentActivityId());
        // 无实际逻辑，流程会自动流转到下一个节点
    }
}
