package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 任务转办配置 Response VO")
@Data
public class BpmTaskTransferConfigRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "原审批人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long fromUserId;

    @Schema(description = "新审批人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long toUserId;

    @Schema(description = "原审批人用户姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String fromUserName;

    @Schema(description = "新审批人用户姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    private String toUserName;
    @Schema(description = "流程定义编号")
    private String processDefinitionId;
    @Schema(description = "开始时间，时间戳", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long startTime;

    @Schema(description = "结束时间，时间戳", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long endTime;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "备注")
    private String reason;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long createTime;

}
