package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 流程模型版本 Response VO
 */
@Data
public class BpmModelVersionRespVO {

    @Schema(description = "流程模型编号", example = "a2c5eee0")
    private String id;

    @Schema(description = "流程模型名称", example = "请假流程")
    private String name;

    @Schema(description = "流程版本号数组", example = "[1,2]")
    private List<Integer> versions;
}
