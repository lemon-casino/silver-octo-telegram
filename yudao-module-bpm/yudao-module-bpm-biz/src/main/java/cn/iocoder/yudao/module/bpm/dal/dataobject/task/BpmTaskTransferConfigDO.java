package cn.iocoder.yudao.module.bpm.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;


/**
 * 任务自动转办配置
 */
@TableName("bpm_task_transfer_config")
@KeySequence("bpm_task_transfer_config_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BpmTaskTransferConfigDO extends BaseDO {

    /** 主键 */
    @TableId
    private Long id;
    /** 原审批人 */
    private Long fromUserId;
    /** 新审批人 */
    private Long toUserId;
    /** 适用的流程模型编号，为空时表示全部流程 */
    private String modelId=null;

    /** 适用的流程版本号 */
    private Integer modelVersion=null;
    /** 开始时间，时间戳 */
    private Long startTime;
    /** 结束时间，时间戳 */
    private Long endTime;
    /** 状态 */
    private Integer status;
    /** 备注 */
    private String reason;
}
