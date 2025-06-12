package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Schema(description = "管理后台 - 任务转办配置创建/修改 Request VO")
@Data
public class BpmTaskTransferConfigSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "原审批人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "原审批人不能为空")
    private Long fromUserId;

    @Schema(description = "新审批人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "新审批人不能为空")
    private Long toUserId;


    @Schema(description = "原审批人用户姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private String fromUserName;

    @Schema(description = "新审批人用户姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private String toUserName;

    @Schema(description = "流程模型编号")
    private String modelId;

    @Schema(description = "流程版本号")
    private Integer modelVersion;
    @Schema(description = "开始时间，时间戳", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    @Schema(description = "结束时间，时间戳", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    private Long endTime;

    @Schema(description = "备注")
    private String reason;
}
