package cn.iocoder.yudao.module.bpm.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务自动转办配置
 */
@TableName("bpm_task_transfer_config")
@KeySequence("bpm_task_transfer_config_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmTaskTransferConfigDO extends BaseDO {

    /** 主键 */
    @TableId
    private Long id;
    /** 原审批人 */
    private Long fromUserId;
    /** 新审批人 */
    private Long toUserId;
    /** 适用的流程定义编号，为空时表示全部流程 */
    private String processDefinitionId;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 备注 */
    private String reason;
}
