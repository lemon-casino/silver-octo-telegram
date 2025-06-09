package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 任务转办配置分页 Request VO")
@Data
public class BpmTaskTransferConfigPageReqVO extends PageParam {

    @Schema(description = "原审批人用户编号", example = "1")
    private Long fromUserId;

    @Schema(description = "新审批人用户编号", example = "2")
    private Long toUserId;

    @Schema(description = "流程定义编号")
    private String processDefinitionId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

    //状态
    private  Integer status;
}
