package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 取消流程任务的 Request VO")
@Data
public class BpmTaskCancelReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "任务编号不能为空")
    private String id;

    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "无需处理")
    @NotEmpty(message = "取消原因不能为空")
    private String reason;

    @Schema(description = "是否是管理员", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean managerial = false;
}
