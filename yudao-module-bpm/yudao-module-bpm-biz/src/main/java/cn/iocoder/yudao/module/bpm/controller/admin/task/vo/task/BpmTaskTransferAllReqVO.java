package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Package: cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task
 * @Description: < 请求VO将所有正在运行的任务从一个用户转移到另一个用户 >
 * @Author: 柠檬果肉
 * @Date: 2025/6/9 10:37
 * @Version V1.0
 */

@Data
@Schema(description = "管理后台 - 批量转办运行中任务 Request VO")
public class BpmTaskTransferAllReqVO {

    @Schema(description = "当前审批人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "当前审批人不能为空")
    private Long fromUserId;

    @Schema(description = "新审批人的用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "新审批人不能为空")
    private Long toUserId;

    @Schema(description = "转办原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "人员变动")
    @NotEmpty(message = "转办原因不能为空")
    private String reason;
}